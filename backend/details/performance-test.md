# 后端性能测试

## 目标和边界

当前压测用于发现早期性能回退, 验证单实例车辆查询和遥测链路是否满足试点阶段开发需要. 它不是生产容量承诺, 原因包括:

- 请求通过本机回环网络, 没有公网延迟, TLS, 网关, 登录和审计开销.
- 数据是固定生成的北京和上海样本, 不代表真实车辆热点分布.
- 后端, PostgreSQL, Redis 和 Kafka 都是单机 Docker 容器.
- 遥测接口返回 `QUEUED` 只表示进入 Kafka, 必须继续检查 consumer lag 和最终落库数.

## 测试文件

- `backend/loadtest/seed.sql`: 生成可重复的 `LT-` 前缀车辆和轨迹数据.
- `backend/loadtest/cleanup.sql`: 只清理 PostgreSQL 中的 `LT-` 前缀数据.
- `backend/loadtest/load-test.mjs`: 无第三方依赖的读接口和遥测写入压测用例.

固定道路级 Mock 数据继续用于界面和功能测试. 压测数据通过 SQL 生成, 不把数百 MB JSON 提交到仓库, 也不经过生产启动初始化器.

## 数据准备

默认生成 5,000 辆车辆和每车 100 个轨迹点, 即 500,000 个轨迹点. 参数可以调整:

```powershell
docker compose cp backend/loadtest/seed.sql db:/tmp/bike-loadtest-seed.sql
docker compose exec -T db psql -U bike -d bike -v vehicle_count=5000 -v points_per_vehicle=100 -f /tmp/bike-loadtest-seed.sql
```

脚本会先删除已有 `LT-` 前缀数据, 因此重复执行后数据规模保持一致. 它不会删除 200 辆固定 Mock 车及其道路级轨迹.

SQL 数据只用于验证查询规模和索引, 不模拟真实道路. 道路形状仍以高德生成的固定 Mock 轨迹为准.

## 读接口压测

先执行脚本自检:

```powershell
node backend/loadtest/load-test.mjs --self-test
```

默认混合比例:

| 场景 | 比例 |
| --- | ---: |
| 车辆分页 | 35% |
| 地图聚合 | 25% |
| 地图车辆点 | 20% |
| 车辆详情 | 10% |
| 历史轨迹 | 10% |

执行示例:

```powershell
$env:MODE="read"
$env:CONCURRENCY="30"
$env:DURATION_SECONDS="30"
$env:WARMUP_SECONDS="5"
$env:VEHICLE_COUNT="5000"
node backend/loadtest/load-test.mjs
```

默认回归阈值:

- 错误率不超过 0.1%.
- 整体 P95 不超过 200 ms.
- 吞吐量不低于 500 RPS.

可通过 `MAX_ERROR_RATE`, `MAX_P95_MS` 和 `MIN_RPS` 调整阈值. 测试未通过时进程返回非零退出码. 不同硬件或 CI 环境必须先建立自己的基线, 不应直接降低阈值掩盖回退.

## 遥测写入压测

写入模式只调用 Mock 雅迪接口, 使用已有 `LT-` 车辆并生成唯一事件编号:

```powershell
$env:MODE="ingest"
$env:RUN_ID="local-001"
$env:CONCURRENCY="10"
$env:DURATION_SECONDS="10"
$env:WARMUP_SECONDS="0"
$env:VEHICLE_COUNT="5000"
node backend/loadtest/load-test.mjs
```

HTTP 测试完成后必须检查 Kafka lag:

```powershell
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group bike-telemetry-worker --describe
```

`LAG` 归零后, 使用同一个 `RUN_ID` 验证最终落库数量:

```powershell
docker compose exec -T db psql -U bike -d bike -c "SELECT count(*) FROM vehicle_position WHERE raw_payload #>> '{rawData,loadTestRunId}' = 'local-001';"
```

只有 HTTP 成功数, Kafka 最终消费数和 PostgreSQL 落库数一致, 才能认为端到端写入成功.

## 本机基线

测试日期: 2026-08-10.

以下结果是在固定 Mock 数据扩容前执行的历史基准, 当时包含 20 辆固定 Mock 车辆和 137 个固定轨迹点. 扩容到 200 辆、10,640 个固定轨迹点后需要重新执行压测, 不直接沿用旧结果作为当前容量结论.

环境:

- CPU: Intel Core Ultra 7 155H, 22 个逻辑处理器.
- 主机内存: 31.5 GB.
- Docker: 22 CPU, 15.4 GB 内存.
- 后端: 单实例, Java 21, Spring Boot 4.1.0, Hikari 最大连接数 10.
- 数据库: PostgreSQL 17.10, TimescaleDB 2.29.1, PostGIS 3.6.4.
- 数据量: 5,020 辆车辆, 500,137 个轨迹点, 数据库 193 MB.

读接口结果:

| 并发 | 持续时间 | 请求数 | 错误率 | RPS | P50 | P95 | P99 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 20 秒 | 12,939 | 0% | 646.95 | 11.56 ms | 25.68 ms | 37.54 ms |
| 30 | 30 秒 | 20,840 | 0% | 694.67 | 39.18 ms | 55.85 ms | 310.85 ms |
| 60 | 30 秒 | 20,681 | 0% | 689.37 | 83.63 ms | 100.80 ms | 135.44 ms |

30 至 60 并发期间的资源快照:

- 后端约 120% CPU, 501 至 504 MiB 内存.
- PostgreSQL 约 239% 至 241% CPU, 410 至 411 MiB 内存.
- Redis 约 1% CPU, 13 MiB 内存.
- Kafka 约 2% CPU, 988 MiB 内存.

吞吐量在 30 并发后稳定在约 690 RPS, 继续提高并发主要增加排队延迟. 当前瓶颈更接近数据库查询和 10 个连接的连接池, 不是内存. 在没有生产查询比例和云服务器基线前, 暂不调整连接池.

遥测写入结果:

- 10 并发持续 10 秒, HTTP 接收 12,110 条, 0 错误, 1,211 RPS, P95 为 11.38 ms.
- 请求结束后 Kafka lag 为 7,347, 约 23 秒后归零.
- PostgreSQL 最终落库 12,110 条, 与 HTTP 成功数一致.
- 5,000 辆压测车辆的 Redis 最新状态均已更新.

结论: HTTP 接收速度明显高于当前单分区, 单消费者的持久化速度. 试点阶段若 5,000 辆车每 30 秒上报一次, 平均约 167 条/秒, 当前链路有余量. 10 万辆每 30 秒上报约 3,333 条/秒, 当前链路不足; 到达该阶段前需要增加 Kafka 分区和消费者实例, 并根据实测评估批量写入, 不能只扩展 HTTP 实例.

## 清理

清理 PostgreSQL:

```powershell
docker compose cp backend/loadtest/cleanup.sql db:/tmp/bike-loadtest-cleanup.sql
docker compose exec -T db psql -U bike -d bike -f /tmp/bike-loadtest-cleanup.sql
```

遥测写入模式还会生成 Redis 最新状态, 使用限定前缀的 Lua 脚本清理:

```powershell
docker compose exec -T redis redis-cli EVAL "local c='0'; repeat local r=redis.call('SCAN',c,'MATCH','vehicle:latest:LT-*','COUNT',1000); c=r[1]; for _,k in ipairs(r[2]) do redis.call('DEL',k) end until c=='0'; return 1" 0
```

不要使用 `FLUSHALL`, 避免删除固定 Mock 车辆和其他本地状态.
