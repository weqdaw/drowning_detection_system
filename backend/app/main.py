import os
import shutil
import subprocess
import asyncio
import time
import logging
import psutil
import torch
import json
import hmac
import hashlib
import base64
from pydantic import BaseModel, Field
from fastapi import FastAPI, HTTPException, UploadFile, File, Form, Header, Depends, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles

from .config import settings
from . import database
from .zlm_client import zlm_client
from .scheduler import scheduler
from .model_wrapper import model_manager, audio_model_manager

# Custom Secure Tokens and Password Verification Constants
JWT_SECRET = settings.ZLM_SECRET

def generate_token(username: str) -> str:
    payload = json.dumps({"username": username, "exp": time.time() + 28800}) # 8 hours lifetime
    payload_b64 = base64.urlsafe_b64encode(payload.encode()).decode().rstrip("=")
    sig = hmac.new(JWT_SECRET.encode(), payload_b64.encode(), hashlib.sha256).hexdigest()
    return f"{payload_b64}.{sig}"

def verify_token(token: str) -> str:
    try:
        payload_b64, sig = token.split(".")
        expected_sig = hmac.new(JWT_SECRET.encode(), payload_b64.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expected_sig):
            return None
        padding = "=" * (4 - len(payload_b64) % 4)
        payload_json = base64.urlsafe_b64decode(payload_b64 + padding).decode()
        payload = json.loads(payload_json)
        if payload["exp"] < time.time():
            return None
        return payload["username"]
    except Exception:
        return None

async def get_current_user(authorization: str = Header(default=None)):
    if not authorization:
        # The video distribution console is intentionally available without login.
        # Keep a distinct audit identity and retain token checks for existing callers.
        return "platform_guest"
    try:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Invalid token type")
        token = authorization.split(" ")[1]

        # Bypass authentication for Java backend service token
        if hmac.compare_digest(token, settings.DISTRIBUTION_API_TOKEN):
            return "system"

        username = verify_token(token)
        if not username:
            raise HTTPException(status_code=401, detail="Invalid or expired token")
        return username
    except Exception:
        raise HTTPException(status_code=401, detail="Unauthorized")

def log_action(request: Request, username: str, action: str, details: str):
    ip_addr = request.client.host if request.client else "unknown"
    database.add_audit_log(username, action, details, ip_addr)

class BlueprintLayout(BaseModel):
    nodes: list = Field(default=[])
    connections: list = Field(default=[])

class LoginRequest(BaseModel):
    username: str
    password: str

class AuditLogRequest(BaseModel):
    action: str
    details: str

# Configure Logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger("Main")

app = FastAPI(title="Video AI Analysis Platform API", version="1.0.0")

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
recordings_dir = os.path.join(base_dir, "static", "recordings")
os.makedirs(recordings_dir, exist_ok=True)
app.mount("/static/recordings", StaticFiles(directory=recordings_dir), name="recordings")

frontend_dir = os.path.abspath(os.path.join(base_dir, "..", "frontend"))
if os.path.exists(frontend_dir):
    app.mount("/platform", StaticFiles(directory=frontend_dir, html=True), name="platform")

# Startup & Shutdown Events
@app.on_event("startup")
async def startup_event():
    logger.info("Initializing database...")
    database.init_db()

    # Load default model into memory and warm up
    logger.info("Pre-loading default model...")
    try:
        model_manager.load_model(settings.DEFAULT_MODEL_NAME)
    except Exception as e:
        logger.error(f"Failed to pre-load default model: {e}")

    logger.info("Starting inference scheduler...")
    scheduler.start()

    # Auto-resume previously active streams
    try:
        streams = database.get_all_streams()
        for s in streams:
            if s["is_active"]:
                logger.info(f"Auto-resuming stream: {s['id']}")
                # Check if ZLM is alive, add proxy if so
                pull_url = s["input_url"]
                if zlm_client.is_alive():
                    zlm_client.add_stream_proxy("rtsp", "live", s["id"], s["input_url"])
                    pull_url = f"rtsp://127.0.0.1:{settings.ZLM_RTSP_PORT}/live/{s['id']}"
                scheduler.add_stream(
                    stream_id=s["id"],
                    input_url=pull_url,
                    model_name=s["selected_model"],
                    confidence=s["confidence"],
                    device=s.get("device", "cuda") or "cuda",
                    rules_config=s.get("rules_config") or ""
                )
    except Exception as e:
        logger.error(f"Error resuming streams on startup: {e}")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("Stopping inference scheduler...")
    scheduler.stop()

@app.post("/api/auth/login")
async def login(req: LoginRequest, request: Request):
    user = database.get_user(req.username)
    if not user or not database.verify_password(req.password, user["password_hash"]):
        log_action(request, req.username, "登录失败", f"尝试登录失败，密码错误或用户不存在")
        raise HTTPException(status_code=400, detail="用户名或密码错误")

    token = generate_token(req.username)
    log_action(request, req.username, "登录成功", f"用户成功登录系统")
    return {"token": token, "username": req.username, "role": user["role"]}

@app.get("/api/logs")
async def list_logs(limit: int = 100, current_user: str = Depends(get_current_user)):
    return database.get_audit_logs(limit)

@app.post("/api/logs/audit")
async def write_audit_log(req: AuditLogRequest, request: Request, current_user: str = Depends(get_current_user)):
    log_action(request, current_user, req.action, req.details)
    return {"success": True}

# API schemas
class StreamCreate(BaseModel):
    id: str = Field(..., description="Unique alphanumeric identifier for the stream")
    name: str = Field(..., description="Human readable name for the stream")
    input_url: str = Field(..., description="RTSP source URL")
    selected_model: str = Field(settings.DEFAULT_MODEL_NAME, description="YOLO model file name")
    confidence: float = Field(0.25, ge=0.0, le=1.0, description="Inference confidence threshold")
    device: str = Field("cuda", description="Device to run inference ('cuda' or 'cpu')")

# REST API Endpoints

@app.get("/api/streams")
async def list_streams(current_user: str = Depends(get_current_user)):
    streams = database.get_all_streams()
    # Enrich streams with real-time stats
    enriched = []
    for s in streams:
        stats = scheduler.get_stream_stats(s["id"])

        # Build play URLs
        play_url = settings.ZLM_PLAY_URL_TEMPLATE.format(
            port=settings.ZLM_HTTP_PORT,
            stream_id=s["id"]
        )

        s_dict = dict(s)
        s_dict["status"] = stats["status"]
        s_dict["decode_fps"] = stats["decode_fps"]
        s_dict["inference_fps"] = stats["inference_fps"]
        s_dict["error_msg"] = stats["error_msg"]
        s_dict["play_url"] = play_url
        enriched.append(s_dict)
    return enriched

@app.post("/api/streams")
async def create_stream(stream: StreamCreate, request: Request, current_user: str = Depends(get_current_user)):
    # Validate ID (alphanumeric only to avoid path traversal/URL issues)
    if not stream.id.isalnum():
        raise HTTPException(status_code=400, detail="Stream ID must be alphanumeric only (no special characters or spaces)")

    existing = database.get_stream(stream.id)
    if existing:
        raise HTTPException(status_code=400, detail=f"Stream with ID {stream.id} already exists")

    database.add_stream(
        stream_id=stream.id,
        name=stream.name,
        input_url=stream.input_url,
        selected_model=stream.selected_model,
        confidence=stream.confidence,
        device=stream.device
    )
    log_action(request, current_user, "接入直播流", f"接入通道 {stream.id} ({stream.name})，地址: {stream.input_url}")
    return {"success": True, "message": f"Stream '{stream.name}' created."}

@app.delete("/api/streams/{stream_id}")
async def delete_stream_api(stream_id: str, request: Request, current_user: str = Depends(get_current_user)):
    # Stop processor if running
    scheduler.remove_stream(stream_id)

    # Try to close ZLM proxies
    if zlm_client.is_alive():
        zlm_client.close_streams("rtsp", "__defaultVhost__", "live", stream_id)
        zlm_client.close_streams("rtsp", "__defaultVhost__", "live", f"{stream_id}_yolo")

    database.delete_stream(stream_id)
    log_action(request, current_user, "删除通道", f"删除通道 {stream_id}")
    return {"success": True, "message": f"Stream '{stream_id}' deleted."}

@app.post("/api/streams/{stream_id}/start")
async def start_stream_analysis(stream_id: str, request: Request, current_user: str = Depends(get_current_user)):
    s = database.get_stream(stream_id)
    if not s:
        raise HTTPException(status_code=404, detail="Stream not found")

    # Check ZLMediaKit connectivity, try to register proxy
    pull_url = s["input_url"]
    zlm_connected = zlm_client.is_alive()

    if zlm_connected:
        logger.info(f"ZLMediaKit online. Adding stream proxy for stream: {stream_id}")
        proxy_res = zlm_client.add_stream_proxy("rtsp", "live", stream_id, s["input_url"])
        if proxy_res.get("code") == 0:
            # Change our pull url to ZLM's local RTSP stream URL
            pull_url = f"rtsp://127.0.0.1:{settings.ZLM_RTSP_PORT}/live/{stream_id}"
            logger.info(f"Pulling from local ZLM stream proxy: {pull_url}")
        else:
            logger.warning(f"Failed to add ZLM stream proxy: {proxy_res.get('msg')}. Falling back to direct URL.")
    else:
        logger.warning("ZLMediaKit is offline. Backend will decode direct RTSP camera URL.")

    # Start scheduler processing
    try:
        scheduler.add_stream(
            stream_id=stream_id,
            input_url=pull_url,
            model_name=s["selected_model"],
            confidence=s["confidence"],
            device=s.get("device", "cuda"),
            rules_config=s.get("rules_config") or ""
        )
        database.update_stream_status(stream_id, True)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to start stream processor: {e}")

    log_action(request, current_user, "启动监控分析", f"启动通道 {stream_id}")
    return {"success": True, "zlm_connected": zlm_connected}

@app.post("/api/streams/{stream_id}/stop")
async def stop_stream_analysis(stream_id: str, request: Request, current_user: str = Depends(get_current_user)):
    s = database.get_stream(stream_id)
    if not s:
        raise HTTPException(status_code=404, detail="Stream not found")

    # Stop processor
    scheduler.remove_stream(stream_id)

    # Try to close ZLM streams
    if zlm_client.is_alive():
        # Close player streams and proxy streams
        zlm_client.close_streams("rtsp", "__defaultVhost__", "live", stream_id)
        zlm_client.close_streams("rtsp", "__defaultVhost__", "live", f"{stream_id}_yolo")

    database.update_stream_status(stream_id, False)
    log_action(request, current_user, "停止监控分析", f"停止通道 {stream_id}")
    return {"success": True}

@app.get("/api/models")
async def list_models(current_user: str = Depends(get_current_user)):
    return model_manager.list_available_models()

@app.post("/api/models/upload")
async def upload_model(file: UploadFile = File(...), request: Request = None, current_user: str = Depends(get_current_user)):
    if not file.filename.endswith(".pt"):
        raise HTTPException(status_code=400, detail="Only PyTorch model files (.pt) are supported")

    target_path = os.path.join(settings.MODEL_DIR, file.filename)
    if os.path.exists(target_path):
        raise HTTPException(status_code=400, detail=f"Model file '{file.filename}' already exists")

    try:
        with open(target_path, "wb") as f:
            shutil.copyfileobj(file.file, f)
        logger.info(f"Model '{file.filename}' uploaded and saved to {target_path}")
        log_action(request, current_user, "上传模型", f"上传模型权重文件: {file.filename}")
        return {"success": True, "filename": file.filename}
    except Exception as e:
        logger.error(f"Failed to save uploaded model: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to save model: {e}")

@app.delete("/api/models/{model_name}")
async def delete_model_api(model_name: str, request: Request, current_user: str = Depends(get_current_user)):
    if model_name == settings.DEFAULT_MODEL_NAME:
        raise HTTPException(status_code=400, detail="Cannot delete the default system model")

    target_path = os.path.join(settings.MODEL_DIR, model_name)
    if not os.path.exists(target_path):
        raise HTTPException(status_code=404, detail="Model file not found")

    try:
        model_manager.unload_model(model_name)
        os.remove(target_path)
        logger.info(f"Model '{model_name}' deleted from disk.")
        log_action(request, current_user, "删除模型", f"删除模型权重文件: {model_name}")
        return {"success": True, "message": f"Model '{model_name}' deleted."}
    except Exception as e:
        logger.error(f"Failed to delete model file '{model_name}': {e}")
        raise HTTPException(status_code=500, detail=f"Failed to delete model file: {e}")

# =========================================================================
# Audio Recognition & ONNX Model API Endpoints
# =========================================================================
@app.get("/api/audio/models")
async def list_audio_models(current_user: str = Depends(get_current_user)):
    return audio_model_manager.list_audio_models()

@app.post("/api/audio/upload-model")
async def upload_audio_model(
    onnx_file: UploadFile = File(...),
    csv_file: UploadFile = File(...),
    request: Request = None,
    current_user: str = Depends(get_current_user)
):
    if not onnx_file.filename.endswith(".onnx"):
        raise HTTPException(status_code=400, detail="模型文件必须为 .onnx 格式")
    if not csv_file.filename.endswith(".csv"):
        raise HTTPException(status_code=400, detail="标签映射文件必须为 .csv 格式")

    audio_dir = audio_model_manager.get_audio_dir()
    base_name = os.path.splitext(onnx_file.filename)[0]

    target_onnx_path = os.path.join(audio_dir, f"{base_name}.onnx")
    target_csv_path = os.path.join(audio_dir, f"{base_name}.csv")

    try:
        # Save ONNX file
        with open(target_onnx_path, "wb") as f:
            shutil.copyfileobj(onnx_file.file, f)

        # Save CSV file
        with open(target_csv_path, "wb") as f:
            shutil.copyfileobj(csv_file.file, f)

        logger.info(f"Audio model '{base_name}.onnx' and CSV '{base_name}.csv' uploaded successfully.")

        # Parse CSV file immediately to return recognizable display_name classes
        from .audio_engine import parse_audio_csv_mapping
        parsed = parse_audio_csv_mapping(target_csv_path)

        log_action(request, current_user, "上传音频ONNX模型", f"上传音频模型 {base_name}.onnx 及配套 CSV 标签映射")

        return {
            "success": True,
            "model_name": f"{base_name}.onnx",
            "csv_name": f"{base_name}.csv",
            "display_names": parsed.get("display_names", []),
            "total_classes": len(parsed.get("mapping", {}))
        }
    except Exception as e:
        logger.error(f"Failed to save audio model and CSV files: {e}")
        raise HTTPException(status_code=500, detail=f"保存音频模型失败: {e}")

@app.delete("/api/audio/models/{model_name}")
async def delete_audio_model(model_name: str, request: Request = None, current_user: str = Depends(get_current_user)):
    audio_dir = audio_model_manager.get_audio_dir()
    base_name = os.path.splitext(model_name)[0]

    onnx_path = os.path.join(audio_dir, f"{base_name}.onnx")
    csv_path = os.path.join(audio_dir, f"{base_name}.csv")

    try:
        if os.path.exists(onnx_path):
            os.remove(onnx_path)
        if os.path.exists(csv_path):
            os.remove(csv_path)
        log_action(request, current_user, "删除音频模型", f"删除音频模型文件: {base_name}.onnx及CSV")
        return {"success": True, "message": f"Audio model '{base_name}' deleted."}
    except Exception as e:
        logger.error(f"Failed to delete audio model '{base_name}': {e}")
        raise HTTPException(status_code=500, detail=f"删除音频模型失败: {e}")

@app.get("/api/blueprint")
async def get_blueprint(current_user: str = Depends(get_current_user)):
    blueprint_path = os.path.join(settings.BASE_DIR, "blueprint.json")
    if os.path.exists(blueprint_path):
        try:
            with open(blueprint_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            logger.error(f"Failed to read blueprint.json: {e}")
            raise HTTPException(status_code=500, detail="Failed to read blueprint configuration")
    return {"nodes": [], "connections": []}

@app.post("/api/blueprint")
async def save_blueprint(layout: BlueprintLayout, request: Request, current_user: str = Depends(get_current_user)):
    blueprint_path = os.path.join(settings.BASE_DIR, "blueprint.json")

    # 1. Save blueprint to disk
    try:
        with open(blueprint_path, "w", encoding="utf-8") as f:
            json.dump(layout.dict(), f, indent=2, ensure_ascii=False)
        logger.info("Blueprint layout saved successfully.")
    except Exception as e:
        logger.error(f"Failed to save blueprint layout: {e}")
        raise HTTPException(status_code=500, detail="Failed to save blueprint configuration")

    # 2. Extract mappings from connections & nodes
    connections = layout.connections
    stream_model_map = {}

    for conn in connections:
        source = conn.get("source", "")
        target = conn.get("target", "")
        if source.startswith("stream_") and target.startswith("model_"):
            stream_id = source.replace("stream_", "")
            model_name = target.replace("model_", "")
            stream_model_map[stream_id] = model_name

    stream_device_map = {}
    for node in layout.nodes:
        if node.get("type") == "stream":
            stream_id = node.get("id", "").replace("stream_", "")
            stream_device_map[stream_id] = node.get("device", "cuda")

    # 3. Update database and restart active streams if parameters changed
    try:
        all_streams = database.get_all_streams()
        for s in all_streams:
            stream_id = s["id"]
            new_model = stream_model_map.get(stream_id, settings.DEFAULT_MODEL_NAME)
            old_model = s["selected_model"]

            # Retrieve new device (if stream node is present on blueprint, otherwise default)
            new_device = stream_device_map.get(stream_id, s.get("device", "cuda") or "cuda")
            old_device = s.get("device", "cuda") or "cuda"

            if new_model != old_model or new_device != old_device:
                logger.info(f"Updating stream '{stream_id}': model '{old_model}'->'{new_model}', device '{old_device}'->'{new_device}'")
                database.update_stream_model(stream_id, new_model)
                database.update_stream_device(stream_id, new_device)

                # If stream is currently active, redeploy it with new parameters
                if s["is_active"]:
                    logger.info(f"Redeploying active stream '{stream_id}' to apply blueprint changes...")
                    scheduler.remove_stream(stream_id)

                    pull_url = s["input_url"]
                    if zlm_client.is_alive():
                        zlm_client.add_stream_proxy("rtsp", "live", stream_id, s["input_url"])
                        pull_url = f"rtsp://127.0.0.1:{settings.ZLM_RTSP_PORT}/live/{stream_id}"

                    scheduler.add_stream(
                        stream_id=stream_id,
                        input_url=pull_url,
                        model_name=new_model,
                        confidence=s["confidence"],
                        device=new_device,
                        rules_config=s.get("rules_config") or ""
                    )

    except Exception as e:
        logger.error(f"Failed to apply blueprint configurations: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to apply blueprint configurations: {e}")

    log_action(request, current_user, "部署蓝图配置", "更新工作流并热重载活动监控通道")
    return {"success": True, "message": "Blueprint applied successfully"}

@app.get("/api/system/status")
async def system_status(current_user: str = Depends(get_current_user)):
    # CPU and Memory
    cpu_usage = psutil.cpu_percent()
    memory_info = psutil.virtual_memory()

    # GPU Information
    gpu_available = torch.cuda.is_available()
    gpu_name = "N/A"
    gpu_mem_used = 0.0
    gpu_mem_total = 0.0
    gpu_util = 0.0

    if gpu_available:
        try:
            gpu_name = torch.cuda.get_device_name(0)
            # Memory allocated by PyTorch (GB)
            gpu_mem_used = torch.cuda.memory_allocated(0) / (1024**3)
            gpu_mem_total = torch.cuda.get_device_properties(0).total_memory / (1024**3)

            # Run nvidia-smi to get exact device utilization
            res = subprocess.run(
                ["nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=True
            )
            gpu_util = float(res.stdout.strip())
        except Exception as e:
            logger.debug(f"Failed to query GPU utilization details: {e}")

    # Count streams
    all_streams = database.get_all_streams()
    active_count = sum(1 for s in all_streams if s["is_active"])

    # ZLMediaKit status
    zlm_alive = zlm_client.is_alive()

    return {
        "cpu_usage": cpu_usage,
        "memory_usage": memory_info.percent,
        "memory_free_gb": round(memory_info.available / (1024**3), 2),
        "memory_total_gb": round(memory_info.total / (1024**3), 2),
        "gpu_available": gpu_available,
        "gpu_name": gpu_name,
        "gpu_utilization": gpu_util,
        "gpu_mem_used_gb": round(gpu_mem_used, 2),
        "gpu_mem_total_gb": round(gpu_mem_total, 2),
        "total_streams": len(all_streams),
        "active_streams": active_count,
        "zlm_online": zlm_alive,
        "zlm_rtsp_port": settings.ZLM_RTSP_PORT,
        "zlm_rtmp_port": settings.ZLM_RTMP_PORT,
        "zlm_http_port": settings.ZLM_HTTP_PORT
    }

class RulesConfigRequest(BaseModel):
    rules_config: dict

@app.get("/api/models/{model_name}/classes")
async def get_model_classes_endpoint(model_name: str, current_user: str = Depends(get_current_user)):
    classes = model_manager.get_model_classes(model_name)
    return {"model_name": model_name, "classes": classes}

@app.get("/api/streams/{stream_id}/rules")
async def get_stream_rules(stream_id: str, current_user: str = Depends(get_current_user)):
    s = database.get_stream(stream_id)
    if not s:
        raise HTTPException(status_code=404, detail="Stream not found")
    rules = {}
    if s.get("rules_config"):
        try:
            rules = json.loads(s["rules_config"])
        except Exception:
            pass
    return rules

@app.post("/api/streams/{stream_id}/rules")
async def update_stream_rules_endpoint(stream_id: str, req: RulesConfigRequest, request: Request, current_user: str = Depends(get_current_user)):
    s = database.get_stream(stream_id)
    if not s:
        raise HTTPException(status_code=404, detail="Stream not found")

    rules_json = json.dumps(req.rules_config, ensure_ascii=False)
    database.update_stream_rules(stream_id, rules_json)

    if s["is_active"]:
        if stream_id in scheduler.processors:
            scheduler.processors[stream_id].rules_config = rules_json
            logger.info(f"Hot-loaded new rules_config for stream {stream_id}")

    log_action(request, current_user, "更新规则配置", f"更新通道 {stream_id} 的智能分析规则配置")
    return {"success": True}

@app.get("/api/events")
async def get_events_history(stream_id: str = None, status: str = None, limit: int = 100, current_user: str = Depends(get_current_user)):
    events = database.get_events(stream_id, status, limit)
    return events

class UpdateEventStatusRequest(BaseModel):
    status: str  # 'PENDING' or 'PROCESSED'
    handled_by: str = "MobileAppUser"

@app.get("/api/mobile/events")
async def get_mobile_events(stream_id: str = None, status: str = None, limit: int = 100):
    """移动端专享：获取告警事件列表 (支持 status=PENDING / PROCESSED 筛选)"""
    return database.get_events(stream_id=stream_id, status=status, limit=limit)

@app.post("/api/mobile/events/{event_id}/status")
async def update_mobile_event_status(event_id: int, req: UpdateEventStatusRequest):
    """移动端专享：变更事件处理状态 (待处理 PENDING -> 已处理 PROCESSED)"""
    database.update_event_status(event_id, req.status, req.handled_by)
    return {"success": True, "event_id": event_id, "status": req.status, "handled_by": req.handled_by}

@app.get("/api/events/stream")
async def events_stream(token: str = None):
    # Optional token verification (allows unauthenticated mobile SSE subscription)
    if token:
        verify_token(token)

    from .analysis_engine import event_broker
    q = event_broker.subscribe()

    async def event_generator():
        try:
            while True:
                try:
                    if not q.empty():
                        ev = q.get_nowait()
                        # Wrap event payload to conform to NEW_ALERT structure if needed
                        if isinstance(ev, dict) and "type" not in ev:
                            sse_data = {
                                "type": "NEW_ALERT",
                                "alert": {
                                    "id": ev.get("id", 1),
                                    "eventNo": ev.get("event_no") or f"EVT-{time.strftime('%Y%m%d')}-{ev.get('id', 1)}",
                                    "alertType": "DROWNING" if "drown" in str(ev.get("event_name", "")).lower() else "ABNORMAL",
                                    "alertLocation": ev.get("alertLocation") or "POINT(119.8234 28.4512)",
                                    "waterAreaName": ev.get("waterAreaName") or "黄村水库01防区",
                                    "confidence": ev.get("confidence", 90.0),
                                    "status": ev.get("status", "PENDING"),
                                    "createdAt": ev.get("timestamp") or time.strftime("%Y-%m-%d %H:%M:%S")
                                }
                            }
                        else:
                            sse_data = ev
                        yield f"data: {json.dumps(sse_data, ensure_ascii=False)}\n\n"
                    else:
                        await asyncio.sleep(0.5)
                except Exception as loop_err:
                    logger.error(f"Error in SSE generator loop: {loop_err}")
                    await asyncio.sleep(0.5)
        except asyncio.CancelledError:
            logger.info("SSE client cancelled subscription.")
        finally:
            event_broker.unsubscribe(q)

    return StreamingResponse(event_generator(), media_type="text/event-stream")
# REST APIs for external developer integration

class ChangeModelRequest(BaseModel):
    model_name: str

@app.get("/api/realtime/data")
async def get_realtime_data(current_user: str = Depends(get_current_user)):
    """Get active streams' current real-time inference data and last detections."""
    active_processors = scheduler.processors
    data = []
    with scheduler.lock:
        for stream_id, proc in active_processors.items():
            if not proc.running:
                continue

            # Get latest detections
            last_dets = getattr(proc, "last_detections", [])

            # Format detections for external developer ease of use
            formatted_dets = []
            for d in last_dets:
                formatted_dets.append({
                    "class_name": d["class_name"],
                    "confidence": round(float(d["confidence"]), 3),
                    "bbox": [round(float(c), 3) for c in d["box"]],
                    "tracker_id": d.get("tracker_id")
                })

            # Compile counts
            counts = {}
            for d in last_dets:
                cls = d["class_name"]
                counts[cls] = counts.get(cls, 0) + 1

            data.append({
                "stream_id": stream_id,
                "status": proc.status,
                "selected_model": proc.model_name,
                "device": proc.device,
                "inference_fps": round(proc.inference_fps, 1),
                "decode_fps": round(proc.decode_fps, 1),
                "counts": counts,
                "detections": formatted_dets,
                "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
            })
    return data

@app.post("/api/streams/{stream_id}/model")
async def change_stream_model(stream_id: str, payload: ChangeModelRequest, request: Request, current_user: str = Depends(get_current_user)):
    """Dynamically reload a different model for a stream."""
    s = database.get_stream(stream_id)
    if not s:
        raise HTTPException(status_code=404, detail="Stream not found")

    model_name = payload.model_name

    # Switch in database
    database.update_stream_model(stream_id, model_name)

    is_running = False
    with scheduler.lock:
        is_running = stream_id in scheduler.processors
    if is_running:
        scheduler.remove_stream(stream_id)
        updated_s = database.get_stream(stream_id)
        scheduler.add_stream(
            stream_id=updated_s["id"],
            input_url=updated_s["input_url"],
            model_name=updated_s["selected_model"],
            confidence=updated_s["confidence"],
            device=updated_s["device"],
            rules_config=updated_s.get("rules_config") or ""
        )

    log_action(request, current_user, "热重载模型", f"通道 {stream_id} 动态切换模型为 {model_name}")
    return {"success": True, "message": f"Stream model changed to {model_name}"}

class ChangeConfidenceRequest(BaseModel):
    confidence: float

@app.post("/api/streams/{stream_id}/confidence")
async def change_stream_confidence(stream_id: str, payload: ChangeConfidenceRequest, request: Request, current_user: str = Depends(get_current_user)):
    """Dynamically hot-reload inference confidence threshold and granularity strategy for a stream."""
    s = database.get_stream(stream_id)
    if not s:
        raise HTTPException(status_code=404, detail="Stream not found")

    conf = float(payload.confidence)
    database.update_stream_confidence(stream_id, conf)

    with scheduler.lock:
        if stream_id in scheduler.processors:
            scheduler.processors[stream_id].confidence = conf
            logger.info(f"Updated stream {stream_id} confidence threshold to {conf}")

    log_action(request, current_user, "热重载识别灵敏度", f"通道 {stream_id} 识别阈值/粒度调整为 {conf}")
    return {"success": True, "confidence": conf}

@app.get("/api/streams/{stream_id}/urls")
async def get_stream_urls(stream_id: str, current_user: str = Depends(get_current_user)):
    """Get playback stream play urls for external developers."""
    s = database.get_stream(stream_id)
    if not s:
        raise HTTPException(status_code=404, detail="Stream not found")

    host = "127.0.0.1"

    return {
        "stream_id": stream_id,
        "rtsp": f"rtsp://{host}:{settings.ZLM_RTSP_PORT}/live/{stream_id}_yolo",
        "rtmp": f"rtmp://{host}:{settings.ZLM_RTMP_PORT}/live/{stream_id}_yolo",
        "flv": f"http://{host}:{settings.ZLM_HTTP_PORT}/live/{stream_id}_yolo.live.flv",
        "hls": f"http://{host}:{settings.ZLM_HTTP_PORT}/live/{stream_id}_yolo/hls.m3u8"
    }

@app.get("/api/recordings")
async def get_recordings(stream_id: str = None, limit: int = 100):
    return database.get_recordings(stream_id, limit)

@app.delete("/api/recordings/{recording_id}")
async def delete_recording(recording_id: int, request: Request, current_user: str = Depends(get_current_user)):
    database.delete_recording(recording_id)
    log_action(request, current_user, "删除录像", f"删除录像 ID {recording_id}")
    return {"success": True, "message": "Recording deleted successfully"}

@app.delete("/api/recordings")
async def delete_all_recordings(request: Request, current_user: str = Depends(get_current_user)):
    database.delete_all_recordings()
    log_action(request, current_user, "清空全量录像", "清空全量抓拍录像及物理文件")
    return {"success": True, "message": "All recordings deleted successfully"}

@app.delete("/api/events/{event_id}")
async def delete_event(event_id: int, request: Request, current_user: str = Depends(get_current_user)):
    database.delete_event(event_id)
    log_action(request, current_user, "删除监测事件", f"删除监测事件 ID {event_id}")
    return {"success": True, "message": "Event deleted successfully"}

@app.delete("/api/events")
async def delete_all_events(request: Request, current_user: str = Depends(get_current_user)):
    database.delete_all_events()
    log_action(request, current_user, "清空监测事件", "清空所有监测历史记录")
    return {"success": True, "message": "All events cleared successfully"}

# Serving the static frontend SPA directly

frontend_dir = os.path.join(settings.BASE_DIR, "../frontend")

@app.get("/")
async def serve_index():
    path = os.path.join(frontend_dir, "index.html")
    if os.path.exists(path):
        return FileResponse(path)
    raise HTTPException(status_code=404, detail="index.html not found")

@app.get("/style.css")
async def serve_css():
    path = os.path.join(frontend_dir, "style.css")
    if os.path.exists(path):
        return FileResponse(path, media_type="text/css")
    raise HTTPException(status_code=404, detail="style.css not found")

@app.get("/app.js")
async def serve_js():
    path = os.path.join(frontend_dir, "app.js")
    if os.path.exists(path):
        return FileResponse(path, media_type="application/javascript")
    raise HTTPException(status_code=404, detail="app.js not found")
