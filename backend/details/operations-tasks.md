# 运维任务与竞品调研

## 调研结论

调研日期为 2026-08-11，优先采用厂商官方公开资料：

- [Joyride Operator App](https://joyride.city/operator-app/) 将换电、车辆再平衡、维修工单、人员指派、通知、角色权限、用户操作日志和配件人工费用放在同一套现场工具中。
- [Joyride Work Orders](https://joyride.city/blog/work-orders-operator-app-joyrides-service-tool-combo-for-smarter-fleet-maintenance/) 展示了优先级、截止时间、开始任务、处理说明、完工照片、维修时间线、配件库存和人工成本等典型工单流程。
- [MotionTools Fleet Servicing](https://www.wundermobility.com/marketplace/m-tools-fleet-servicing) 支持新建并监控计划中或执行中的任务、自动派单、人员实时位置、车辆专属检查流程、多站点路线、CSV 批量导入、检查单和照片上传。
- [Vulog Micromobility](https://www.vulog.com/micromobility/) 将车队维护、换电、服务人员调度、实时车辆监控、诊断和损坏报告列为通用能力。

由上述资料可归纳出业内常见能力：任务生成与分派、现场移动执行、优先级和 SLA、车辆及人员位置、检查单和完工凭证、配件人工成本、完整历史记录、批量任务与路线优化。

公开资料明确展示了管理员指派和自动派单，但没有找到可确认的“运维人员公开抢单”说明。本项目因此采用业务方确认的混合模式：管理员可以直接派单，未指派任务进入公共任务池，由符合城市范围的运维人员抢单。

## 当前范围

### 任务分类

| 类型 | 用途 | 开始后的车辆状态 |
| --- | --- | --- |
| `BATTERY_SWAP` | 低电量或异常电池更换 | `MAINTENANCE` |
| `REBALANCE` | 热点、冷点之间车辆调度 | `DISPATCHING` |
| `REPAIR` | 机械、车锁或控制器维修 | `MAINTENANCE` |
| `INSPECTION` | 例行巡检和合规检查 | `MAINTENANCE` |
| `RETRIEVAL` | 禁停、故障或失联车辆回收 | `DISPATCHING` |
| `CLEANING` | 车身、二维码和标识清洁 | `MAINTENANCE` |

优先级为 `LOW`、`NORMAL`、`HIGH`、`URGENT`。任务列表先按优先级，再按截止时间和创建时间排序。

### 状态机

```text
OPEN --抢单/指派--> CLAIMED --开始--> IN_PROGRESS --完成--> COMPLETED
  ^                    |
  |------ 释放 --------|

OPEN / CLAIMED / IN_PROGRESS --管理员取消--> CANCELLED
```

状态流转规则：

1. 抢单使用单条带状态条件的 `UPDATE`，只有首个请求能把 `OPEN` 改成 `CLAIMED`。
2. 指派、释放、开始、完成和取消同时校验 `version`，防止管理员改派与现场操作互相覆盖。
3. 数据库部分唯一索引保证同一车辆只有一个 `OPEN`、`CLAIMED` 或 `IN_PROGRESS` 任务。
4. 运维人员只能释放、开始和完成自己领取的任务；管理员可以看到领取人并执行改派或取消。
5. 每次成功流转都在 `operations_task_event` 追加事件，不修改历史事件。

## 接口

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/v1/ops/tasks` | 按城市、状态、类型、范围和关键字分页查询任务。 |
| `GET /api/v1/ops/tasks/summary` | 查询队列、超时、今日完成和我的任务汇总。 |
| `GET /api/v1/ops/tasks/assignees` | 查询任务城市内可指派的运维人员。 |
| `GET /api/v1/ops/tasks/{taskId}` | 查询任务及完整事件时间线。 |
| `POST /api/v1/ops/tasks` | 创建任务，可由管理员直接指派。 |
| `POST /api/v1/ops/tasks/{taskId}/claim` | 运维人员抢单。 |
| `PUT /api/v1/ops/tasks/{taskId}/assignment` | 管理员指派或改派。 |
| `POST /api/v1/ops/tasks/{taskId}/release` | 领取人释放任务。 |
| `POST /api/v1/ops/tasks/{taskId}/start` | 领取人开始任务。 |
| `POST /api/v1/ops/tasks/{taskId}/complete` | 领取人填写结果并完成任务。 |
| `POST /api/v1/ops/tasks/{taskId}/cancel` | 管理员填写原因并取消任务。 |

## 后续增强

按竞品公开能力和落地依赖排序：

1. 完工照片、损坏照片和结构化检查单，需要接入对象存储及移动端相机能力。
2. 配件领用、库存扣减、人工工时和单任务成本，用于维修成本和车辆全生命周期分析。
3. 任务通知、即将超时提醒、SLA 和班次交接，减少任务遗漏。
4. 批量创建、CSV 导入、多车辆任务和多站点路线，提升大规模换电和调度效率。
5. 结合运维人员实时位置、技能和任务负载自动派单；调度任务进一步接入需求预测。
6. 增加首次响应时长、平均完成时长、一次修复率、超时率、人均完成量和车辆停运时长报表。

照片、定位和成本都属于敏感或高增长数据，生产环境应保存到对象存储或专用事实表，不写入任务主表，也不经过报表 API 进程生成大文件。
