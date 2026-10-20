import subprocess
import threading
import queue
import time
import numpy as np
import logging
import os
import cv2
import json
from .config import settings
from .model_wrapper import model_manager

logger = logging.getLogger("Scheduler")

def _start_stderr_drainer(proc, stream_id: str, tag: str):
    """Continuously drain process stderr in background to prevent OS pipe buffer deadlock."""
    stderr_buf = []
    def drain():
        try:
            if proc and proc.stderr:
                for line in iter(proc.stderr.readline, b''):
                    decoded = line.decode('utf-8', errors='ignore').strip()
                    if decoded:
                        stderr_buf.append(decoded)
                        if len(stderr_buf) > 100:
                            stderr_buf.pop(0)
        except Exception:
            pass
    t = threading.Thread(target=drain, name=f"{tag}-{stream_id}-stderr", daemon=True)
    t.start()
    return stderr_buf


class StreamProcessor:
    def __init__(self, stream_id: str, input_url: str, model_name: str, confidence: float, device: str = "cuda", rules_config: str = ""):
        self.stream_id = stream_id
        self.input_url = input_url
        self.model_name = model_name
        self.confidence = confidence
        self.device = device
        self.rules_config = rules_config

        self.width = settings.FRAME_WIDTH
        self.height = settings.FRAME_HEIGHT
        self.frame_size = self.width * self.height * 3 # BGR24: 3 bytes per pixel

        self.input_queue = queue.Queue(maxsize=5)    # Queue for decoding -> inference
        self.output_queue = queue.Queue(maxsize=30)  # Queue for inference -> encoding

        self.running = False
        self.decode_thread = None
        self.encode_thread = None

        # Stats
        self.decode_fps = 0.0
        self.inference_fps = 0.0
        self.status = "Stopped"  # Running, Stopped, Reconnecting, Error
        self.error_msg = ""
        self.current_transport = "tcp"
        self.last_detections = []

        # Cache for parsed rules
        self._raw_rules_str = None
        self._rules_cache = {}

    @property
    def rules_dict(self) -> dict:
        if self._raw_rules_str != self.rules_config:
            self._raw_rules_str = self.rules_config
            if self.rules_config:
                try:
                    self._rules_cache = json.loads(self.rules_config)
                except Exception:
                    self._rules_cache = {}
            else:
                self._rules_cache = {}
        return self._rules_cache

    def start(self):
        if self.running:
            return
        self.running = True
        self.status = "Starting"
        self.error_msg = ""

        self._frames_decoded = 0
        self._frames_inferred = 0
        self._last_fps_calc_time = time.time()

        # Start Threads
        self.decode_thread = threading.Thread(target=self._decode_loop, name=f"dec-{self.stream_id}", daemon=True)
        self.encode_thread = threading.Thread(target=self._encode_loop, name=f"enc-{self.stream_id}", daemon=True)

        self.decode_thread.start()
        self.encode_thread.start()

    def stop(self):
        self.running = False
        self.status = "Stopped"

        # Clear queues
        try:
            while not self.input_queue.empty():
                self.input_queue.get_nowait()
            while not self.output_queue.empty():
                self.output_queue.get_nowait()
        except Exception:
            pass

    def get_latest_frame(self) -> np.ndarray:
        """Fetch the latest decoded frame, discarding stale queued frames to prevent lag."""
        latest = None
        while True:
            try:
                latest = self.input_queue.get_nowait()
            except queue.Empty:
                break
        return latest

    def push_annotated_frame(self, frame: np.ndarray):
        """Push processed frame to encoder queue. Drop if encoder is slow."""
        self._frames_inferred += 1
        if self.output_queue.full():
            try:
                self.output_queue.get_nowait()  # Drop oldest frame to avoid lag
            except queue.Empty:
                pass
        try:
            self.output_queue.put_nowait(frame)
        except queue.Full:
            pass

    def _decode_loop(self):
        reconnect_attempts = 0
        max_reconnects = 9999

        while self.running:
            self.status = "Running" if reconnect_attempts == 0 else "Reconnecting"
            transport = self.current_transport
            logger.info(f"[{self.stream_id}] Starting FFmpeg decoder process using {transport} transport (Attempt {reconnect_attempts + 1})...")

            # Setup decoders
            # Attempt CUDA GPU decoding first, fallback to CPU if fails
            use_gpu = self.device == "cuda"
            is_network_stream = self.input_url.lower().startswith(("rtsp://", "rtmp://", "http://", "https://"))

            decode_cmd = ["ffmpeg", "-y", "-loglevel", "warning"]
            if is_network_stream:
                decode_cmd.extend(["-rtsp_transport", transport, "-timeout", "5000000"])
            decode_cmd.extend([
                "-fflags", "nobuffer",
                "-flags", "low_delay",
                "-probesize", "32768",
                "-analyzeduration", "0"
            ])
            if use_gpu:
                decode_cmd.extend(["-hwaccel", "cuda"])
            decode_cmd.extend([
                "-i", self.input_url,
                "-f", "rawvideo", "-pix_fmt", "bgr24",
                "-s", f"{self.width}x{self.height}",
                "-"
            ])

            session_start_time = time.time()
            frames_read_this_session = 0
            try:
                # Spawn FFmpeg decode subprocess
                proc = subprocess.Popen(
                    decode_cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    bufsize=self.frame_size * 2
                )
                stderr_buf = _start_stderr_drainer(proc, self.stream_id, "dec")

                # Check if it failed immediately (within 1 second)
                time.sleep(1.0)
                if proc.poll() is not None:
                    err_msg = "\n".join(stderr_buf)[-500:]
                    logger.warning(f"[{self.stream_id}] FFmpeg GPU decoder failed to start. Stderr: {err_msg}")

                    if use_gpu:
                        logger.info(f"[{self.stream_id}] Falling back to CPU decoding.")
                        # Fallback CPU decode command with low latency flags
                        decode_cmd = ["ffmpeg", "-y", "-loglevel", "warning"]
                        if is_network_stream:
                            decode_cmd.extend(["-rtsp_transport", transport, "-timeout", "5000000"])
                        decode_cmd.extend([
                            "-fflags", "nobuffer",
                            "-flags", "low_delay",
                            "-probesize", "32768",
                            "-analyzeduration", "0",
                            "-i", self.input_url,
                            "-f", "rawvideo", "-pix_fmt", "bgr24",
                            "-s", f"{self.width}x{self.height}",
                            "-"
                        ])
                        proc = subprocess.Popen(
                            decode_cmd,
                            stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE,
                            bufsize=self.frame_size * 2
                        )
                        stderr_buf = _start_stderr_drainer(proc, self.stream_id, "dec-cpu")
                        time.sleep(0.5)
                        if proc.poll() is not None:
                            err_msg = "\n".join(stderr_buf)[-500:]
                            raise Exception(f"Both GPU and CPU decoders failed to start. {err_msg}")
                    else:
                        raise Exception(f"FFmpeg decoder failed to start. {err_msg}")

                # Read raw frames from stdout
                logger.info(f"[{self.stream_id}] FFmpeg decoder connected successfully.")
                reconnect_attempts = 0
                self.status = "Running"

                while self.running:
                    raw_frame = proc.stdout.read(self.frame_size)
                    if len(raw_frame) < self.frame_size:
                        # Stream finished or broke
                        break

                    frames_read_this_session += 1
                    # Convert raw bytes to numpy array (BGR24)
                    frame = np.frombuffer(raw_frame, dtype=np.uint8).reshape((self.height, self.width, 3))

                    # Update stats
                    self._frames_decoded += 1
                    self._update_fps()

                    # Queue push with keep-newest strategy (dynamic frame dropping)
                    if self.input_queue.full():
                        try:
                            self.input_queue.get_nowait()
                        except queue.Empty:
                            pass
                    self.input_queue.put(frame)

            except Exception as e:
                self.error_msg = str(e)
                logger.error(f"[{self.stream_id}] Decoder error: {e}")
            finally:
                # Cleanup process
                try:
                    proc.stdout.close()
                    proc.stderr.close()
                    proc.terminate()
                    proc.wait(timeout=2)
                except Exception:
                    try:
                        proc.kill()
                    except Exception:
                        pass

                # Switch transport if we got no frames or if connection was extremely unstable (disconnected in <15s)
                session_duration = time.time() - session_start_time
                if frames_read_this_session == 0 or session_duration < 15.0:
                    self.current_transport = "udp" if self.current_transport == "tcp" else "tcp"
                    logger.info(f"[{self.stream_id}] Session lasted only {session_duration:.1f}s with {frames_read_this_session} frames. Switching transport protocol to {self.current_transport} for next attempt.")

            # Reconnection delay
            if self.running:
                reconnect_attempts += 1
                self.status = "Reconnecting"
                logger.info(f"[{self.stream_id}] Reconnecting in 3 seconds (Attempt {reconnect_attempts}/{max_reconnects})...")
                time.sleep(3)

        self.status = "Stopped"

    def _encode_loop(self):
        # Build push URL
        # e.g., rtsp://127.0.0.1:554/live/{stream_id}_yolo?secret=035c73f7-bb6b-4889-a715-d9eb2d1925cc
        push_url = settings.ZLM_PUSH_URL_TEMPLATE.format(
            port=settings.ZLM_RTSP_PORT,
            stream_id=self.stream_id,
            secret=settings.ZLM_SECRET
        )

        while self.running:
            logger.info(f"[{self.stream_id}] Starting FFmpeg encoder process...")

            # Setup push commands
            use_gpu = self.device == "cuda"

            if use_gpu:
                # NVIDIA H.264 Encoder (h264_nvenc), ultra low latency preset for RTX 4080
                encode_cmd = [
                    "ffmpeg", "-y",
                    "-loglevel", "warning",
                    "-f", "rawvideo", "-pix_fmt", "bgr24",
                    "-s", f"{self.width}x{self.height}", "-r", str(settings.TARGET_FPS),
                    "-i", "-",
                    "-c:v", "h264_nvenc",
                    "-preset", "p1",
                    "-tune", "ll",
                    "-zerolatency", "1",
                    "-delay", "0",
                    "-rc", "cbr",
                    "-b:v", "3M",
                    "-maxrate", "4M",
                    "-bufsize", "1M",
                    "-g", "25",
                    "-bf", "0",
                    "-pix_fmt", "yuv420p",
                    "-f", "rtsp",
                    "-rtsp_transport", "tcp",
                    push_url
                ]
            else:
                # Standard x264 software encoder
                encode_cmd = [
                    "ffmpeg", "-y",
                    "-loglevel", "warning",
                    "-f", "rawvideo", "-pix_fmt", "bgr24",
                    "-s", f"{self.width}x{self.height}", "-r", str(settings.TARGET_FPS),
                    "-i", "-",
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-g", "25",
                    "-bf", "0",
                    "-pix_fmt", "yuv420p",
                    "-f", "rtsp",
                    "-rtsp_transport", "tcp",
                    push_url
                ]

            try:
                proc = subprocess.Popen(
                    encode_cmd,
                    stdin=subprocess.PIPE,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.PIPE,
                    bufsize=self.frame_size * 2
                )
                stderr_buf = _start_stderr_drainer(proc, self.stream_id, "enc")

                # Check for startup failures
                time.sleep(1.0)
                if proc.poll() is not None:
                    err_msg = "\n".join(stderr_buf)[-500:]
                    logger.warning(f"[{self.stream_id}] FFmpeg GPU encoder failed. Stderr: {err_msg}")

                    if use_gpu:
                        logger.info(f"[{self.stream_id}] Falling back to CPU x264 encoder.")
                        encode_cmd = [
                            "ffmpeg", "-y",
                            "-loglevel", "warning",
                            "-f", "rawvideo", "-pix_fmt", "bgr24",
                            "-s", f"{self.width}x{self.height}", "-r", str(settings.TARGET_FPS),
                            "-i", "-",
                            "-c:v", "libx264",
                            "-preset", "ultrafast",
                            "-tune", "zerolatency",
                            "-g", "25",
                            "-pix_fmt", "yuv420p",
                            "-f", "rtsp",
                            "-rtsp_transport", "tcp",
                            push_url
                        ]
                        proc = subprocess.Popen(
                            encode_cmd,
                            stdin=subprocess.PIPE,
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.PIPE,
                            bufsize=self.frame_size * 2
                        )
                        stderr_buf = _start_stderr_drainer(proc, self.stream_id, "enc-cpu")
                        time.sleep(0.5)
                        if proc.poll() is not None:
                            err_msg = "\n".join(stderr_buf)[-500:]
                            raise Exception(f"Both GPU and CPU encoders failed to start. {err_msg}")
                    else:
                        raise Exception(f"FFmpeg encoder failed to start. {err_msg}")

                logger.info(f"[{self.stream_id}] FFmpeg encoder pushing streams to ZLMediaKit.")

                # Default frame to feed encoder if queue is starved, prevents disconnects
                blank_frame = np.zeros((self.height, self.width, 3), dtype=np.uint8)
                last_successful_frame = blank_frame

                target_interval = 1.0 / float(settings.TARGET_FPS)
                next_tick = time.perf_counter()

                while self.running:
                    # Drain output_queue to grab the latest annotated frame available
                    try:
                        while not self.output_queue.empty():
                            last_successful_frame = self.output_queue.get_nowait()
                    except Exception:
                        pass

                    # Write BGR24 raw frame to FFmpeg stdin
                    try:
                        proc.stdin.write(last_successful_frame.tobytes())
                    except (OSError, ValueError) as e:
                        logger.warning(f"[{self.stream_id}] Pipe broken in encoder: {e}")
                        break

                    # Precise 25 FPS monotonic timer pacing to ensure smooth, continuous video stream
                    next_tick += target_interval
                    sleep_time = next_tick - time.perf_counter()
                    if sleep_time > 0:
                        time.sleep(sleep_time)
                    else:
                        # Reset tick timing if fallen behind
                        next_tick = time.perf_counter()

            except Exception as e:
                logger.error(f"[{self.stream_id}] Encoder error: {e}")
            finally:
                # Clean up encoder process
                try:
                    proc.stdin.close()
                    proc.stderr.close()
                    proc.terminate()
                    proc.wait(timeout=2)
                except Exception:
                    try:
                        proc.kill()
                    except Exception:
                        pass

            if self.running:
                logger.info(f"[{self.stream_id}] Restarting encoder in 3 seconds...")
                time.sleep(3)

    def _update_fps(self):
        now = time.time()
        elapsed = now - self._last_fps_calc_time
        if elapsed >= 2.0: # recalculate every 2 seconds
            self.decode_fps = round(self._frames_decoded / elapsed, 1)
            self.inference_fps = round(self._frames_inferred / elapsed, 1)
            self._frames_decoded = 0
            self._frames_inferred = 0
            self._last_fps_calc_time = now


class InferenceScheduler:
    def __init__(self):
        self.processors = {}  # stream_id -> StreamProcessor
        self.lock = threading.Lock()
        self.scheduler_thread = None
        self.running = False

    def start(self):
        with self.lock:
            if self.running:
                return
            self.running = True
            self.scheduler_thread = threading.Thread(target=self._scheduler_loop, name="InferenceScheduler", daemon=True)
            self.scheduler_thread.start()
            logger.info("InferenceScheduler started successfully.")

    def stop(self):
        with self.lock:
            self.running = False
            for pid, proc in list(self.processors.items()):
                proc.stop()
            self.processors.clear()
        if self.scheduler_thread:
            self.scheduler_thread.join(timeout=3)
        logger.info("InferenceScheduler stopped.")

    def add_stream(self, stream_id: str, input_url: str, model_name: str, confidence: float, device: str = "cuda", rules_config: str = ""):
        with self.lock:
            if stream_id in self.processors:
                logger.warning(f"Stream {stream_id} already exists in scheduler. Stopping old one.")
                self.processors[stream_id].stop()

            proc = StreamProcessor(stream_id, input_url, model_name, confidence, device, rules_config)
            proc.start()
            self.processors[stream_id] = proc
            logger.info(f"Stream '{stream_id}' added to scheduler with rules and started.")

    def remove_stream(self, stream_id: str):
        with self.lock:
            if stream_id in self.processors:
                proc = self.processors.pop(stream_id)
                proc.stop()
                logger.info(f"Stream '{stream_id}' stopped and removed from scheduler.")

    def update_stream_params(self, stream_id: str, model_name: str, confidence: float):
        with self.lock:
            if stream_id in self.processors:
                proc = self.processors[stream_id]
                proc.model_name = model_name
                proc.confidence = confidence
                logger.info(f"Stream '{stream_id}' parameters updated: model={model_name}, confidence={confidence}.")

    def get_stream_stats(self, stream_id: str) -> dict:
        with self.lock:
            if stream_id in self.processors:
                proc = self.processors[stream_id]
                return {
                    "status": proc.status,
                    "decode_fps": proc.decode_fps,
                    "inference_fps": proc.inference_fps,
                    "error_msg": proc.error_msg
                }
            return {"status": "Stopped", "decode_fps": 0, "inference_fps": 0, "error_msg": "Not in scheduler"}

    def _scheduler_loop(self):
        while self.running:
            # Gather active streams
            with self.lock:
                active_processors = [p for p in self.processors.values() if p.running]

            if not active_processors:
                time.sleep(0.01)
                continue

            # Group active streams by model, confidence, and device to do Batch Inference
            # model_to_groups = { model_name: { confidence: { device: [ (stream_id, frame_ndarray) ] } } }
            model_to_groups = {}

            for proc in active_processors:
                frame = proc.get_latest_frame()
                if frame is not None:
                    m_name = proc.model_name
                    conf = proc.confidence
                    dev = proc.device

                    if m_name not in model_to_groups:
                        model_to_groups[m_name] = {}
                    if conf not in model_to_groups[m_name]:
                        model_to_groups[m_name][conf] = {}
                    if dev not in model_to_groups[m_name][conf]:
                        model_to_groups[m_name][conf][dev] = []

                    model_to_groups[m_name][conf][dev].append((proc.stream_id, frame))

            # If no new frames were collected across all streams, wait a tiny bit and poll again
            has_frames = False
            for m_dict in model_to_groups.values():
                for c_dict in m_dict.values():
                    for c_list in c_dict.values():
                        if len(c_list) > 0:
                            has_frames = True
                            break

            if not has_frames:
                time.sleep(0.002) # sleep 2ms to prevent CPU spinning
                continue

            # Perform Batch Inference for each model + confidence + device group
            for model_name, conf_groups in model_to_groups.items():
                for conf_threshold, dev_groups in conf_groups.items():
                    for device_name, items in dev_groups.items():
                        if not items:
                            continue

                        # Extract frames
                        frames = [item[1] for item in items]
                        stream_ids = [item[0] for item in items]

                        # Batch predict (Runs forward pass on the specified device)
                        annotated_frames, raw_detections_batch = model_manager.predict_batch(
                            model_name=model_name,
                            frames=frames,
                            conf_threshold=conf_threshold,
                            device=device_name,
                            stream_ids=stream_ids
                        )

                        # Push back results
                        with self.lock:
                            for idx, stream_id in enumerate(stream_ids):
                                if stream_id in self.processors:
                                    proc = self.processors[stream_id]
                                    raw_detections = raw_detections_batch[idx]
                                    proc.last_detections = raw_detections

                                    # Perform Behavior Analysis
                                    from .analysis_engine import analysis_engine, event_broker
                                    frame = annotated_frames[idx]
                                    analysis_engine.process_frame(
                                        stream_id=stream_id,
                                        detections=raw_detections,
                                        rules_config_str=proc.rules_config,
                                        frame=frame,
                                        stream_threshold=proc.confidence
                                    )

                                    # Calculate real-time detection counts per class
                                    counts = {}
                                    for det in raw_detections:
                                        cls = det["class_name"]
                                        counts[cls] = counts.get(cls, 0) + 1

                                    # Publish realtime stats via EventBroker (in-memory only, no DB writes)
                                    event_broker.publish({
                                        "type": "realtime_stats",
                                        "stream_id": stream_id,
                                        "counts": counts,
                                        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
                                    })

                                    frame = annotated_frames[idx]
                                    try:
                                        # Gather active alarms first
                                        state = analysis_engine.states.get(stream_id)
                                        active_alarms = []
                                        if state:
                                            active_alarms = [rule_type for (tid, rule_type), t in state.cooldowns.items() if time.time() - t < 4.0]

                                        # Draw ROI annotations on frame
                                        rules = proc.rules_dict
                                        if rules:
                                            # Intrusion ROI (Green under normal monitoring, Red under intrusion alarm)
                                            if rules.get("intrusion", {}).get("enabled"):
                                                roi = rules.get("intrusion", {}).get("roi", [])
                                                if roi:
                                                    pts = np.array([[int(p[0] * proc.width), int(p[1] * proc.height)] for p in roi], np.int32)
                                                    pts = pts.reshape((-1, 1, 2))
                                                    color = (0, 0, 255) if "intrusion" in active_alarms else (0, 255, 0)
                                                    cv2.polylines(frame, [pts], True, color, 2)
                                                    cv2.putText(frame, "INTRUSION ROI", (pts[0][0][0], pts[0][0][1] - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.4, color, 1)

                                            # Drowning water ROI (Blue under normal monitoring, Red under drowning alarm)
                                            if rules.get("drowning", {}).get("enabled"):
                                                roi = rules.get("drowning", {}).get("water_roi", []) or rules.get("drowning", {}).get("roi", [])
                                                if roi:
                                                    pts = np.array([[int(p[0] * proc.width), int(p[1] * proc.height)] for p in roi], np.int32)
                                                    pts = pts.reshape((-1, 1, 2))
                                                    color = (0, 0, 255) if "drowning" in active_alarms else (255, 0, 0)
                                                    cv2.polylines(frame, [pts], True, color, 2)
                                                    cv2.putText(frame, "WATER ROI", (pts[0][0][0], pts[0][0][1] - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.4, color, 1)

                                            # Draw warning bar if any alarms were active
                                            if active_alarms:
                                                cv2.rectangle(frame, (0, 0), (proc.width, 30), (0, 0, 255), -1)
                                                cv2.putText(frame, f"ALARM: {', '.join(set(active_alarms)).upper()} DETECTED!", (15, 20), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2)
                                    except Exception as overlay_err:
                                        logger.error(f"Failed to draw overlay on frame: {overlay_err}")

                                    proc.push_annotated_frame(frame)

            # Small throttle to yield CPU execution
            time.sleep(0.001)

scheduler = InferenceScheduler()
