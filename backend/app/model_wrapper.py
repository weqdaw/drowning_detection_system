import os
import torch
import logging
import cv2
import numpy as np
import hashlib
from ultralytics import YOLO
from sahi import AutoDetectionModel
from sahi.predict import get_sliced_prediction
from .config import settings

logger = logging.getLogger("ModelManager")

# Enable CUDNN Benchmark for PyTorch GPU Acceleration
if torch.cuda.is_available():
    torch.backends.cudnn.benchmark = True

classNames = ['standing', 'drowing', 'Freestyle', 'backstroke', 'breaststroke', 'diving']

CLASS_COLORS = {
    'standing':            (0, 200, 0),      # 绿色
    'drowing':             (0, 0, 255),      # 红色
    'Drowning':            (0, 0, 255),      # 红色
    'drowning':            (0, 0, 255),      # 红色
    'Freestyle':           (255, 120, 0),    # 橙蓝色
    'backstroke':          (0, 220, 255),    # 青黄色
    'breaststroke':        (200, 0, 200),    # 紫色
    'diving':              (255, 255, 0),    # 青色
    'Person out of water': (0, 200, 0),
    'Swimming':            (255, 120, 0),
    'person':              (0, 200, 0),
    'unknown':             (128, 128, 128),  # 灰色
}

from deep_sort_realtime.deepsort_tracker import DeepSort

def get_category_color(category: str):
    """为每个类别生成固定颜色（BGR）"""
    if category in CLASS_COLORS:
        return CLASS_COLORS[category]
    hv = int(hashlib.md5(category.encode('utf-8')).hexdigest()[:6], 16)
    return ((hv % 180) + 40, ((hv // 180) % 180) + 40, ((hv // 32400) % 180) + 40)

def remove_duplicate_detections(detections, iou_thresh=0.85, contain_thresh=0.85):
    """
    去除重复检测框（根据 IoU 与包含比进行合并，精细去重抑制幻觉虚框）
    严格参照滑块跟踪检测.py: IOU_THRESH = 0.85, CONTAIN_THRESH = 0.85
    Args:
        detections: [([x, y, w, h], score, category), ...]
    """
    if len(detections) <= 1:
        return detections
    dets = sorted(detections, key=lambda d: -d[1])
    keep = []
    for det in dets:
        x1, y1, w1, h1 = det[0]
        bx1_1, by1_1, bx2_1, by2_1 = x1, y1, x1 + w1, y1 + h1
        area1 = w1 * h1
        cat1 = det[2]
        suppressed = False
        for kept in keep:
            if kept[2] != cat1:
                continue
            x2, y2, w2, h2 = kept[0]
            bx1_2, by1_2, bx2_2, by2_2 = x2, y2, x2 + w2, y2 + h2
            area2 = w2 * h2
            ix1 = max(bx1_1, bx1_2); iy1 = max(by1_1, by1_2)
            ix2 = min(bx2_1, bx2_2); iy2 = min(by2_1, by2_2)
            if ix2 <= ix1 or iy2 <= iy1:
                continue
            inter = (ix2 - ix1) * (iy2 - iy1)
            union = area1 + area2 - inter
            iou = inter / union if union > 0 else 0.0
            contain = inter / min(area1, area2) if min(area1, area2) > 0 else 0.0

            if iou > iou_thresh or contain > contain_thresh:
                suppressed = True
                break

        if not suppressed:
            keep.append(det)

    return keep

class ModelManager:
    def __init__(self):
        self._loaded_models = {}  # model_name -> YOLO model instance
        self._model_devices = {}   # model_name -> str device name
        self._sahi_models = {}    # model_name -> SAHI AutoDetectionModel instance
        self._trackers = {}       # stream_id -> DeepSort instance

    def get_tracker(self, stream_id: str = "default") -> DeepSort:
        if stream_id not in self._trackers:
            self._trackers[stream_id] = DeepSort(max_age=30, n_init=3, embedder='mobilenet')
        return self._trackers[stream_id]

    def load_model(self, model_name: str, device: str = None) -> YOLO:
        """Load a YOLO model from pt file. Downloads default if not present."""
        if device is None:
            device = settings.DEVICE
        if device == "cuda" and not torch.cuda.is_available():
            device = "cpu"

        if model_name in self._loaded_models:
            model = self._loaded_models[model_name]
            if self._model_devices.get(model_name) != device:
                try:
                    model.to(device)
                    self._model_devices[model_name] = device
                except Exception as e:
                    logger.error(f"Failed to move loaded model to device {device}: {e}")
            return model

        model_path = os.path.join(settings.MODEL_DIR, model_name)
        if model_name == settings.DEFAULT_MODEL_NAME and not os.path.exists(model_path):
            logger.info(f"Default model {model_name} not found. YOLO will download it to {model_path}.")
            model = YOLO(model_path, task='detect')
        else:
            if not os.path.exists(model_path):
                logger.warning(f"Requested model {model_name} not found at {model_path}. Falling back to default model.")
                return self.load_model(settings.DEFAULT_MODEL_NAME, device=device)
            model = YOLO(model_path)

        try:
            model.to(device)
            self._model_devices[model_name] = device
        except Exception as e:
            logger.error(f"Failed to move model to device {device}: {e}")
        logger.info(f"Successfully loaded model '{model_name}' on device '{device}'.")

        try:
            dummy_img = np.zeros((settings.FRAME_HEIGHT, settings.FRAME_WIDTH, 3), dtype=np.uint8)
            model.predict(dummy_img, device=device, verbose=False)
            logger.info(f"Model '{model_name}' warmed up successfully.")
        except Exception as e:
            logger.error(f"Warm up failed for model '{model_name}': {e}")

        self._loaded_models[model_name] = model
        return model

    def load_sahi_model(self, model_name: str, device: str = None, conf_threshold: float = 0.20):
        if device is None:
            device = settings.DEVICE
        if device == "cuda" and not torch.cuda.is_available():
            device = "cpu"
        device_str = "cuda:0" if device == "cuda" else "cpu"

        cache_key = f"{model_name}_{device_str}_{conf_threshold}"
        if cache_key in self._sahi_models:
            return self._sahi_models[cache_key]

        model_path = os.path.join(settings.MODEL_DIR, model_name)
        if not os.path.exists(model_path):
            model_path = os.path.join(settings.MODEL_DIR, settings.DEFAULT_MODEL_NAME)

        try:
            sahi_model = AutoDetectionModel.from_pretrained(
                model_type='ultralytics',
                model_path=model_path,
                device=device_str,
                confidence_threshold=conf_threshold
            )
            self._sahi_models[cache_key] = sahi_model
            logger.info(f"Loaded SAHI AutoDetectionModel for {model_name} on {device_str}")
            return sahi_model
        except Exception as e:
            logger.error(f"Failed to load SAHI model {model_name}: {e}")
            return None

    def unload_model(self, model_name: str):
        """Unload model instance from memory"""
        if model_name in self._loaded_models:
            del self._loaded_models[model_name]
            logger.info(f"Unloaded model '{model_name}' from memory.")

    def predict_batch(self, model_name: str, frames: list, conf_threshold: float = None, device: str = None, use_sahi: bool = True, stream_ids: list = None) -> tuple:
        """
        Run inference on a batch of frames using SAHI fine-grained sliced detection and DeepSort tracking.
        Fixed 640x640 slice size, 50% overlap, 0.85 IoU/Contain duplicate removal, and 0.20 default threshold.
        ONLY drowning (drowing/drowning) detections/tracks are drawn and returned.
        """
        if not frames:
            return [], []

        if device is None:
            device = settings.DEVICE
        if device == "cuda" and not torch.cuda.is_available():
            device = "cpu"

        if conf_threshold is None:
            conf_threshold = settings.DEFAULT_CONFIDENCE_THRESHOLD

        effective_conf = max(0.05, min(float(conf_threshold), 0.85))

        # 保持与训练/测试脚本 (滑块跟踪检测.py) 完全一致的 SAHI 640x640 切片与 0.5 重叠率
        slice_h, slice_w = 640, 640
        overlap_h, overlap_w = 0.5, 0.5

        # Attempt SAHI sliced prediction
        if use_sahi:
            sahi_model = self.load_sahi_model(model_name, device=device, conf_threshold=effective_conf)
            if sahi_model is not None:
                annotated_frames = []
                raw_detections_batch = []
                for idx, frame in enumerate(frames):
                    stream_id = stream_ids[idx] if (stream_ids and idx < len(stream_ids)) else "default"
                    tracker = self.get_tracker(stream_id)

                    h, w = frame.shape[:2]
                    frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                    try:
                        result = get_sliced_prediction(
                            frame_rgb,
                            sahi_model,
                            slice_height=slice_h,
                            slice_width=slice_w,
                            overlap_height_ratio=overlap_h,
                            overlap_width_ratio=overlap_w,
                            confidence_threshold=effective_conf,
                            verbose=0
                        )

                        raw_dets = []
                        for pred in result.object_prediction_list:
                            bbox = pred.bbox.to_xyxy()
                            x1, y1, x2, y2 = int(bbox[0]), int(bbox[1]), int(bbox[2]), int(bbox[3])
                            bw, bh = max(1, x2 - x1), max(1, y2 - y1)

                            category_id = int(pred.category.id)
                            if category_id < len(classNames):
                                category = classNames[category_id]
                            else:
                                category = pred.category.name

                            # 【核心要求】：只显示和处理 drowning 溺水类别，其余类别 (standing, freestyle, backstroke, breaststroke, diving 等) 全部过滤排除
                            if category.lower() not in ["drowing", "drowning"]:
                                continue

                            # 过滤微小像素噪点 (防止背景噪点产生虚假目标框)
                            if bw < 14 or bh < 14 or (bw * bh) < 200:
                                continue

                            score = float(pred.score.value)
                            raw_dets.append(([x1, y1, bw, bh], score, category))

                        # Apply duplicate removal matching 滑块跟踪检测.py (iou_thresh=0.85, contain_thresh=0.85)
                        filtered_dets = remove_duplicate_detections(raw_dets, iou_thresh=0.85, contain_thresh=0.85)

                        # Update DeepSORT tracker
                        tracks = tracker.update_tracks(filtered_dets, frame=frame_rgb)

                        annotated = frame.copy()
                        frame_raw_dets = []
                        for track in tracks:
                            # 必须为已确认轨迹，且当前帧有实际检测匹配 (time_since_update <= 1)，彻底消除虚假漂移虚框
                            if not track.is_confirmed() or track.time_since_update > 1:
                                continue
                            track_id = track.track_id
                            tx1, ty1, tx2, ty2 = track.to_tlbr()
                            fx1, fy1, fx2, fy2 = int(tx1), int(ty1), int(tx2), int(ty2)
                            fw, fh = max(1, fx2 - fx1), max(1, fy2 - fy1)

                            matched_category = track.get_det_class()
                            matched_score = track.get_det_conf()

                            # 再次严格检查：只显示 drowning 溺水类别，其他任何类别都不绘制也不返回
                            if matched_category is None or matched_category.lower() not in ["drowing", "drowning"]:
                                continue
                            if matched_score is None or matched_score <= 0.0:
                                continue

                            color = get_category_color(matched_category)

                            line_thickness = 1 if (fw < 40 or fh < 40) else 2
                            font_scale = 0.4 if (fw < 40 or fh < 40) else 0.5

                            # Draw bounding box
                            cv2.rectangle(annotated, (fx1, fy1), (fx2, fy2), color, line_thickness)

                            # Draw category label badge
                            label = f"ID:{track_id} {matched_category} {matched_score:.2f}"
                            (lw, lh), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, font_scale, 1)
                            top = max(0, fy1 - lh - 6)
                            cv2.rectangle(annotated, (fx1, top), (fx1 + lw, top + lh + 6), color, -1)
                            cv2.putText(annotated, label, (fx1, top + lh + 2), cv2.FONT_HERSHEY_SIMPLEX, font_scale, (255, 255, 255), 1)

                            # Normalized coordinates
                            nx1 = max(0.0, min(1.0, fx1 / float(w)))
                            ny1 = max(0.0, min(1.0, fy1 / float(h)))
                            nx2 = max(0.0, min(1.0, fx2 / float(w)))
                            ny2 = max(0.0, min(1.0, fy2 / float(h)))

                            frame_raw_dets.append({
                                "box": [nx1, ny1, nx2, ny2],
                                "class_name": matched_category,
                                "confidence": float(matched_score),
                                "tracker_id": int(track_id) if str(track_id).isdigit() else track_id
                            })

                        annotated_frames.append(annotated)
                        raw_detections_batch.append(frame_raw_dets)
                    except Exception as sahi_err:
                        logger.error(f"SAHI prediction failed on frame: {sahi_err}")
                        annotated_frames.append(frame)
                        raw_detections_batch.append([])
                return annotated_frames, raw_detections_batch

        # Standard YOLO fallback
        try:
            model = self.load_model(model_name, device=device)
            use_half = (device == "cuda")

            with torch.inference_mode():
                results = model.predict(
                    source=frames,
                    device=device,
                    conf=effective_conf,
                    half=use_half,
                    verbose=False
                )

            annotated_frames = []
            raw_detections_batch = []
            for r in results:
                annotated = frame.copy() if (frames and hasattr(frames[0], 'copy')) else r.orig_img
                raw_detections = []
                if r.boxes is not None:
                    names_dict = getattr(r, 'names', {})
                    for box in r.boxes:
                        cls_id = int(box.cls[0].cpu().item())
                        class_name = names_dict.get(cls_id, str(cls_id))
                        # 仅保留 drowning 溺水类别
                        if class_name.lower() not in ["drowing", "drowning"]:
                            continue
                        conf = float(box.conf[0].cpu().item())
                        coords = box.xyxyn[0].cpu().tolist()

                        tracker_id = None
                        if box.id is not None:
                            tracker_id = int(box.id[0].cpu().item())

                        raw_detections.append({
                            "box": coords,
                            "class_name": class_name,
                            "confidence": conf,
                            "tracker_id": tracker_id
                        })

                        b_coords = box.xyxy[0].cpu().tolist()
                        fx1, fy1, fx2, fy2 = int(b_coords[0]), int(b_coords[1]), int(b_coords[2]), int(b_coords[3])
                        cv2.rectangle(annotated, (fx1, fy1), (fx2, fy2), (0, 0, 255), 2)
                        label = f"{class_name} {conf:.2f}"
                        cv2.putText(annotated, label, (fx1, max(0, fy1 - 5)), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 2)

                annotated_frames.append(annotated)
                raw_detections_batch.append(raw_detections)

            return annotated_frames, raw_detections_batch
        except Exception as e:
            logger.error(f"Error running inference for model '{model_name}': {e}")
            return frames, [[] for _ in frames]


    def list_available_models(self) -> list:
        """List all .pt models in the models directory"""
        try:
            files = os.listdir(settings.MODEL_DIR)
            models = [f for f in files if f.endswith(".pt")]
            # Guarantee default model is listed
            if settings.DEFAULT_MODEL_NAME not in models:
                models.append(settings.DEFAULT_MODEL_NAME)
            return sorted(list(set(models)))
        except Exception as e:
            logger.error(f"Failed to list models: {e}")
            return [settings.DEFAULT_MODEL_NAME]

    def get_model_classes(self, model_name: str) -> list:
        """Load the model and extract recognizable classes."""
        try:
            model = self.load_model(model_name, device="cpu")
            model_names = []
            if hasattr(model, 'names') and model.names:
                model_names = list(model.names.values())
            combined = list(dict.fromkeys(classNames + model_names))
            return combined
        except Exception as e:
            logger.error(f"Failed to get classes for model '{model_name}': {e}")
            return classNames

class AudioModelManager:
    def __init__(self):
        self.audio_dir = os.path.join(settings.MODEL_DIR, "audio")
        os.makedirs(self.audio_dir, exist_ok=True)
        self._loaded_engines = {}  # model_name -> AudioInferenceEngine

    def get_audio_dir(self) -> str:
        return self.audio_dir

    def list_audio_models(self) -> list:
        """Finds all pairs of .onnx and .csv files in models/audio directory."""
        try:
            files = os.listdir(self.audio_dir)
            onnx_files = [f for f in files if f.endswith(".onnx")]
            result = []
            for onnx_file in onnx_files:
                base_name = os.path.splitext(onnx_file)[0]
                csv_file = f"{base_name}.csv"
                csv_path = os.path.join(self.audio_dir, csv_file)
                has_csv = os.path.exists(csv_path)

                # Parse CSV display_names if csv exists
                display_names = []
                if has_csv:
                    from .audio_engine import parse_audio_csv_mapping
                    parsed = parse_audio_csv_mapping(csv_path)
                    display_names = parsed.get("display_names", [])

                result.append({
                    "model_name": onnx_file,
                    "csv_name": csv_file if has_csv else None,
                    "has_csv": has_csv,
                    "display_names": display_names
                })
            return result
        except Exception as e:
            logger.error(f"Failed to list audio models: {e}")
            return []

    def get_engine(self, model_name: str) -> any:
        """Returns or initializes AudioInferenceEngine for the specified model_name."""
        if model_name in self._loaded_engines:
            return self._loaded_engines[model_name]

        onnx_path = os.path.join(self.audio_dir, model_name)
        base_name = os.path.splitext(model_name)[0]
        csv_path = os.path.join(self.audio_dir, f"{base_name}.csv")

        if not os.path.exists(onnx_path):
            logger.error(f"Audio ONNX model {model_name} not found at {onnx_path}")
            return None

        from .audio_engine import AudioInferenceEngine
        engine = AudioInferenceEngine(onnx_path, csv_path)
        self._loaded_engines[model_name] = engine
        return engine

model_manager = ModelManager()
audio_model_manager = AudioModelManager()
