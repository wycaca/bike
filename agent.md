# 项目 Agent 指南

## 文档作用

本文件是项目的统一入口文档. 新 Agent 开始工作前必须先阅读本文件, 再按任务读取对应模块的 `details/` 文档和代码.

本文件回答以下问题:

- 项目要解决什么问题.
- 当前已经完成什么.
- 当前架构和关键约束是什么.
- 如何启动, 测试和验证项目.
- 下一步应该做什么.
- 哪些需求仍待业务或厂商确认.
- 详细设计分别记录在哪里.

本文件只保留足够的项目全貌. 具体算法, 协议和交互细节以对应 `details/` 文档为准.

## 项目目标

构建面向中国大陆共享电动单车业务的管理和运维平台, 首个试点同时考虑北京和上海.

平台包含三个模块:

- `backend`: Java 后端, 提供车辆资产, 地图, 状态, 轨迹和设备事件接口.
- `frontend`: 桌面管理和运维端, 适合地图监控, 检索, 批量操作和复核.
- `app`: Android 移动管理和运维端, H5 使用 WebView 套壳, 适合扫码, 定位, 拍照和现场工单.

桌面端和移动端使用相同的后端 API, 权限, 状态枚举和业务口径. 两端只在布局, 信息密度和操作流程上不同.

当前不开发用户骑行端. 用户开锁, 订单, 支付, 营销等功能暂缓. 当前优先完成管理和运维能力.

业务和数据设计必须符合中国大陆使用习惯, 北京和上海的监管要求及适用国家标准. 尚未确认的具体标准和保存期限不能自行假设.

## 当前阶段

项目处于早期开发和本地验证阶段, 没有真实车辆数据.

当前优先级:

1. 完善并验证后端核心能力.
2. 构建桌面管理和运维端.
3. 补充告警, 工单, 调度和权限等运维能力.
4. 桌面端核心流程稳定后再开发移动端.
5. 取得雅迪正式资料后接入真实雅迪云 API.

不要把规划中的功能描述为已经实现.

## 当前进度

### 后端

已完成可运行的后端 MVP:

- Java 21 + Spring Boot 4.1 模块化单体.
- MyBatis-Flex 1.11.8 数据库访问层, 复杂 PostgreSQL SQL 使用 Mapper XML.
- PostgreSQL 17 + TimescaleDB + PostGIS.
- Redis 最新车辆状态缓存.
- Kafka 车辆遥测事件队列.
- Flyway 数据库迁移.
- 车辆档案和最新状态存储.
- TimescaleDB 历史轨迹超表.
- 当前地图视野查询和低缩放级别聚合.
- 雅迪云临时 Mock 协议, 请求校验, Kafka 生产和消费.
- 北京和上海模拟车辆及轨迹数据.
- Spring Security + Redis Session 登录、CSRF、角色权限和组织数据范围.
- 围栏、停车点、看板、收入报表和持久化异步导出 Worker.
- 运维任务、自动规则、批量建单、路线优化、作业凭证、验收和异常闭环.
- 后端 `report` 已按 `revenue/export` 归纳, `ops` 已按 `attachment/route/rule` 归纳, 工单核心和自动化保留在 `ops` 根包.
- Dockerfile 和根目录 Docker Compose 环境.

当前接口:

| 接口 | 状态 | 用途 |
| --- | --- | --- |
| `GET /api/v1/map/vehicles` | 已实现 | 按地图视野查询聚合点或车辆点. |
| `GET /api/v1/vehicles` | 已实现 | 分页检索车辆和最新状态. |
| `GET /api/v1/vehicles/{vehicleId}` | 已实现 | 查询车辆档案和最新状态. |
| `GET /api/v1/vehicles/{vehicleId}/trajectory` | 已实现 | 查询指定时间范围的历史轨迹. |
| `POST /api/v1/mock/yadea/events` | 已实现, 仅 Mock | 仅管理员可模拟雅迪云事件进入 Kafka. |
| 登录、用户、组织和审计接口 | 已实现 | Redis 会话、角色权限和组织数据范围. |
| 围栏、看板、收入和运维任务接口 | 已实现 | 管理、分析和现场运维闭环. |

模拟数据位于:

- `backend/src/main/resources/mock/vehicles.json`.
- `backend/src/main/resources/mock/yadea-cloud-events.json`.

当前有 200 辆模拟车辆, 北京和上海各 100 辆, 共 10,640 条固定上报事件. 数据覆盖正常, 骑行, 低电量, 故障, 离线, 维护和调度等状态. 每辆车都有 25 至 60 个由高德 v4 骑行路径规划生成的道路级轨迹点.

性能测试可另加载 5,000 辆 `LT-` 前缀车辆和每车 100 个基础轨迹点. 这些数据不进入固定 JSON, 可以通过 `backend/loadtest/cleanup.sql` 清理.

### 桌面前端

已完成可运行的桌面管理端 MVP:

- Vue 3 + TypeScript 严格模式 + Vite.
- Vue Router + Pinia + Axios + Element Plus.
- 车辆监控地图, 北京和上海切换, 状态筛选和 15 秒轮询.
- 车辆资产分页列表和关键字筛选.
- 车辆详情抽屉.
- 历史轨迹查询, 摘要和回放控制.
- 加载, 空数据, 接口错误和请求取消状态.
- Vitest 最小测试, Nginx 镜像和根目录 Compose 前端服务.

地图和轨迹接口当前请求 GCJ-02, 后端从 WGS84 统一转换. 直接运行 Vite 时, 本地高德 Web JS Key 保存在 Git 忽略的 `frontend/.env.local`; Docker Compose 构建使用根目录 `.env.local` 中的 Web JS Key 和安全密钥. 本地 Web Service Key 同样可以保存在根目录 `.env.local`, 两类 Key 不得混用或写入生成数据. 车辆分布和历史轨迹均已接入高德 JS API 2.0. 未配置 Key 或 SDK 加载失败时保留坐标预览. 地图只在城市切换时重置中心, 用户拖动和缩放不会触发回到城市中心. 已使用本机 Edge 验证真实高德组件、中文车辆信息和道路轨迹折线渲染.

登录、Redis 会话、CSRF、角色路由、组织数据范围和用户配置页面已经联调. 数据范围支持 `ALL`、`ORG_AND_CHILDREN`、`ORG_ONLY`, 详细规则见 `backend/details/data-permissions.md`.

### 移动端

移动管理和运维端已完成可运行 MVP:

- H5: Vue 3 + TypeScript + Vite + Vant.
- Android: Kotlin WebView 壳.
- 地图: 高德地图 JS API 2.0.

移动端是管理和运维端, 不是用户骑行 App. 已实现角色工作台、任务池、任务执行、路线、车辆地图与查询、运营收益指标以及定位、扫码、拍照 Bridge; Android Kotlin WebView 壳已建立. 数据权限由后端统一执行.

Android 原生层已增加 H5 首页连接错误页, 区分断网、服务不可达、HTTP 异常和证书错误, 支持用户重新连接, 不再显示 WebView 默认错误页面. 当前开发机器没有 Android SDK 或虚拟机, Android 单元测试与实机交互需要在具备 Android 环境的机器补做.

## 总体架构

### 当前部署形态

当前使用模块化单体和 Docker Compose, 不使用微服务, 注册中心, 配置中心, 服务网格或 Kubernetes.

本地 Compose 包含:

- `backend`: Spring Boot API 和 Kafka consumer.
- `frontend`: Nginx 静态站点和后端同源代理.
- `report-worker`: 独立异步报表进程.
- `mobile-web`: 移动端 H5 Nginx 服务.
- `db`、`redis`、`kafka`: 本地基础设施.

当前 Compose 只用于开发和验证, 不是生产高可用部署.

### 数据流

车辆遥测事件当前按以下顺序处理:

1. Mock 雅迪接口接收 JSON 并执行 Bean Validation.
2. 合法事件写入 Kafka `vehicle-telemetry`.
3. consumer 幂等写入 TimescaleDB 历史轨迹.
4. consumer 更新 PostgreSQL 最新状态投影.
5. 只有事件时间不早于现有状态时才更新 Redis.

数据库先落盘, Redis 后更新. Redis 不是唯一持久化来源, 丢失后必须能从 PostgreSQL 恢复.

### 数据职责

- PostgreSQL: 车辆档案, 最新状态投影和可恢复业务数据.
- TimescaleDB: 原始历史轨迹时序数据.
- PostGIS: 空间索引, 地图视野查询和聚合.
- Redis: 最新状态, 后续登录态, 验证码, 幂等键和短期状态.
- Kafka: 车辆上报缓冲和异步消费.
- 对象存储: 后续保存图片, 报表, 工单附件和冷归档.

### 无状态约束

- 后端实例不保存本地业务状态.
- 登录态和分布式状态放 Redis.
- 文件不写入容器本地磁盘.
- 多个后端实例可以直接挂在负载均衡后.
- 后台消费通过 Kafka consumer group 保证实例协作.

### 地图和坐标

- 设备原始位置按 WGS84 入库.
- PostGIS 使用 SRID 4326.
- 原始 WGS84 数据不能被 GCJ-02 覆盖.
- 高德地图使用 GCJ-02 展示.
- 坐标转换由后端统一完成.
- 地图和轨迹接口支持目标坐标系参数, 默认 WGS84, 桌面端使用 GCJ-02.
- 地图只查询当前视野, 不允许客户端一次下载全城车辆.
- 低缩放级别返回聚合点, 高缩放级别返回单车点.

### 轨迹约束

车辆历史轨迹是核心数据.

- 历史点按车辆编号和上报时间幂等写入.
- 乱序点可以进入历史表, 但不能回退最新状态.
- Redis 不保存历史轨迹.
- 单次轨迹查询最长 31 天.
- 单次最多返回 10000 个点, 达到上限必须标记截断.
- 当前未启用自动删除, 压缩和冷归档策略.

## 本地部署

### Docker 环境

已在 Windows + WSL 2 本机完成验证.

- Docker Desktop 程序: `F:\devTools\Docker`.
- Docker 镜像, 容器和卷: `D:\other\docker_data\disk\docker_data.vhdx`.
- Docker Desktop 管理数据: `D:\other\docker_data\main\ext4.vhdx`.
- Docker CLI 已加入系统 PATH.
- `%LOCALAPPDATA%\Docker` 只保留少量配置, 锁文件和日志.

不要把 Docker 数据盘迁回 C 盘. 公共镜像站只用于开发环境, 生产环境应同步固定版本镜像到自有阿里云 ACR 或 Harbor.

### 启动

在项目根目录执行:

```powershell
docker compose --env-file .env.local up -d --build
docker compose ps
```

查看后端日志:

```powershell
docker compose logs -f backend
```

停止并保留数据:

```powershell
docker compose down
```

只有明确需要清空本地数据库和 Redis 时才执行:

```powershell
docker compose down -v
```

本地数据库密码 `bike_dev_password` 只用于开发环境, 不能用于生产.

## 测试和验证

### Java 测试

后端构建镜像时会执行 `mvn -B verify`. 也可以在 `backend` 目录使用 Java 21 和 Maven 3.9 执行:

```powershell
mvn -B verify
```

当前后端有 45 个自动化测试, 覆盖核心服务行为、数据范围、HTTP 错误契约和 7 个 MyBatis-Flex XML Mapper 装载. 数据库, Kafka 和 Redis 链路通过 Docker 环境做集成验证. 修改核心数据流时应补充最小必要测试.

### 后端性能回归

`backend/loadtest` 提供可重复的数据准备, 清理和零依赖压测脚本. 先执行 `node backend/loadtest/load-test.mjs --self-test`, 再按 `backend/details/performance-test.md` 运行读接口或遥测写入模式. 默认门槛为错误率不超过 0.1%, P95 不超过 200 ms, 吞吐量不低于 500 RPS.

### 前端测试

```powershell
cd frontend
npm test
npm run build
```

Docker Compose 前端地址为 `http://localhost:8081`. 前端 Nginx 将 `/api` 和 `/actuator` 同源代理到后端.

当前内置浏览器自动化可能受企业 Windows 策略限制. 命令测试出现 `spawn EPERM` 时按已确认的 `sandbox = "unelevated"` 配置或使用 Docker 构建, 不修改企业安全策略.

### 健康检查

```powershell
curl.exe -fsS http://localhost:8080/actuator/health
docker compose exec -T redis redis-cli ping
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec -T db psql -U bike -d bike -c "SELECT extname, extversion FROM pg_extension WHERE extname IN ('timescaledb', 'postgis');"
```

预期结果:

- 后端状态为 `UP`.
- Redis 返回 `PONG`.
- Kafka 包含 `vehicle-telemetry`.
- 数据库包含 `timescaledb` 和 `postgis`.

### 核心接口冒烟检查

```powershell
curl.exe -fsS "http://localhost:8080/api/v1/vehicles?page=1&pageSize=20"
curl.exe -fsS "http://localhost:8080/api/v1/map/vehicles?minLongitude=116.2&minLatitude=39.8&maxLongitude=116.6&maxLatitude=40.1&zoom=16&coordinateSystem=GCJ02"
curl.exe -fsS "http://localhost:8080/api/v1/map/vehicles?minLongitude=121.3&minLatitude=31.1&maxLongitude=121.7&maxLatitude=31.4&zoom=16&coordinateSystem=GCJ02"
curl.exe -fsS http://localhost:8080/api/v1/vehicles/YD-BJ-000001
curl.exe -fsS "http://localhost:8080/api/v1/vehicles/YD-BJ-000001/trajectory?startTime=2026-08-10T00:00:00Z&endTime=2026-08-10T01:00:00Z"
```

2026-08-10 已验证:

- Docker 镜像内 `mvn verify` 通过.
- 7 个 Compose 服务正常运行, 前端地址为 `http://localhost:8081`.
- PostgreSQL 17.10, TimescaleDB 2.29.1, PostGIS 3.6.4, Redis 7.4.2 和 Java 21.0.11 运行正常.
- 未加载压测数据时, 车辆分页返回北京和上海共 200 辆固定 Mock 车辆, 两个城市地图查询分别返回 100 个 GCJ-02 车辆点.
- 固定 Mock 数据已完整载入 10,640 个轨迹点, 每辆车 25 至 60 点.
- 车辆详情和历史轨迹返回成功, 坐标转换结果与 WGS84 原始值存在预期偏移.
- 前端 `npm test` 和 `npm run build` 通过.
- 前端镜像构建成功, 健康检查, 车辆分页, 北京地图点位, 车辆详情和历史轨迹均通过 Nginx 同源代理验证.
- 页面截图已验证中文车辆信息、高德组件和道路轨迹折线; 完整交互回归仍可继续补充.
- Mock 事件进入 Kafka 后成功写入轨迹表, PostgreSQL 最新状态和 Redis 缓存.
- 读接口压测在 10, 30 和 60 并发下均为 0 错误, 吞吐平台约 690 RPS, 60 并发 P95 为 100.80 ms.
- 遥测接口 10 并发接收 1,211 RPS, 12,110 条最终全部落库; 当前单分区, 单消费者的持久化速度低于 HTTP 接收速度.

2026-08-11 已验证:

- 数据库访问层已从 Spring JDBC 重构为 MyBatis-Flex 1.11.8, Java 源码不再直接使用 `JdbcClient`, `JdbcTemplate` 或 `ResultSet`.
- `mvn -B verify` 通过 38 个测试, Mapper 装载测试使用真实 `FlexSqlSessionFactoryBean` 解析 7 个 XML Mapper.
- 前端 11 个 Vitest 和移动端 6 个 Vitest 通过, 两端生产构建成功.
- Compose 全部服务启动成功, Flyway V7 已应用, 北京和上海车辆各归属 100 辆.
- 北京 `ORG_ONLY` 运维账号只能看到北京车辆、地图、看板、收入、围栏、任务和规则数据, 上海数据为 0, 跨组织车辆详情返回 404.
- 北京 `ORG_AND_CHILDREN` 审计账号只返回 `ORG-BJ` 日志, 临时测试账号已清理.

2026-08-13 已同步:

- 合并远端 Android 局域网地址、移动端口、车辆地图和运营收益指标提交, 本地数据权限提交完整保留.
- 增加 Android H5 首页连接错误分类、原生错误界面和重新连接逻辑, 并补充 4 个 JVM 单元测试用例.
- 当前机器未配置 Android SDK 和虚拟机, Docker 未缓存 Android 构建镜像且镜像拉取未完成, 因此 Android 单元测试和实机验证尚未执行.
- 后端统一补齐 MVC 参数、权限、上传、方法、媒体类型、接口不存在和未知异常响应; 创建资源返回 `201 + Location`, 队列接收返回 `202`.
- 作业凭证的 Spring multipart 与业务上限统一为单文件 8 MB, 请求 10 MB; Docker 镜像内 45 个测试全部通过.

## 未来计划

按当前优先级推进, 不提前拆分微服务:

1. 补充地图点选, 城市切换, 详情抽屉和轨迹播放的自动化交互回归.
2. 申请生产高德 Key 和安全密钥, 配置域名白名单及同源安全代理.
3. 取得雅迪正式 API 文档和测试账号后替换 Mock 接入边界.
4. 根据试点反馈补充告警通知、排班、库存和调度算法.
5. 确认轨迹保留策略后实现冷热分层和归档任务.

远程开锁, 关锁, 寻车, 断电和 OTA 在雅迪正式协议, 权限, 审计, 幂等和设备回执均确认前不得实现为真实控制功能.

## 待确认事项

以下事项不能由 Agent 自行决定:

- 原始轨迹保留多少天.
- 简化轨迹是否永久保存.
- 北京和上海监管要求的具体保存期限.
- 冷轨迹是否需要恢复到在线数据库查询.
- 雅迪正式 API 地址, 鉴权, 字段, QPS, 错误码和设备编号关系.
- 远程车辆控制的业务权限和审批流程.
- 高德地图生产 Key, 域名白名单和安全代理配置.
- 移动端是否需要弱网离线队列和自动重试.
- 等保, 审计, 灾备和跨城市资源隔离要求.

## Details 文档说明

每个模块使用独立的 `details/` 目录. 一个文件只描述一个主要主题, 不再创建单一 `details.md`.

### 后端文档

- `backend/details/architecture.md`: 后端总体架构, 模块, 数据职责和部署演进.
- `backend/details/database-access.md`: MyBatis-Flex Mapper、复杂 SQL、事务和报表双数据源.
- `backend/details/data-permissions.md`: 角色默认数据范围、组织过滤资源和越权处理.
- `backend/details/http-error-contract.md`: HTTP 状态、统一错误 JSON、上传限制和异常映射.
- `backend/details/trajectory-and-map.md`: 轨迹存储, 坐标, 查询限制和地图聚合.
- `backend/details/yadea-cloud-api.md`: 雅迪 Mock 接入, 正式联调资料和远程控制约束.
- `backend/details/cost-control.md`: 后端实现中的成本控制和扩容触发条件.
- `backend/details/local-development.md`: Docker 安装位置, 国内镜像, 启停和基础检查.
- `backend/details/performance-test.md`: 压测数据, 读写场景, 回归阈值, 本机基线和清理方式.

### 桌面前端文档

- `frontend/details/architecture.md`: 桌面端技术栈, 工程结构, 功能范围, API 和部署.
- `frontend/details/desktop-interaction.md`: 桌面端布局, 地图入口, 列表和运维交互.
- `frontend/details/map-and-trajectory.md`: 高德地图请求, 点位渲染和轨迹回放.
- `frontend/details/local-development.md`: 前端安装, 开发启动, 构建, Compose 和联调检查.

### 移动端文档

- `app/details/architecture.md`: H5, Android WebView, JSBridge, 安全和发布方式.
- `app/details/mobile-interaction.md`: 移动端地图, 检索, 工单和现场操作.

### 成本文档

- `cost.md`: 各车辆规模阶段的云资源规格, 月成本估算和待确认成本项.

新增详细设计时:

- 文件名使用简短英文 kebab-case.
- 正文使用中文.
- 当前实现, 已确认方案, 未来计划和待确认事项必须分开.
- 新主题在对应模块的 `details/` 下新增文件.
- 调度算法等复杂主题单独建文档, 不扩大总体架构文档.
- 机器规格和价格统一更新根目录 `cost.md`.
- 实现和设计发生变化时, 同步更新本文件的进度和对应详情文档.

## Agent 工作规则

### 工作原则

- 修改前阅读本文件, 相关 `details/` 文档, 代码和调用关系.
- 按最小改动执行, 只解决当前已确认需求.
- 优先复用现有实现, 标准库和平台能力.
- 不新增无必要依赖, 不做只有一个实现的抽象层.
- 不清楚需求, 上下文模糊或存在多种重要方案时, 先与用户确认.
- 用户要求先讨论方案时, 不直接修改代码.
- 不把模拟协议当作雅迪正式协议.
- 不把本地 Compose 当作生产高可用方案.
- 修改完成后说明改动文件, 原因和实际验证结果.

### 代码风格

- 后端使用 Java 21 + Spring Boot 4.1.
- 后端数据库访问使用 MyBatis-Flex, 不在 Repository 中直接编写 JDBC 结果集映射.
- 桌面前端使用 Vue 3 + TypeScript + Element Plus.
- 移动 H5 使用 Vue 3 + TypeScript + Vant.
- Android 只做 Kotlin WebView 壳和必要原生能力.
- 桌面端和移动端共享业务契约, 不复制业务规则.
- 结构化数据使用解析器和类型模型, 不使用脆弱的字符串拼接.

### 注释要求

- 所有新增或修改的注释和 docstring 保持中文, 技术专有名词可以保留原文.
- 标点统一使用英文标点.
- 注释解释设计原因, 不只复述代码行为.
- 复杂逻辑必须有解释性注释.
- 函数级 docstring 说明职责, 关键输入输出, 重要约束和异常情况.
- 长代码段使用清晰注释分隔处理阶段.
- 注释密度与当前文件保持一致.
- 逻辑不确定时, 在注释中明确假设和待验证点.

### 文档要求

- 文档简单易懂, 先讲结论, 再讲细节.
- 不确定的业务规则写入待确认, 不写死.
- 价格估算必须标明日期和估算口径.
- 代码, 配置, 测试结果和文档必须保持一致.

### Git 要求

- commit 消息保持中文, 不使用 `feat`, `fix`, `docs` 等英文类型前缀, 技术专有名词可以保留原文.
- commit 消息说明实际变更内容, 不写空泛描述.
- 不提交密钥, 正式密码, 证书或真实车辆敏感数据.
- 不回退或覆盖与当前任务无关的用户修改.

## 完成任务前检查

每个 Agent 在结束代码任务前至少确认:

- 改动符合当前阶段, 没有提前实现未确认功能.
- 对应测试或最小可运行检查已经执行.
- Docker 或接口行为变化时已执行实际运行验证.
- 架构, 接口, 部署或进度变化时已更新对应文档和本文件.
- 没有提交开发密码之外的新凭证或敏感数据.
- 最终说明中明确列出未完成项和未执行的测试.
