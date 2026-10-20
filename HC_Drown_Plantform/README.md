# 黄村乡 AI 防溺水智能管理系统 - 后端服务 (Spring Boot 3.x)

本项目为黄村乡 AI 防溺水智能管理系统的业务管理平台后端服务，负责处理设备管理、水域围栏监测、报警处理、联动决策引擎、人员派发调度、三维轨迹遥测以及数据大屏指标统计等核心业务逻辑。

---

## 🛠️ 技术选型
- **核心框架**：Spring Boot 3.3.5 / Java 21
- **数据库**：PostgreSQL 15 + PostGIS (空间数据库扩展，用以实现地理围栏与最邻近救生力量调度)
- **缓存层**：Redis 7.x (缓存当前在岗救生员与飞行无人机实时定位热数据)
- **消息中介**：Kafka 3.x (异步解耦，推送 AI 疑似求救事件、指令下发与联动控制)
- **持久层**：MyBatis-Plus 3.5.7 (结合 XML 支持 PostGIS 空间语法)
- **权限安全**：Spring Security 6 + JWT
- **数据库迁移**：Flyway (服务启动自动初始化表结构与初始种子数据)
- **接口文档**：SpringDoc OpenAPI 3 (Swagger UI `/swagger-ui.html` 调试入口)
- **审计控制**：基于 AOP 切面实现的统一操作日志审计录入

---

## 📂 项目结构
```
hc-drown-platform/
├── pom.xml                  # 核心 Maven 依赖配置
├── src/main/java/com/hc/drown/
│   ├── HcDrownApplication.java  # 启动类
│   ├── common/              # 公共响应结构、全局异常拦截、Geo工具类及AOP日志切面
│   ├── config/              # Redis, Kafka, Security 及 Swagger 接口文档配置
│   ├── infrastructure/      # Kafka 生产者服务与警报联动消费者监听器 (AlertConfirmedListener)
│   └── module/              # 业务子模块 (Entity, Mapper, Service, Controller)
│       ├── auth/            # 登录注册鉴权模块
│       ├── user/            # RBAC 权限与救生员在岗状态维护
│       ├── device/          # 摄像头、远程音柱、无人机机库空间标点管理
│       ├── alert/           # AI 警报流水与联动日志记录
│       ├── task/            # 救援任务指派与实时 GPS 位置上传
│       ├── drone/           # 无人机巡检路线规划与实时遥测指标上报
│       ├── statistics/      # 大屏 KPI 与 ECharts 图表数据提供
│       └── admin/           # 操作日志查询与文件存储留存清理策略
└── src/main/resources/
    ├── application.yml      # 全局数据库连接、超时参数及 Kafka Topic 配置
    └── db/migration/        # Flyway 迁移脚本 (V1建表, V2初始播种默认权限及测试水域数据)
```

---

## 💿 核心表结构说明 (Flyway 自动执行)
- `sys_user` / `sys_role` / `sys_permission`：基于 RBAC 的权限安全表，内置 `admin` 账号（密码: `Admin@2024`）。
- `water_area`：水域地理表，含有 `boundary` 空间图形字段 (`GEOMETRY(POLYGON, 4326)`)，支持空间包含关系筛选。
- `device`：设备统一注册表，含 `location` 安装坐标字段 (`GEOMETRY(POINT, 4326)`)。
- `alert_event`：AI 检测疑似溺水报警主记录表。
- `rescue_task`：派单救援任务生命周期追踪表。
- `lifeguard_location`：救生员定位历史流水表，用于计算报警点最近人员。
- `drone_flight_log`：无人机巡逻历史遥测轨迹点流水表。
- `audit_log`：记录关键接口的 AOP 审计日志。

---

## 🚀 启动与配置指南

### 1. 前置依赖启动
请确保您本地已经正常启动了以下中间件服务：
- **PostgreSQL 15**（需安装 `postgis` 插件）并创建数据库 `hc_drown`。
- **Redis 7.x** 默认端口 `6379`。
- **Kafka** 默认端口 `9092`。

### 2. Maven 依赖下载配置 (备选)
若您本地编译出现 remote host terminated handshake 的 SSL 下载错误，请修改 `~/.m2/settings.xml` 并加入阿里云公共镜像源：
```xml
<mirror>
    <id>aliyunmaven</id>
    <mirrorOf>*</mirrorOf>
    <name>阿里云公共镜像仓库</name>
    <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### 3. 项目运行
在根项目目录下执行以下指令编译并运行服务：
```bash
mvn clean spring-boot:run
```

启动成功后，后端服务将默认运行在 `http://localhost:8080`，可通过访问 `http://localhost:8080/swagger-ui.html` 在线查阅并调试所有 REST 接口。
