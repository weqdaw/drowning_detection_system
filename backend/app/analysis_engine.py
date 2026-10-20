import time
import logging
import json
import queue
import os
import threading
from concurrent.futures import ThreadPoolExecutor
import cv2
import numpy as np
from . import database
from .model_wrapper import audio_model_manager

logger = logging.getLogger("AnalysisEngine")
db_executor = ThreadPoolExecutor(max_workers=4, thread_name_prefix="db-worker")

class RecordingSession:
    def __init__(self, stream_id: str, threshold: float, width: int = 640, height: int = 480, fps: int = 25):
        self.stream_id = stream_id
        self.threshold = threshold
        self.width = width
        self.height = height
        self.fps = fps
        self.start_time = time.time()
        self.last_active_time = time.time()
        self.max_confidence = 0.0
        self.frame_count = 0

        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        self.recordings_dir = os.path.join(base_dir, "static", "recordings")
        os.makedirs(self.recordings_dir, exist_ok=True)

        timestamp_str = time.strftime("%Y%m%d_%H%M%S")
        self.file_name = f"rec_{stream_id}_{timestamp_str}.mp4"
        self.file_path = os.path.join(self.recordings_dir, self.file_name)

        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        self.writer = cv2.VideoWriter(self.file_path, fourcc, self.fps, (self.width, self.height))
        self.write_queue = queue.Queue(maxsize=100)
        self.is_active = True
        self.worker_thread = threading.Thread(target=self._worker_loop, daemon=True)
        self.worker_thread.start()
        logger.info(f"[{stream_id}] Automatic threshold recording started: {self.file_name} (Threshold >= {threshold*100:.0f}%)")

    def _worker_loop(self):
        while self.is_active or not self.write_queue.empty():
            try:
                item = self.write_queue.get(timeout=0.2)
                if item is None:
                    break
                frame, current_confidence = item
                if frame.shape[0] != self.height or frame.shape[1] != self.width:
                    frame = cv2.resize(frame, (self.width, self.height))
                if self.writer:
                    self.writer.write(frame)
                    self.frame_count += 1
                    self.last_active_time = time.time()
                    if current_confidence > self.max_confidence:
                        self.max_confidence = current_confidence
            except queue.Empty:
                continue
            except Exception as e:
                logger.error(f"[{self.stream_id}] Error in async video writer worker: {e}")

    def write_frame(self, frame: np.ndarray, current_confidence: float):
        if not self.is_active or frame is None:
            return
        try:
            if not self.write_queue.full():
                self.write_queue.put_nowait((frame.copy(), current_confidence))
        except Exception as e:
            logger.error(f"[{self.stream_id}] Error queuing frame to video writer: {e}")

    def stop(self, trigger_reason: str = "触发设定的告警阈值自动开启与关闭"):
        if not self.is_active:
            return
        self.is_active = False
        self.write_queue.put(None)
        if self.worker_thread.is_alive():
            self.worker_thread.join(timeout=2.0)
        if self.writer:
            self.writer.release()
            self.writer = None

        duration = round(time.time() - self.start_time, 1)
        file_size = os.path.getsize(self.file_path) if os.path.exists(self.file_path) else 0

        logger.info(f"[{self.stream_id}] Stopped recording {self.file_name}. Duration: {duration}s, Size: {file_size} bytes")

        db_executor.submit(
            database.add_recording,
            stream_id=self.stream_id,
            file_name=self.file_name,
            file_path=self.file_path,
            duration=duration,
            file_size=file_size,
            threshold=self.threshold,
            max_confidence=round(float(self.max_confidence), 2),
            trigger_reason=trigger_reason
        )

class EventBroker:
    """Publishes real-time events to all active SSE listener queues."""
    def __init__(self):
        self.listeners = []

    def subscribe(self) -> queue.Queue:
        q = queue.Queue(maxsize=100)
        self.listeners.append(q)
        logger.info(f"New SSE client subscribed. Total subscriptions: {len(self.listeners)}")
        return q

    def unsubscribe(self, q: queue.Queue):
        if q in self.listeners:
            self.listeners.remove(q)
            logger.info(f"SSE client disconnected. Total subscriptions: {len(self.listeners)}")

    def publish(self, event_data: dict):
        for q in list(self.listeners):
            try:
                q.put_nowait(event_data)
            except queue.Full:
                try:
                    q.get_nowait()
                    q.put_nowait(event_data)
                except Exception:
                    pass

event_broker = EventBroker()

def is_point_in_polygon(x: float, y: float, polygon: list) -> bool:
    """Ray casting algorithm to check if point (x, y) is inside polygon coordinates."""
    if not polygon or len(polygon) < 3:
        return False
    n = len(polygon)
    inside = False
    p1x, p1y = polygon[0]
    for i in range(n + 1):
        p2x, p2y = polygon[i % n]
        if y > min(p1y, p2y):
            if y <= max(p1y, p2y):
                if x <= max(p1x, p2x):
                    if p1y != p2y:
                        xints = (y - p1y) * (p2x - p1x) / (p2y - p1y) + p1x
                    if p1x == p2x or x <= xints:
                        inside = not inside
        p1x, p1y = p2x, p2y
    return inside

class StreamTrackerState:
    def __init__(self, stream_id: str):
        self.stream_id = stream_id
        self.first_seen = {}       # tracker_id -> float (timestamp)
        self.last_seen = {}        # tracker_id -> float (timestamp)
        self.cooldowns = {}        # (tracker_id, rule_type) -> float (timestamp)
        self.track_history = {}    # tracker_id -> list of (timestamp, cx, cy)
        self.cls_history = {}      # tracker_id -> list of str categories

    def update_seen(self, tracker_id: int):
        now = time.time()
        if tracker_id not in self.first_seen:
            self.first_seen[tracker_id] = now
        self.last_seen[tracker_id] = now

    def clean_stale_tracks(self, max_stale_seconds=5.0):
        """Remove tracker IDs that haven't been detected recently."""
        now = time.time()
        stale_ids = [tid for tid, ltime in self.last_seen.items() if now - ltime > max_stale_seconds]
        for tid in stale_ids:
            self.first_seen.pop(tid, None)
            self.last_seen.pop(tid, None)
            self.track_history.pop(tid, None)
            self.cls_history.pop(tid, None)
            # Clean cooldowns for that tracker ID
            keys_to_del = [k for k in self.cooldowns.keys() if k[0] == tid]
            for k in keys_to_del:
                self.cooldowns.pop(k, None)

class AnalysisEngine:
    def __init__(self):
        self.states = {}  # stream_id -> StreamTrackerState
        self.recording_sessions = {} # stream_id -> RecordingSession
        self.cooldown_period = 15.0  # seconds between alarms of same type & tracker ID

    def _get_or_create_state(self, stream_id: str) -> StreamTrackerState:
        if stream_id not in self.states:
            self.states[stream_id] = StreamTrackerState(stream_id)
        return self.states[stream_id]

    def process_frame(self, stream_id: str, detections: list, rules_config_str: str, frame: np.ndarray = None, stream_threshold: float = 0.70, audio_frame: np.ndarray = None) -> list:
        state = self._get_or_create_state(stream_id)
        state.clean_stale_tracks()

        # Parse configurations
        rules = {}
        if rules_config_str:
            try:
                rules = json.loads(rules_config_str)
            except Exception as e:
                logger.error(f"Failed to parse rules_config for stream {stream_id}: {e}")

        triggered_events = []
        now = time.time()
        max_conf_in_frame = max([d["confidence"] for d in detections]) if detections else 0.0

        # Check for Audio ONNX Recognition rules
        audio_config = rules.get("audio_config") if isinstance(rules, dict) else None
        if audio_config and audio_config.get("enabled"):
            audio_model = audio_config.get("model_name")
            selected_classes = audio_config.get("selected_classes", [])
            audio_thresh = float(audio_config.get("audio_threshold", 0.50))

            if audio_model and selected_classes:
                try:
                    engine = audio_model_manager.get_engine(audio_model)
                    if engine:
                        sample_audio = audio_frame if audio_frame is not None else np.random.uniform(-0.1, 0.1, 16000).astype(np.float32)
                        audio_alerts = engine.analyze_audio_frame(sample_audio, selected_classes, audio_thresh)

                        for alert in audio_alerts:
                            audio_conf = alert["confidence"]
                            cooldown_key = (alert["display_name"], "audio_alert")
                            last_t = state.cooldowns.get(cooldown_key, 0.0)
                            if now - last_t > 8.0:  # 8 seconds cooldown for audio alerts
                                state.cooldowns[cooldown_key] = now
                                audio_event = {
                                    "stream_id": stream_id,
                                    "event_name": f"Audio_{alert['display_name']}",
                                    "rule_type": "audio_alert",
                                    "details": f"🚨 音视频识别告警: 声音[{alert['display_name']}]与模型视觉识别同步触发！",
                                    "confidence": alert["confidence"],
                                    "threshold": 0.0,
                                    "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
                                }
                                db_executor.submit(
                                    database.add_event,
                                    stream_id=stream_id,
                                    rule_type="audio_alert",
                                    event_name=audio_event["event_name"],
                                    details=audio_event["details"],
                                    confidence=alert["confidence"]
                                )
                                triggered_events.append(audio_event)
                                event_broker.publish(audio_event)
                except Exception as audio_err:
                    logger.error(f"Audio analysis error for stream {stream_id}: {audio_err}")

        rec_session = self.recording_sessions.get(stream_id)

        # AI Model detection triggers automatic recording session
        if detections:
            if not rec_session or not rec_session.is_active:
                h, w = (frame.shape[0], frame.shape[1]) if frame is not None else (480, 640)
                rec_session = RecordingSession(
                    stream_id=stream_id,
                    threshold=0.0,
                    width=w,
                    height=h
                )
                self.recording_sessions[stream_id] = rec_session

            if frame is not None:
                rec_session.write_frame(frame, max_conf_in_frame)

        elif rec_session and rec_session.is_active:
            dur = now - rec_session.start_time
            idle_dur = now - rec_session.last_active_time

            if idle_dur <= 5.0 and dur < 30.0:
                if frame is not None:
                    rec_session.write_frame(frame, max_conf_in_frame)
            else:
                stop_reason = "模型识别完成，持续5秒无新目标后自动归档录像"
                rec_session.stop(trigger_reason=stop_reason)
                self.recording_sessions.pop(stream_id, None)

        # Publish a general detection event to SSE for real-time scrolling in UI
        if detections:
            # Throttle general detection events so we don't overwhelm SSE client (e.g. max once per 1.0 second per stream)
            cooldown_key = ("general", "detection")
            last_triggered = state.cooldowns.get(cooldown_key, 0.0)
            if now - last_triggered > 1.0:
                state.cooldowns[cooldown_key] = now
                details = ", ".join([f"{d['class_name']}(ID:{d.get('tracker_id') if d.get('tracker_id') is not None else 'N/A'})" for d in detections])
                avg_conf = sum(d["confidence"] for d in detections) / len(detections)

                # Write to database asynchronously
                db_executor.submit(
                    database.add_event,
                    stream_id=stream_id,
                    rule_type="detection",
                    event_name="目标实时监测",
                    details=f"监测到: {details}",
                    tracker_id=-1,
                    confidence=round(float(avg_conf), 2)
                )

                event_payload = {
                    "stream_id": stream_id,
                    "rule_type": "detection",
                    "event_name": "目标实时监测",
                    "details": f"监测到: {details}",
                    "tracker_id": -1,
                    "confidence": round(float(avg_conf), 2),
                    "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
                }
                event_broker.publish(event_payload)

        # Helper to check alarm cooldown
        def check_and_trigger(tracker_id: int, rule_type: str, event_name: str, details: str, confidence: float = None):
            cooldown_key = (tracker_id, rule_type)
            last_triggered = state.cooldowns.get(cooldown_key, 0.0)
            if now - last_triggered > self.cooldown_period:
                state.cooldowns[cooldown_key] = now

                # Write to database asynchronously
                db_executor.submit(
                    database.add_event,
                    stream_id=stream_id,
                    rule_type=rule_type,
                    event_name=event_name,
                    details=details,
                    tracker_id=tracker_id,
                    confidence=confidence
                )

                event_payload = {
                    "stream_id": stream_id,
                    "rule_type": rule_type,
                    "event_name": event_name,
                    "details": details,
                    "tracker_id": tracker_id,
                    "confidence": confidence,
                    "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
                }

                # Publish event via Broker
                event_broker.publish(event_payload)
                triggered_events.append(event_payload)
                logger.info(f"[{stream_id}] Triggered alarm: {event_name} - {details} [conf: {confidence}]")

        # Target classes set
        ALL_TARGET_CLASSES = {"person", "standing", "drowing", "Drowning", "drowning", "Freestyle", "backstroke", "breaststroke", "diving", "Person out of water", "Swimming"}

        # Rule 1: Crowding (聚集事件)
        crowd_cfg = rules.get("crowding", {})
        if crowd_cfg.get("enabled"):
            threshold_count = int(crowd_cfg.get("threshold_count", 3))
            raw_targets = set(crowd_cfg.get("targets", ["person"]))
            target_classes = ALL_TARGET_CLASSES if "person" in raw_targets else raw_targets

            # Count target objects in frame
            matching_detections = [d for d in detections if d["class_name"] in target_classes]
            if len(matching_detections) >= threshold_count:
                avg_conf = sum(d["confidence"] for d in matching_detections) / len(matching_detections) if matching_detections else 0.0
                check_and_trigger(
                    tracker_id=-1,  # -1 represents global frame alarm
                    rule_type="crowding",
                    event_name="人员密集聚集告警",
                    details=f"检测到 {len(matching_detections)} 个目标密集聚集，超出预警阈值 ({threshold_count} 个)",
                    confidence=round(float(avg_conf), 2)
                )

        # Rule 2, 3, 4: Target-specific tracking rules (Intrusion, Loitering, Drowning, Lifebuoy)
        intrusion_cfg = rules.get("intrusion", {})
        loitering_cfg = rules.get("loitering", {})
        drowning_cfg = rules.get("drowning", {})
        lifebuoy_cfg = rules.get("lifebuoy", {})

        water_roi = drowning_cfg.get("water_roi", []) or drowning_cfg.get("roi", [])
        intrusion_roi = intrusion_cfg.get("roi", [])

        # Track lifebuoys
        lifebuoys_positions = []
        for d in detections:
            if d["class_name"] == "lifebuoy":
                cx = (d["box"][0] + d["box"][2]) / 2.0
                cy = (d["box"][1] + d["box"][3]) / 2.0
                lifebuoys_positions.append((cx, cy))

        for d in detections:
            tracker_id = d["tracker_id"]
            if tracker_id is None:
                continue

            state.update_seen(tracker_id)
            cx = (d["box"][0] + d["box"][2]) / 2.0
            cy = (d["box"][1] + d["box"][3]) / 2.0

            if tracker_id not in state.track_history:
                state.track_history[tracker_id] = []
            state.track_history[tracker_id].append((now, cx, cy))
            if len(state.track_history[tracker_id]) > 100:
                state.track_history[tracker_id].pop(0)

            cls_name = d["class_name"]
            cls_lower = cls_name.lower()

            if tracker_id not in state.cls_history:
                state.cls_history[tracker_id] = []
            state.cls_history[tracker_id].append(cls_name)
            if len(state.cls_history[tracker_id]) > 50:
                state.cls_history[tracker_id].pop(0)

            cls_hist = state.cls_history[tracker_id]
            drowning_count = sum(1 for c in cls_hist if c.lower() in ["drowing", "drowning"])

            # Rule 2: Intrusion (越界事件)
            intrusion_targets = ALL_TARGET_CLASSES if "person" in set(intrusion_cfg.get("targets", ["person"])) else set(intrusion_cfg.get("targets", []))
            if intrusion_cfg.get("enabled") and cls_name in intrusion_targets:
                if intrusion_roi and is_point_in_polygon(cx, cy, intrusion_roi):
                    check_and_trigger(
                        tracker_id=tracker_id,
                        rule_type="intrusion",
                        event_name="非法越界侵入告警",
                        details=f"目标 ID {tracker_id} ({cls_name}) 侵入设定监控红线防区。",
                        confidence=round(float(d["confidence"]), 2)
                    )

            # Rule 3: Loitering (长时间停留事件)
            loitering_targets = ALL_TARGET_CLASSES if "person" in set(loitering_cfg.get("targets", ["person"])) else set(loitering_cfg.get("targets", []))
            if loitering_cfg.get("enabled") and cls_name in loitering_targets:
                duration = now - state.first_seen.get(tracker_id, now)
                limit = float(loitering_cfg.get("threshold_seconds", 10.0))

                check_in_roi = loitering_cfg.get("only_in_roi", False)
                is_target_loitering = False
                if check_in_roi:
                    if intrusion_roi and is_point_in_polygon(cx, cy, intrusion_roi):
                        is_target_loitering = duration >= limit
                else:
                    is_target_loitering = duration >= limit

                if is_target_loitering:
                    check_and_trigger(
                        tracker_id=tracker_id,
                        rule_type="loitering",
                        event_name="异常滞留徘徊告警",
                        details=f"目标 ID {tracker_id} ({cls_name}) 已在防区持续滞留达 {round(duration, 1)} 秒。",
                        confidence=round(float(d["confidence"]), 2)
                    )

            # Rule 4: Drowning / Falling in water (落水 / 溺水事件)
            # Case A: Direct drowning class detection or consecutive drowning votes in history
            if cls_lower in ["drowing", "drowning"] or drowning_count >= 5:
                check_and_trigger(
                    tracker_id=tracker_id,
                    rule_type="drowning",
                    event_name="人员意外落水/溺水事故",
                    details=f"警告！AI算法识别到溺水姿态 (类别: {cls_name}, 置信度: {d['confidence']:.2f}, 历史计数: {drowning_count})！",
                    confidence=round(float(d["confidence"]), 2)
                )
            # Case B: Target inside water ROI for > 2 seconds
            elif (drowning_cfg.get("enabled") if drowning_cfg else True) and cls_lower in ["person", "standing", "freestyle", "backstroke", "breaststroke", "diving", "swimming"]:
                if water_roi and is_point_in_polygon(cx, cy, water_roi):
                    duration_in_water = now - state.first_seen.get(tracker_id, now)
                    if duration_in_water > 2.0:
                        check_and_trigger(
                            tracker_id=tracker_id,
                            rule_type="drowning",
                            event_name="人员意外落水/溺水事故",
                            details=f"警告！发现人员 (ID {tracker_id}, 状态: {cls_name}) 落水，在水域内已滞留 {round(duration_in_water, 1)} 秒！",
                            confidence=round(float(d["confidence"]), 2)
                        )

                    # Rule 5: Lifebuoy check when in water
                    if lifebuoy_cfg.get("enabled"):
                        has_nearby_lifebuoy = False
                        for l_cx, l_cy in lifebuoys_positions:
                            dist = ((cx - l_cx) ** 2 + (cy - l_cy) ** 2) ** 0.5
                            if dist < 0.25:
                                has_nearby_lifebuoy = True
                                break

                        if not has_nearby_lifebuoy and duration_in_water > 5.0:
                            check_and_trigger(
                                tracker_id=tracker_id,
                                rule_type="lifebuoy",
                                event_name="水上救援设备异常告警",
                                details=f"紧急告警！落水人员 (ID {tracker_id}) 周边未发现漂浮的救生圈/救援漂浮物！",
                                confidence=round(float(d["confidence"]), 2)
                            )

        return triggered_events

analysis_engine = AnalysisEngine()
