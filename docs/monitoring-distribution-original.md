# VisiFlow AI - 智能多路视频流视觉分析平台

VisiFlow AI 是一个高效率、低延迟的多路 RTSP 直播流智能视觉分析平台。项目基于 **ZLMediaKit** 统一流媒体管理，使用 **FFmpeg + CUDA** 实现 GPU 硬件加速编解码，并通过 **PyTorch** 在 GPU (如 RTX 4080) 上实现多通道图像帧的 Batch 推理，最终将实时绘制 YOLO 边界框的视频流以超低延迟推回 ZLMediaKit 进行 Web 分发。

---

## 🛠️ 第一部分：流媒体中心 (ZLMediaKit) 安装与运行指南

### 方法一：Windows 本地运行 (推荐)

1. **下载预编译版**：
   - 访问 ZLMediaKit 官方或社区的 [GitHub Releases](https://github.com/ZLMediaKit/ZLMediaKit) 下载适用于 Windows x64 的预编译版本。
   - 或者，您也可以直接在 GitHub Actions 中下载最新的持续集成构建出的 `MediaServer` 压缩包。
2. **启动运行**：
   - 解压下载好的压缩包，进入目录。
   - 双击运行 `MediaServer.exe`，它会使用默认配置并在同级目录下自动生成 `config.ini` 配置文件。
   - **默认端口**：
     - HTTP API 端口：`80` (若与本地 IIS/Nginx 冲突，可修改 `config.ini` 中 `[http]` 下的 `port` 为 `8080`)
     - RTSP 端口：`554`
     - RTMP 端口：`1935`
3. **查看默认 API 密钥 (Secret)**：
   - 用文本编辑器打开生成的 `config.ini`。
   - 找到 `[api]` 部分下的 `secret` 配置项，默认通常为：`035c73f7-bb6b-4889-a715-d9eb2d1925cc`。
   - *注意*：若您更改了 ZLMediaKit 的端口或 `secret` 密钥，请同步修改本项目 `backend/app/config.py` 中的相应配置。

### 方法二：Docker 容器运行 (若系统安装了 Docker)

若您安装了 Docker Desktop，可以在 Powershell 中运行以下一行命令快速部署 ZLMediaKit：

```bash
docker run -id --name zlmediakit -p 80:80 -p 554:554 -p 1935:1935 -p 10000:10000/udp zlmediakit/zlmediakit:latest
```

---

## 💻 第二部分：平台后端部署指南

项目后端采用 Python 3.10 开发，基于 FastAPI 构建 Web APIs，利用 PyTorch/Ultralytics 进行 YOLO 推理。

### 1. 启动虚拟环境并安装依赖

在项目根目录 `d:\desktop\video-ai-platform` 下执行：

```powershell
# 1. 激活虚拟环境 (已由系统初始化创建)
.\venv\Scripts\activate

# 2. 安装 Python 核心依赖 (如未安装)
pip install -r .\backend\requirements.txt
```

*注意：`pip install` 过程中会自动根据系统环境下载 PyTorch。若您的计算机包含 NVIDIA GPU 且驱动完备，系统会自动挂载 CUDA 并开启 GPU 加速推理。*

### 2. 运行平台服务

在激活的虚拟环境中运行：

```powershell
python .\backend\run.py
```

终端输出如下信息时，说明服务成功开启：

```
Starting Video AI Analysis Platform Backend...
Access REST APIs at http://127.0.0.1:8000/docs
Access Web Console at http://127.0.0.1:8000/
```

---

## 🚀 第三部分：平台使用方法与管道功能

1. **打开控制台**：
   浏览器访问 `http://127.0.0.1:8000/`，即可看到设计精美的现代化玻璃质感暗黑系监控看板。
2. **上传自定义模型 (.pt)**：
   - 默认情况下，系统会自动从官方下载并加载轻量型的 `yolov8n.pt` 目标检测模型。
   - 若您有针对特定场景训练好的模型（如防溺水检测、人脸识别等），可直接在网页左侧的 **“算法模型管理”** 框中，将 `.pt` 权重文件拖拽或点击上传，系统支持实时保存并在后续通道中选用。
3. **接入 RTSP 视频流**：
   - 在左侧 **“接入 RTSP 直播流”** 表单中输入流参数：
     - **通道 ID**：例如 `cam01` (需为英文或数字)。
     - **通道名称**：例如 `防溺水监控一号`。
     - **RTSP 源地址**：输入监控摄像头的原始 RTSP URL。
     - **分析模型**：在下拉框中选择刚刚上传的特定 `.pt` 模型（或默认 `yolov8n.pt`）。
     - **置信度阈值**：调节滑动条设置检测灵敏度。
   - 点击 **“添加并启动监控”**。
4. **视觉推理流查看**：
   - 启动后，后端会使用 FFmpeg 自动拉取该通道视频并启动 GPU 加速解码。
   - 推理调度器（Scheduler）会将当前所有活跃视频帧打包进行 **Batch 推理**，最大限度利用 RTX 4080 显卡算力，绘制出 YOLO 边界框。
   - 绘制好边界框的视频帧被送入 FFmpeg 编码器硬编码，推流回 ZLMediaKit。
   - 右侧 **“实时智能监控墙”** 包含集成的 `flv.js` 低延迟播放器，会自动拉取并展示带有目标检测边界框的实时监控画面。

---

## 🛡️ 第四部分：容错与降级设计说明

- **GPU 编解码降级**：
  在启动 FFmpeg 进程进行硬件加速解码（`-hwaccel cuda`）与推流编码（`h264_nvenc`）时，若显卡被独占、驱动不匹配或视频流格式不兼容导致进程异常退出，系统会自动捕获该异常并**自动降级至 CPU 模式**（即 CPU 解码及 `libx264` 软编码），确保系统不会因为硬件调用异常而崩溃，持续维持流推送。
- **动态丢帧机制**：
  系统为每个通道设计了长度为 `1` 的帧读取缓冲区。若显卡推理负载较满导致排队，解码线程会实时覆盖缓冲区内未被读取的旧帧。这从根本上保证了推理管道消费的一定是**最新采集到的实时帧**，彻底消除了常规监控中容易出现的长延时和画面累积卡顿。

## 接口相关文档

- **Access REST APIs**：
  http://127.0.0.1:8000/docs
- **Access Web Console at**：
  http://127.0.0.1:8000/
