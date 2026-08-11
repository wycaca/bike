# 骑巡共享电单车管理平台

面向共享电单车运营与运维的模块化单体应用，包含 Vue 3 桌面管理端、安卓移动作业端、Spring Boot 后端，以及 PostgreSQL/PostGIS、Redis 和 Kafka 本地环境。

## 已实现模块

- 登录、Redis 会话、CSRF 与 ADMIN/OPERATOR/AUDITOR 权限控制。
- 用户、组织架构与写操作审计日志。
- 车辆资产、实时地图、车辆详情和高德道路历史轨迹。
- 电子围栏、停车点、停车半径车辆计数和空间违规识别。
- 运营看板、趋势与区域统计、UTF-8 CSV 车辆状态报表。
- 日/月收入报表、单车日均骑行次数、单均收入、单车日均收入及优惠退款分析。
- 持久化异步报表队列、独立报表 Worker、流式 CSV 落盘与下载。
- 换电、调度、维修、巡检、回收和清洁任务，支持自动规则、同车去重、批量建单、高德道路路线、抢单、作业凭证、验收及异常闭环。
- 安卓管理与运维双角色工作台，支持移动派单、抢单、定位、扫码、现场照片和角色页面隔离。

## 快速启动

复制 `.env.example` 为 `.env.local`，填写高德 Web JS Key 和安全密钥后执行：

```powershell
docker compose --env-file .env.local up -d --build
```

桌面端访问 `http://localhost:8081`，移动端本机访问 `http://localhost:18082`，局域网手机访问 `http://192.168.50.204:18082`。Mock 环境默认管理员为 `admin`，北京和上海运维账号分别为 `operator.bj`、`operator.sh`，密码统一由 `APP_ADMIN_PASSWORD` 配置；示例文件中的密码仅用于本机开发，部署前必须修改。

详细说明见 [安卓移动作业端](app/README.md)、[后端本地开发](backend/details/local-development.md)、[数据库访问层](backend/details/database-access.md)、[桌面端本地开发](frontend/details/local-development.md)、[运营模块设计](backend/details/operations-modules.md)、[运维任务与竞品调研](backend/details/operations-tasks.md)、[收入报表口径](backend/details/revenue-reports.md) 和 [异步报表 Worker](backend/details/report-export-worker.md)。
