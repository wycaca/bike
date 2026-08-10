# 骑巡共享电单车管理平台

面向共享电单车运营与运维的模块化单体应用，包含 Vue 3 桌面管理端、Spring Boot 后端，以及 PostgreSQL/PostGIS、Redis 和 Kafka 本地环境。

## 已实现模块

- 登录、Redis 会话、CSRF 与 ADMIN/OPERATOR/AUDITOR 权限控制。
- 用户、组织架构与写操作审计日志。
- 车辆资产、实时地图、车辆详情和高德道路历史轨迹。
- 电子围栏、停车点、停车半径车辆计数和空间违规识别。
- 运营看板、趋势与区域统计、UTF-8 CSV 车辆状态报表。

## 快速启动

复制 `.env.example` 为 `.env.local`，填写高德 Web JS Key 和安全密钥后执行：

```powershell
docker compose --env-file .env.local up -d --build
```

打开 `http://localhost:8081`。Mock 环境默认管理员为 `admin`，密码由 `APP_ADMIN_PASSWORD` 配置；示例文件中的密码仅用于本机开发，部署前必须修改。

详细说明见 [后端本地开发](backend/details/local-development.md)、[桌面端本地开发](frontend/details/local-development.md) 和 [运营模块设计](backend/details/operations-modules.md)。
