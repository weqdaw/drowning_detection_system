# 黄村乡视频分析与监控分发系统

本仓库保留 Python / FastAPI 视频分析后端及其管理前端。原 `HC_Drown_Plantform` 已独立迁移至 [VideoFlow_AI](https://github.com/weqdaw/VideoFlow_AI)，两个系统通过 HTTP 接口集成。

| 目录 / 项目 | 内容 | 默认入口 |
| --- | --- | --- |
| `backend/` | FastAPI、视频分析、模型及流媒体集成 | `http://localhost:8000/docs` |
| `frontend/` | 由 FastAPI 提供的监控分发管理前端 | `http://localhost:8000/platform/` |
| [VideoFlow_AI](https://github.com/weqdaw/VideoFlow_AI) | 独立 Spring Boot 业务后端与 Vue 监管前端 | 8080 / 5173 |

## 环境要求

Python 3.10、FFmpeg、ZLMediaKit；GPU 模式还需要相应 NVIDIA 驱动及 CUDA 版 PyTorch，CPU 模式可设置 `USE_CUDA=0`。ZLMediaKit 的配置应与 `.env.example` 一致。

## 配置


真实数据库密码、JWT 密钥、媒体服务器密钥和摄像头凭据不提交。参考根目录 `.env.example`，在各服务终端设置环境变量。

Python 和 Spring Boot **不会自动读取本项目的 `.env` 文件**。Windows PowerShell 可复制示例，编辑本地 `.env` 后加载：

```powershell
Copy-Item .env.example .env
notepad .env
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $pair = $line.Split('=', 2)
        [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
    }
}
```

替换所有 `replace-with-...` 值。`JWT_SECRET` 至少 32 字节；`DISTRIBUTION_API_TOKEN` 在 Java 与 Python 中必须一致；`ZLM_SECRET` 必须与 ZLMediaKit 的 API secret 一致。同一终端启动的子进程会继承环境变量；分别打开终端时需分别加载。

摄像头通过设备管理/直播流管理配置，代码不再提供开发者的摄像头密码。`DEMO_CAMERA_RTSP_URL` 默认不设置；设置后会启用原有演示初始化逻辑，该逻辑也会重置演示管理员密码，仅供演示环境使用。

## 单独启动监控分发系统

在已加载环境变量的终端执行：

```powershell
python -m venv venv
.\venv\Scripts\python.exe -m pip install -r backend/requirements.txt
.\venv\Scripts\python.exe backend/run.py
```

FastAPI 会提供根目录的 `frontend/`，无需 npm 服务。

## 与监管系统联动

两个仓库放在同一个父目录：

```powershell
git clone https://github.com/weqdaw/drowning_detection_system.git
git clone https://github.com/weqdaw/VideoFlow_AI.git
```

分别按两个仓库的说明配置环境和依赖。联合启动脚本 `start_system.bat` 默认查找相邻的 `VideoFlow_AI`，也可以通过 `HC_PLATFORM_DIR` 指定监管系统所在目录；仍兼容本机原有的嵌套 HC 目录。

```powershell
$env:HC_PLATFORM_DIR = 'D:\projects\VideoFlow_AI'
cmd /c start_system.bat
```

脚本沿用结束 8080、8000、5173 端口进程的原有行为；本次未运行该脚本。手动分别启动服务可避免该行为。

## 模型与数据


现有 `backend/models/check1.pt` 和 `yolov8n.pt` 一并提交，默认视觉模型为 `check1.pt`。音频目录中的 ONNX 文件是现有示例占位文件，使用真实音频识别前需要替换为可用模型及对应标签。

SQLite 数据库 `backend/data.db`、录像、日志、缓存、虚拟环境、`node_modules`、`target` 和 `dist` 不提交。分发系统启动时创建 SQLite 表；监管系统使用 Flyway 初始化 PostgreSQL。原有工作流布局属于本机运行状态，参考 `backend/blueprint.example.json`。

原始教程 PDF 未作为源码上传；仓库提供脱敏后的 Markdown 使用指南。本机截图不在项目目录内，指南中对应位置已标明。

## 文档与迁移记录

- [监管系统](https://github.com/weqdaw/VideoFlow_AI)
- [Android 终端](https://github.com/weqdaw/drowning_detection_system_android)
- [监控分发原有文档](docs/monitoring-distribution-original.md)
- [使用文档](使用文档.md)
- [置信度阈值分析报告](置信度阈值分析报告.md)
- [仓库拆分记录](docs/repository-split.md)

首次单仓库导入记录保留在 [repository-import.md](docs/repository-import.md) 作为历史说明；当前结构以本 README 为准。
