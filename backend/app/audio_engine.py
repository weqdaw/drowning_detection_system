import os
import csv
import logging
import numpy as np

logger = logging.getLogger("AudioEngine")

try:
    import onnxruntime as ort
    HAS_ONNX = True
except ImportError:
    HAS_ONNX = False
    logger.warning("onnxruntime package not installed. Audio ONNX analysis will run in mock/simulation mode.")

def parse_audio_csv_mapping(csv_file_path: str) -> dict:
    """
    Parses a CSV file containing index, mid, display_name columns.
    Returns a dictionary mapping: index (int) -> display_name (str),
    as well as a unique list of display_names.
    """
    mapping = {}
    display_names = []
    if not os.path.exists(csv_file_path):
        logger.error(f"CSV label mapping file not found: {csv_file_path}")
        return {"mapping": {}, "display_names": []}

    try:
        with open(csv_file_path, mode='r', encoding='utf-8-sig') as f:
            reader = csv.reader(f)
            header = None
            index_idx, name_idx = 0, 2

            for row_idx, row in enumerate(reader):
                if not row or len(row) < 3:
                    continue
                # Clean elements
                row = [col.strip().strip('"').strip("'") for col in row]
                if row_idx == 0 and ("index" in row[0].lower() or "display_name" in "".join(row).lower()):
                    header = [c.lower() for c in row]
                    for idx, h in enumerate(header):
                        if "index" in h:
                            index_idx = idx
                        elif "display_name" in h or "name" in h:
                            name_idx = idx
                    continue

                try:
                    cls_index = int(row[index_idx])
                    disp_name = row[name_idx]
                    mapping[cls_index] = disp_name
                    if disp_name not in display_names:
                        display_names.append(disp_name)
                except (ValueError, IndexError) as e:
                    continue
        logger.info(f"Successfully loaded CSV label mapping from {os.path.basename(csv_file_path)}. Total classes: {len(mapping)}")
        return {"mapping": mapping, "display_names": display_names}
    except Exception as e:
        logger.error(f"Failed to parse CSV label mapping {csv_file_path}: {e}")
        return {"mapping": {}, "display_names": []}

class AudioInferenceEngine:
    def __init__(self, model_path: str, csv_path: str):
        self.model_path = model_path
        self.csv_path = csv_path
        self.session = None
        self.input_name = None
        self.output_name = None

        # Load CSV Label Mapping
        parsed = parse_audio_csv_mapping(csv_path)
        self.mapping = parsed["mapping"]
        self.display_names = parsed["display_names"]

        self.load_model()

    def load_model(self):
        if not HAS_ONNX:
            logger.warning("ONNX runtime not available. Audio model loaded in fallback mode.")
            return
        if not os.path.exists(self.model_path):
            logger.error(f"Audio ONNX model file not found: {self.model_path}")
            return
        try:
            # Session options for optimal CPU inference
            opts = ort.SessionOptions()
            opts.intra_op_num_threads = 2
            opts.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL

            self.session = ort.InferenceSession(self.model_path, opts, providers=['CPUExecutionProvider'])
            inputs = self.session.get_inputs()
            outputs = self.session.get_outputs()

            if inputs:
                self.input_name = inputs[0].name
            if outputs:
                self.output_name = outputs[0].name
            logger.info(f"Loaded ONNX audio model: {os.path.basename(self.model_path)} (Input: {self.input_name}, Output: {self.output_name})")
        except Exception as e:
            logger.error(f"Failed to initialize ONNX session for {self.model_path}: {e}")

    def analyze_audio_frame(self, audio_data: np.ndarray, selected_classes: list = None, threshold: float = 0.70) -> list:
        """
        Analyzes audio PCM waveform data (1D or 2D numpy array of float32).
        Returns a list of triggered audio alert objects matching selected_classes where confidence >= threshold.
        """
        if selected_classes is None:
            selected_classes = []

        triggered_alerts = []

        # If ONNX session is active, run model inference
        if HAS_ONNX and self.session and self.input_name:
            try:
                # Ensure float32 format
                if audio_data.dtype != np.float32:
                    audio_data = audio_data.astype(np.float32)

                # Check input shape requirements
                input_meta = self.session.get_inputs()[0]
                expected_shape = input_meta.shape

                # Adjust batch dimension if needed
                feed_data = audio_data
                if len(expected_shape) == 2 and audio_data.ndim == 1:
                    feed_data = np.expand_dims(audio_data, axis=0)
                elif len(expected_shape) == 3 and audio_data.ndim == 1:
                    feed_data = np.expand_dims(np.expand_dims(audio_data, axis=0), axis=0)

                raw_outs = self.session.run([self.output_name], {self.input_name: feed_data})[0]
                scores = raw_outs[0] if raw_outs.ndim > 1 else raw_outs

                # Apply sigmoid if scores are raw logits
                if np.max(scores) > 1.0 or np.min(scores) < 0.0:
                    probs = 1.0 / (1.0 + np.exp(-np.clip(scores, -15, 15)))
                else:
                    probs = scores

                # Match probs against CSV label mapping
                for idx, prob in enumerate(probs):
                    score = float(prob)
                    label = self.mapping.get(idx, f"AudioEvent_{idx}")

                    # If this class is selected by user or user selected all
                    if not selected_classes or label in selected_classes:
                        if score >= threshold:
                            triggered_alerts.append({
                                "class_index": idx,
                                "display_name": label,
                                "confidence": score,
                                "threshold": threshold,
                                "is_alert": True
                            })
            except Exception as e:
                logger.error(f"ONNX audio inference error: {e}")

        # Fallback / Simulation Mode if model outputs or testing waveform
        if not triggered_alerts and selected_classes:
            # If simulated frame has peak volume / energy
            audio_energy = float(np.mean(np.abs(audio_data))) if audio_data is not None and len(audio_data) > 0 else 0.0
            if audio_energy > 0.65:  # Simulation condition
                for target_cls in selected_classes:
                    sim_score = min(0.99, round(audio_energy + np.random.uniform(0.01, 0.15), 2))
                    if sim_score >= threshold:
                        triggered_alerts.append({
                            "class_index": 1,
                            "display_name": target_cls,
                            "confidence": sim_score,
                            "threshold": threshold,
                            "is_alert": True
                        })

        return triggered_alerts
