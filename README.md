# 黄村乡防溺水安全监管与监控分发系统

本仓库采用单仓库（Monorepo）管理两个相互集成的系统，保留已有目录名称，方便维护启动脚本和相对路径。

| 目录 | 所属系统 | 内容 | 默认入口 |
| --- | --- | --- | --- |
| `HC_Drown_Plantform/` | 黄村乡防溺水安全监管系统 | Java / Spring Boot 业务后端、数据库迁移和设计文档 | `http://localhost:8080` |
| `HC_Drown_Plantform/frontend/` | 黄村乡防溺水安全监管系统 | Vue 3 + TypeScript + Cesium + ECharts 前端 | `http://localhost:5173` |
| `backend/` | 黄村乡监控分发系统 | Python / FastAPI 后端、视频分析、模型与流媒体集成 | `http://localhost:8000/docs` |
| `frontend/` | 黄村乡监控分发系统 | HTML / CSS / JavaScript 管理前端，由 FastAPI 提供服务 | `http://localhost:8000/platform/` |

`HC_Drown_Plantform` 沿用原有拼写；其下的 `frontend` 和根目录的 `frontend` 分别属于不同系统。

## 仓库结构

```text
drowning_detection_system/
├── HC_Drown_Plantform/
│   ├── frontend/                  # 监管系统 Vue 前端，包含 package-lock.json
│   ├── src/main/java/             # 监管系统 Java 后端
│   ├── src/main/resources/        # 配置、Flyway SQL、静态资源
│   ├── pom.xml
│   └── README.md
├── backend/
│   ├── app/                       # 监控分发后端
│   ├── models/                    # 视觉模型、音频模型示例
│   ├── static/recordings/          # 运行时录像，不纳入 Git
│   ├── requirements.txt
│   └── run.py
├── frontend/                      # 监控分发前端
├── docs/                          # 补充说明及原有分发系统文档
├── .env.example                   # 配置示例，无真实凭据
├── start_system.bat               # 原有 Windows 启动脚本
└── 使用文档.md
```

## 环境要求

- Java 21、Maven；Node.js 20、npm。
- Python 3.10；FFmpeg 加入 PATH。使用 NVIDIA GPU 时另外准备匹配的驱动和 CUDA 版 PyTorch；CPU 模式设置 `USE_CUDA=0`。
- PostgreSQL 15 + PostGIS，创建 `hc_drown` 数据库并启用 PostGIS；Redis 7；Kafka 3。
- ZLMediaKit：项目默认 HTTP/API 端口 `88`、RTSP `554`、RTMP `19355`，媒体服务器配置需与环境变量一致。

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

## 启动

从根目录创建虚拟环境并启动监控分发后端：

```powershell
python -m venv venv
.\venv\Scripts\python.exe -m pip install -r backend/requirements.txt
.\venv\Scripts\python.exe backend/run.py
```

在已加载配置的另一个终端启动监管系统后端：

```powershell
cd HC_Drown_Plantform
mvn spring-boot:run
```

启动监管系统前端：

```powershell
cd HC_Drown_Plantform/frontend
npm ci
npm run dev
```

先启动 PostgreSQL、Redis、Kafka 和 ZLMediaKit。FastAPI 自动提供根目录 `frontend/`，监控分发前端无需 npm 服务。

也可以从已加载环境变量的 PowerShell 执行 `cmd /c start_system.bat`。原有脚本会尝试结束占用 8080、8000、5173 端口的进程；执行前确认这些端口属于本项目。媒体服务器路径按本机位置准备。

## 模型与数据

现有 `backend/models/check1.pt` 和 `yolov8n.pt` 一并提交，默认视觉模型为 `check1.pt`。音频目录中的 ONNX 文件是现有示例占位文件，使用真实音频识别前需要替换为可用模型及对应标签。

SQLite 数据库 `backend/data.db`、录像、日志、缓存、虚拟环境、`node_modules`、`target` 和 `dist` 不提交。分发系统启动时创建 SQLite 表；监管系统使用 Flyway 初始化 PostgreSQL。原有工作流布局属于本机运行状态，参考 `backend/blueprint.example.json`。

原始教程 PDF 未作为源码上传；仓库提供脱敏后的 Markdown 使用指南。本机截图不在项目目录内，指南中对应位置已标明。

## 开发约定

- 所有组件作为普通目录提交，不使用嵌套 Git 仓库或子模块。
- 从 `main` 创建功能分支；提交信息建议使用 `feat:`、`fix:`、`docs:`、`chore:` 前缀。
- 本机参数通过环境变量或被忽略的本地配置维护。数据库备份、录像使用独立存储。
- 当前代码保留原有演示账号及监控控制台访客访问行为。实际部署前需按业务要求配置账户、访问控制和网络权限。

## 更多文档

- [监管系统说明](HC_Drown_Plantform/README.md)
- [监管系统前端说明](HC_Drown_Plantform/frontend/README.md)
- [监控分发原有文档](docs/monitoring-distribution-original.md)（架构背景参考，以本文配置和端口为准）
- [使用文档](使用文档.md)
- [置信度阈值分析报告](置信度阈值分析报告.md)
- [首次导入说明](docs/repository-import.md)
