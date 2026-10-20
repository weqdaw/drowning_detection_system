import os

class Settings:
    # ZLMediaKit Server Config
    ZLM_API_URL: str = os.getenv("ZLM_API_URL", "http://127.0.0.1:88")
    ZLM_SECRET: str = os.environ["ZLM_SECRET"]  # Must match the ZLMediaKit API secret
    DISTRIBUTION_API_TOKEN: str = os.environ["DISTRIBUTION_API_TOKEN"]
    ZLM_RTSP_PORT: int = int(os.getenv("ZLM_RTSP_PORT", 554))
    ZLM_RTMP_PORT: int = int(os.getenv("ZLM_RTMP_PORT", 19355))
    ZLM_HTTP_PORT: int = int(os.getenv("ZLM_HTTP_PORT", 88))

    # Internal pushing config (for RTMP/RTSP push)
    # We will use RTSP push because ZLMediaKit handles it natively and very fast.
    # RTSP push URL: rtsp://127.0.0.1:<RTSP_PORT>/live/<stream_id>_yolo?secret=<ZLM_SECRET>
    ZLM_PUSH_URL_TEMPLATE: str = "rtsp://127.0.0.1:{port}/live/{stream_id}_yolo?secret={secret}"
    ZLM_PLAY_URL_TEMPLATE: str = "http://127.0.0.1:{port}/live/{stream_id}_yolo.live.flv"

    # Model Config
    BASE_DIR: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    MODEL_DIR: str = os.path.join(BASE_DIR, "models")
    DEFAULT_MODEL_NAME: str = "check1.pt"
    DEFAULT_CONFIDENCE_THRESHOLD: float = 0.20  # 依据置信度阈值分析报告.md，溺水预警安全优先设定为 0.20

    # Processing Config
    FRAME_WIDTH: int = 1280
    FRAME_HEIGHT: int = 720
    TARGET_FPS: int = 25

    # DB Config
    DATABASE_PATH: str = os.path.join(BASE_DIR, "data.db")

    # Hardware config (0 for GPU, -1/cpu for CPU)
    DEVICE: str = "cuda" if os.getenv("USE_CUDA", "1") == "1" else "cpu"

settings = Settings()

# Ensure model dir exists
os.makedirs(settings.MODEL_DIR, exist_ok=True)
