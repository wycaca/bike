# 本地开发环境

## Docker 安装位置

本机 Docker Desktop 使用 WSL 2 后端.

- 程序目录: `D:\Docker\Docker`.
- 安装包目录: `D:\Docker\installer`.
- Linux 镜像, 容器和数据卷: `D:\Docker\data\wsl\disk\docker_data.vhdx`.
- Docker Desktop 的少量配置, 锁文件和日志仍由程序放在 `%LOCALAPPDATA%\Docker`. 该目录不保存镜像数据磁盘.

不要在 C 盘重新创建 Docker WSL 数据目录. 修改 Docker Desktop 安装或恢复出厂设置后, 需要重新确认数据磁盘仍位于 D 盘.

## 国内镜像

项目在镜像名称中显式指定镜像站, 不依赖每台机器的 Docker 全局配置.

- PostgreSQL, TimescaleDB 和 PostGIS: `dockerproxy.net/timescale/timescaledb-ha:pg17.10-ts2.29.1`.
- Redis: `m.daocloud.io/docker.io/library/redis:7.4.2-alpine`.
- Kafka: `m.daocloud.io/docker.io/apache/kafka:4.2.0`.
- Maven 和 Java 运行时: `m.daocloud.io/docker.io/library/...`.

公共镜像站只用于开发环境. 生产环境应将固定版本镜像同步到自有阿里云 ACR 或 Harbor, 避免公共镜像站限流或不可用.

## 启动和停止

在项目根目录执行:

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

停止服务但保留数据库数据:

```powershell
docker compose down
```

只有明确需要清空本地数据库和 Redis 时才删除数据卷:

```powershell
docker compose down -v
```

## 基础检查

```powershell
curl.exe -fsS http://localhost:8080/actuator/health
docker compose exec -T redis redis-cli ping
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec -T db psql -U bike -d bike -c "SELECT extname, extversion FROM pg_extension WHERE extname IN ('timescaledb','postgis');"
```

健康接口应返回 `UP`, Redis 应返回 `PONG`, Kafka 应包含 `vehicle-telemetry`, 数据库应包含 `timescaledb` 和 `postgis`.

## 当前验证结果

2026-08-10 已在本机完成以下验证:

- 后端镜像中的 `mvn verify` 通过.
- PostgreSQL, TimescaleDB, PostGIS, Redis, Kafka 和后端容器启动成功.
- 未加载压测数据时, 固定 Mock 基线为 20 辆车辆, 北京和上海地图查询分别返回 10 个车辆点.
- 当前本机另加载 5,000 辆 `LT-` 压测车辆和 512,110 个相关轨迹点, 数据库总量为 5,020 辆和 512,247 个轨迹点.
- 地图和轨迹接口的 GCJ-02 输出已验证, 默认 WGS84 输出保持兼容.
- 车辆分页, 详情和历史轨迹接口返回成功.
- 北京道路级 Mock 轨迹返回 56 点, 约 2.24 km; 上海返回 61 点, 约 2.44 km; 最长采样间隔均为 10 秒.
- 模拟雅迪云事件通过 Kafka 消费后写入轨迹表, 并更新 PostgreSQL 最新状态和 Redis 缓存.
- 读接口 10, 30 和 60 并发压测均为 0 错误, 吞吐平台约 690 RPS; 详细环境和结果见 `performance-test.md`.
- 遥测写入 12,110 条最终全部落库, Kafka 单分区, 单消费者的持久化速度低于 1,211 RPS 的 HTTP 接收速度.
