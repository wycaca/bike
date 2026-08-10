# 运营管理模块

## 模块边界

本轮实现保持模块化单体结构：

- `security`: 登录、退出、当前用户、CSRF 和角色规则。
- `admin`: 用户、组织、Mock 初始管理员和审计日志。
- `geo`: 电子围栏、停车点、PostGIS 空间计算和实时违规。
- `dashboard`: 运营指标、趋势、区域分布和 CSV 报表。
- `report`: 骑行订单、日/月收入、车辆周转率、单位经济指标和 CSV 导出。

四个模块共享现有 PostgreSQL、Redis 和车辆最新状态，不新增独立服务或重复车辆投影。

## 权限矩阵

| 能力 | ADMIN | OPERATOR | AUDITOR |
| --- | --- | --- | --- |
| 车辆、地图、轨迹 | 读 | 读 | 读 |
| 看板、收入和 CSV 报表 | 读 | 读 | 读 |
| 围栏与停车点 | 读写 | 读写 | 无 |
| 用户与组织 | 读写 | 无 | 无 |
| 审计日志 | 读 | 无 | 读 |

前端路由守卫只负责隐藏不可用入口，最终权限由 `SecurityFilterChain` 判定。

## 登录与写请求

1. 页面先调用 `GET /api/v1/auth/csrf`，令牌写入同源 Cookie。
2. 登录表单提交到 `POST /api/v1/auth/login`，认证主体保存在 Redis Session。
3. Spring Security 登录成功后轮换 CSRF 令牌，前端必须再次请求 `/auth/csrf`。
4. 后续 POST、PUT、DELETE 请求使用 `X-XSRF-TOKEN` 请求头。
5. 写请求完成后，`AuditFilter` 记录操作者、资源、路径、状态码、来源 IP 和耗时。

## 空间判定

- 围栏使用 `geometry(Polygon, 4326)` 和 GIST 索引。
- 停车点使用 `geometry(Point, 4326)`，半径距离通过 `geography` 以米计算。
- `OUTSIDE_OPERATION`: 车辆不在任何启用的运营围栏内。
- `IN_NO_PARK`: 空闲车辆位于启用的禁停围栏内。
- `RIDING_IN_NO_RIDE`: 骑行车辆位于启用的禁骑围栏内。
- 停用设施保留历史记录，但不再进入总览、停车计数和违规判定。
- 设施所属组织必须启用，且组织城市为空或与设施城市一致。

## 看板与报表

看板按城市计算车辆总数、在线、骑行、离线、低电量、故障和维护数量；趋势按中国时区聚合遥测日数据，区域表按运营区比较在线和异常状态。

车辆状态报表使用 UTF-8 BOM、RFC 兼容字段转义和带日期的下载文件名，确保中文在 Excel 中直接打开时不乱码。

收入报表以骑行订单为事实数据，支持闭区间日期和日/月粒度。金额拆分为总流水、优惠、退款和净收入，运营效率使用有效订单、投放车辆日数、RpD、单均收入和单车日均收入。完整公式与行业参考见 [收入报表口径](revenue-reports.md)。

## 测试口径

- `AdminServiceTest`: 当前账号自停用保护、组织层级环检测。
- `GeoServiceTest`: 围栏闭合、退化多边形、WKT 和组织城市归属。
- `DashboardServiceTest`: CSV 中文、逗号、引号、BOM 和趋势参数。
- `RevenueReportServiceTest`: 车辆日数、跨月周期、RpD、退款率、单均与单车收入、CSV BOM。
- Docker 构建执行全部 Maven/Vitest 测试与 TypeScript 检查。
- 浏览器冒烟覆盖登录、看板、高德空间地图、组织更新、CSRF 和审计落库。
