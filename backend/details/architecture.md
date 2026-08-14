# 后端总体架构

## 定位

后端同时服务桌面端 `frontend` 和移动端 `app`. 两端使用相同的业务接口, 权限和数据口径, 只在交互方式和展示密度上不同.

当前优先支持管理和运维能力. 用户骑行业务, 支付和营销暂缓.

## 技术选型

- Java 21.
- Spring Boot 4.1.
- PostgreSQL 17.
- MyBatis-Flex 1.11.8.
- PostGIS + TimescaleDB.
- Redis.
- Kafka.
- Flyway.
- Docker Compose.

后端先采用模块化单体. 当前业务规模不需要注册中心, 配置中心, 服务网格和 Kubernetes.
数据库访问层的 Mapper、复杂 SQL、双数据源和事务约束见 `database-access.md`.

## 业务模块

首期模块:

- 车辆资产.
- 车辆地图.
- 最新状态.
- 历史轨迹.
- 雅迪云事件接入.
- 登录, 用户, 组织、角色权限和可配置数据范围.
- 电子围栏, 停车点和空间违规.
- 运营看板、车辆状态和日/月收入报表.
- 持久化报表任务与独立进程异步 CSV 导出.
- 换电、调度、维修等运维任务，支持管理员派单、运维抢单和处理时间线.
- 写操作审计日志.

后续按业务确认增加:

- 车辆告警自动生成运维任务.
- 运维照片、检查单、配件人工成本和自动派单.

调度算法需要单独设计时, 在本目录新增独立文档, 不写入总体架构.

## 当前接口

| 接口 | 用途 |
| --- | --- |
| `GET /api/v1/map/vehicles` | 按地图视野查询聚合点或车辆点. |
| `GET /api/v1/vehicles` | 分页检索车辆资产和最新状态. |
| `GET /api/v1/vehicles/{vehicleId}` | 查询车辆档案和最新状态. |
| `GET /api/v1/vehicles/{vehicleId}/trajectory` | 查询指定时间范围的历史轨迹. |
| `POST /api/v1/mock/yadea/events` | 仅管理员可在 `mock` 环境模拟雅迪云事件. |
| `GET /api/v1/auth/csrf` | 获取 SPA 写请求使用的 CSRF 令牌. |
| `POST /api/v1/auth/login` | 登录并创建 Redis 会话. |
| `GET /api/v1/auth/me` | 查询当前用户和角色. |
| `GET /api/v1/dashboard` | 查询城市运营指标, 趋势和区域分布. |
| `GET /api/v1/reports/revenue` | 查询有界的日/月收入汇总. |
| `POST /api/v1/reports/exports` | 创建持久化异步报表任务. |
| `GET /api/v1/reports/exports/{jobId}` | 查询本人报表任务状态. |
| `GET /api/v1/reports/exports/{jobId}/file` | 流式下载已完成报表. |
| `GET /api/v1/geo/overview` | 查询围栏, 停车点和实时空间违规. |
| `POST/PUT/DELETE /api/v1/geo/...` | 新建, 编辑和停用空间设施. |
| `GET/POST/PUT /api/v1/admin/organizations` | 维护组织架构. |
| `GET/POST/PUT /api/v1/admin/users` | 维护平台用户和密码. 权限、状态或密码变更后立即删除该账号的全部 Redis 会话. |
| `GET /api/v1/admin/audit-logs` | 分页检索审计日志. |
| `GET /api/v1/cities` | 查询当前用户可访问的启用运营城市. |
| `GET/POST/PUT /api/v1/admin/cities` | 维护运营城市、负责组织和地图边界. |
| `POST /api/v1/admin/vehicles` | 单条新增车辆资产. |
| `POST /api/v1/admin/vehicles/batch` | 批量新增车辆并返回逐行跳过原因. |
| `GET/POST /api/v1/ops/tasks` | 查询或创建换电、调度、维修等运维任务. |
| `POST /api/v1/ops/tasks/{taskId}/claim` | 运维人员原子抢单. |
| `PUT /api/v1/ops/tasks/{taskId}/assignment` | 管理员指派或改派运维人员. |
| `POST /api/v1/ops/tasks/{taskId}/{action}` | 释放、开始、完成或取消任务. |

## 数据职责

- PostgreSQL 保存车辆档案和可恢复的业务投影.
- TimescaleDB 保存车辆时序轨迹.
- PostGIS 提供空间索引和地图范围查询.
- Redis 保存最新状态, 登录态, 幂等键和短期业务状态.
- Kafka 缓冲车辆事件并支持异步消费.
- 本地使用 Docker 命名卷保存异步报表; 生产环境使用对象存储保存文件, 图片, 报表和冷数据归档.

Redis 不是唯一持久化来源. Redis 数据丢失后必须可以从 PostgreSQL 恢复.

角色与组织数据范围的默认值、过滤资源和越权处理见 `data-permissions.md`.

业务接口的成功状态、错误 JSON、上传限制和异常映射见 `http-error-contract.md`.

## 无状态要求

- 服务实例不保存本地业务状态.
- 登录和权限状态放 Redis.
- 报表文件不写入容器可写层; 本地写共享卷, 生产写对象存储.
- 多个实例可以直接挂在同一个负载均衡后.
- 后台任务通过 Kafka consumer group 或分布式锁避免重复执行.

## 部署演进

开发和验证阶段:

- 一个 HTTP/Kafka Spring Boot 进程和一个无 Web 端口的报表 Worker 进程.
- PostgreSQL, Redis 和 Kafka 使用 Docker Compose.

试运营阶段:

- 后端至少两个实例.
- 云数据库自动备份.
- Redis 使用高可用版.
- Kafka 优先使用国内云托管服务.

真实车辆上报量明显增长后:

- 将雅迪事件接入进程和 Kafka 消费进程从单体中拆出.
- 管理接口仍保留在原有应用中.
- 只拆部署单元, 不提前拆成大量微服务.

## 已确认约束

- 原始位置以 WGS84 保存.
- 高德地图使用 GCJ-02 展示.
- 原始轨迹保留天数待业务确认.
- 简化轨迹是否永久保存待业务确认.
- 雅迪正式 API 字段以厂商文档为准, 当前只使用 mock 协议.
