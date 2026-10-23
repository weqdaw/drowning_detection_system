# 仓库拆分记录

2026-10-23：监管系统迁移至 [VideoFlow_AI](https://github.com/weqdaw/VideoFlow_AI)，在新仓库根目录包含 `pom.xml`、`src/`、`frontend/` 和 `docs/`。

本仓库保留监控分发系统及历史。移除 HC 目录之外，仅更新 README、跨仓库路径和本说明；其余源文件及模型不变。原版本 `42c5c7104e7bc0c5cf733392a30d9e7bc2033655` 保留于 `archive/before-hc-split-20261023` 分支和提交历史。

联合启动时将两个仓库放在同一个父目录，或设置 `HC_PLATFORM_DIR`。本机正在开发的目录不搬移、不删除；既有嵌套目录仍可被联合启动脚本识别。
