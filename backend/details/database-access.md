# 数据库访问层

## 当前实现

- Spring Boot 4.1 使用 `mybatis-flex-spring-boot4-starter` 1.11.8.
- 保留 `spring-boot-starter-jdbc`, 用于 Spring Boot 4 的数据源自动配置.
- Repository 负责领域对象转换和业务语义, Mapper 负责 SQL 执行.
- 简单查询和更新使用 Mapper 注解.
- 动态筛选、PostGIS、TimescaleDB、JSONB 和 `RETURNING` 语句放在 `src/main/resources/mapper/` XML 中.
- Java 代码不再直接使用 `JdbcClient`, `JdbcTemplate` 或 `ResultSet`.
- 使用 `map-underscore-to-camel-case` 和构造参数名称完成 Java record 映射.

MyBatis-Flex 支持原生 MyBatis 注解和 XML. 当前 SQL 包含较多 PostgreSQL 专用能力, 因此不强制为每张表增加 Entity 和 `BaseMapper`. 只有出现大量标准单表 CRUD 时再评估代码生成, 避免为了框架形式增加无用模型.

## 报表 Worker

报表 Worker 保留两个独立的 MyBatis-Flex 会话工厂:

- 主会话使用读写连接池, 负责领取任务和更新任务状态.
- 报表会话使用只读连接池, 只加载收入聚合 Mapper.
- 报表连接池限制连接数和 `statement_timeout`, 避免聚合查询挤占在线业务连接.

## 事务和数据一致性

- 现有 `@Transactional` 边界保持不变.
- 车辆遥测仍先写 PostgreSQL, 再更新 Redis.
- 历史轨迹幂等约束、乱序事件保护和运维任务乐观锁继续由数据库约束和 SQL 条件保证.
- 报表任务继续使用 `FOR UPDATE SKIP LOCKED` 防止多个 Worker 重复领取.

## 验证

2026-08-11 执行 `mvn -B verify`, 35 个测试全部通过.

`MyBatisMapperTest` 使用真实 `FlexSqlSessionFactoryBean` 装载 5 个 XML Mapper, 检查 XML 语法、命名空间、结果类型和关键语句编号.

本次验证环境未启动 PostgreSQL、Redis 和 Kafka, 因此尚未重新执行 Compose 接口冒烟. Docker 环境恢复后应按 `local-development.md` 验证车辆、地图、轨迹、运维任务和报表接口.
