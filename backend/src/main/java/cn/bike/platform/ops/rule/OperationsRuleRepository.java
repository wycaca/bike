package cn.bike.platform.ops.rule;

import cn.bike.platform.ops.OperationsModels.TaskRule;
import cn.bike.platform.ops.OperationsModels.TaskRuleRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 自动运维规则仓储, 负责请求字段规范化和 Mapper 调用.
 * 更新结果保留影响行数, 由服务层统一转换为乐观锁冲突.
 */
@Repository
public class OperationsRuleRepository {

    private final OperationsRuleMapper mapper;

    public OperationsRuleRepository(OperationsRuleMapper mapper) {
        this.mapper = mapper;
    }

    /** 输入: 城市; 输出: 该城市全部自动任务规则。 */
    public List<TaskRule> findRules(String cityCode) {
        return mapper.findRules(cityCode);
    }

    /** 输入: 城市; 输出: 遥测评估使用的启用规则。 */
    public List<TaskRule> findEnabledRules(String cityCode) {
        return mapper.findEnabledRules(cityCode);
    }

    public Optional<TaskRule> findRule(String ruleId) {
        return Optional.ofNullable(mapper.findRule(ruleId));
    }

    /** 输入: 新规则和创建人; 输出: 无。 */
    public void insertRule(String ruleId, TaskRuleRequest request, String createdBy) {
        mapper.insertRule(ruleId, request.ruleName().trim(), request.cityCode(), request.orgId(),
                request.triggerType().name(), request.thresholdValue(), request.taskType().name(),
                request.priority().name(), request.titleTemplate().trim(),
                blankToNull(request.descriptionTemplate()), request.dueMinutes(), request.cooldownMinutes(),
                request.autoClose(), request.enabled(), createdBy);
    }

    /** 输入: 规则编号、版本和新配置; 输出: 乐观锁更新行数。 */
    public int updateRule(String ruleId, int version, TaskRuleRequest request) {
        return mapper.updateRule(ruleId, version, request.ruleName().trim(), request.cityCode(), request.orgId(),
                request.triggerType().name(), request.thresholdValue(), request.taskType().name(),
                request.priority().name(), request.titleTemplate().trim(),
                blankToNull(request.descriptionTemplate()), request.dueMinutes(), request.cooldownMinutes(),
                request.autoClose(), request.enabled());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
