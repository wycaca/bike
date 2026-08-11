# 异步报表 Worker

## 目标

大数据量导出不能占用 HTTP 请求线程，也不能让生成文件的堆内存、CPU 峰值或进程故障拖垮车辆、地图、看板等核心接口。项目保留同一份业务代码，但使用两个独立部署进程：

- `backend`: 认证、核心 API、Kafka 消费和报表任务创建、状态查询、文件流式下载。
- `report-worker`: 不启动 Web 服务，不消费车辆遥测，只领取报表任务并生成文件。

## 执行流程

1. API 校验报表参数和单用户任务上限，将任务写入 `report_export_job`，立即返回 `202 Accepted`。
2. Worker 单线程轮询队列，通过 `FOR UPDATE SKIP LOCKED` 原子领取最早的 `PENDING` 任务。
3. 队列状态读写使用 `ReportQueuePool`，报表聚合使用独立只读的 `ReportQueryPool`。
4. CSV 按 UTF-8 流式写入 `.part` 文件，完成后原子重命名，避免 API 读到半个文件。
5. Worker 回写行数、字节数、完成时间和存储键；API 使用 `FileSystemResource` 流式响应，不把整个文件载入 JVM 堆。
6. 前端轮询状态，任务成功后才触发浏览器下载。

两个连接池分别持有 `FlexSqlSessionFactoryBean` 和 `SqlSessionTemplate`。普通 `@Mapper` 使用 `@Primary` 队列会话；`RevenueReportMapper` 和 `VehicleStatusReportMapper` 只从报表会话获取，并且只加载报表查询 XML，防止长查询误用队列连接池。

当前队列支持收入报表和车辆状态报表。车辆状态导出不再保留同步 HTTP 接口，避免全量车辆查询和 CSV 字节数组占用核心 API 的连接池、请求线程和 JVM 堆。

## 隔离边界

| 资源 | 本地 Compose 边界 |
| --- | --- |
| 进程 | `backend` 与 `report-worker` 为不同容器和 JVM |
| CPU | Worker 上限 1 CPU |
| 内存 | Worker 上限 512 MiB |
| 并发 | 单调度线程，同一 Worker 串行生成 |
| 队列连接 | `ReportQueuePool` 最多 1 个连接，只做短事务 |
| 查询连接 | `ReportQueryPool` 最多 1 个只读连接 |
| SQL 时间 | 报表查询默认 120 秒超时 |
| 文件 | Worker 读写共享卷，API 只读挂载 |

本地环境的两个连接池仍指向同一个 PostgreSQL，因此数据库磁盘和 CPU 不可能做到物理上的零竞争；小连接池、串行查询和 SQL 超时把影响限制在明确上限内。生产环境应把 `REPORT_DB_URL` 或 `REPORT_DB_HOST` 等参数指向 PostgreSQL 只读副本；队列池继续连接主库，仅执行领取与状态回写。数据量继续增长时，应把报表事实数据同步到数据仓库或 OLAP，而不是持续放宽主库查询限制。

## 配置

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `REPORT_DB_URL` | 空 | 完整报表查询 JDBC 地址，优先级最高 |
| `REPORT_DB_HOST` | `db` | 报表查询库地址 |
| `REPORT_DB_PORT` | `5432` | 报表查询库端口 |
| `REPORT_DB_NAME` | `bike` | 报表查询数据库 |
| `REPORT_DB_USER` | `bike` | 报表只读账号，生产环境必须限制权限 |
| `REPORT_DB_PASSWORD` | 本地开发值 | 报表只读账号密码 |
| `REPORT_DB_POOL_SIZE` | `1` | 报表查询连接上限 |
| `REPORT_STATEMENT_TIMEOUT_MS` | `120000` | 单条报表 SQL 超时毫秒数 |
| `REPORT_STORAGE_PATH` | `/data/reports` | Worker 与 API 共享的报表目录 |

## 故障与清理

- 单用户最多同时存在 3 个 `PENDING` 或 `RUNNING` 任务，避免重复点击形成无界队列。
- Worker 停止不会丢任务；重启后继续领取 `PENDING` 任务。
- 运行超过 30 分钟的任务视为中断并重新排队，累计尝试 3 次后标记 `FAILED`。
- 写入失败会删除目标文件和 `.part` 文件，并保存截断后的错误摘要。
- 成功文件保留 24 小时，过期任务标记为 `EXPIRED` 并清理文件。
- 任务状态和文件下载均校验申请人，用户不能读取他人的报表。

## 本地验收

2026-08-10 的容器验收先停止 `report-worker`，导出请求在约 62 ms 内返回并保持 `PENDING`，同时运营看板正常响应；恢复 Worker 后原任务自动转为 `SUCCEEDED`。下载文件包含 UTF-8 BOM，任务记录的文件大小与实际下载字节数一致，存储目录不存在残留 `.part` 文件。
