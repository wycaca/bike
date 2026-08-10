package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsModels.TaskPriority;
import cn.bike.platform.ops.OperationsModels.TaskRule;
import cn.bike.platform.ops.OperationsModels.TaskRuleRequest;
import cn.bike.platform.ops.OperationsModels.TaskType;
import cn.bike.platform.ops.OperationsModels.TriggerType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class OperationsRuleRepository {

    private static final String RULE_SELECT = """
            SELECT rule.*, org.org_name FROM operations_task_rule rule
            JOIN organization org ON org.org_id = rule.org_id
            """;

    private final JdbcClient jdbcClient;

    public OperationsRuleRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 输入: 城市; 输出: 该城市全部自动任务规则。 */
    public List<TaskRule> findRules(String cityCode) {
        return jdbcClient.sql(RULE_SELECT + " WHERE rule.city_code = :cityCode ORDER BY rule.created_at")
                .param("cityCode", cityCode).query(this::mapRule).list();
    }

    /** 输入: 城市; 输出: 遥测评估使用的启用规则。 */
    public List<TaskRule> findEnabledRules(String cityCode) {
        return jdbcClient.sql(RULE_SELECT + """
                        WHERE rule.city_code = :cityCode AND rule.enabled = true
                        ORDER BY rule.created_at
                        """).param("cityCode", cityCode).query(this::mapRule).list();
    }

    public Optional<TaskRule> findRule(String ruleId) {
        return jdbcClient.sql(RULE_SELECT + " WHERE rule.rule_id = :ruleId")
                .param("ruleId", ruleId).query(this::mapRule).optional();
    }

    /** 输入: 新规则和创建人; 输出: 无。 */
    public void insertRule(String ruleId, TaskRuleRequest request, String createdBy) {
        jdbcClient.sql("""
                        INSERT INTO operations_task_rule (
                            rule_id, rule_name, city_code, org_id, trigger_type, threshold_value,
                            task_type, priority, title_template, description_template,
                            due_minutes, cooldown_minutes, auto_close, enabled, created_by
                        ) VALUES (
                            :ruleId, :ruleName, :cityCode, :orgId, :triggerType, :thresholdValue,
                            :taskType, :priority, :titleTemplate, :descriptionTemplate,
                            :dueMinutes, :cooldownMinutes, :autoClose, :enabled, :createdBy
                        )
                        """).param("ruleId", ruleId).param("ruleName", request.ruleName().trim())
                .param("cityCode", request.cityCode()).param("orgId", request.orgId())
                .param("triggerType", request.triggerType().name()).param("thresholdValue", request.thresholdValue())
                .param("taskType", request.taskType().name()).param("priority", request.priority().name())
                .param("titleTemplate", request.titleTemplate().trim())
                .param("descriptionTemplate", blankToNull(request.descriptionTemplate()))
                .param("dueMinutes", request.dueMinutes()).param("cooldownMinutes", request.cooldownMinutes())
                .param("autoClose", request.autoClose()).param("enabled", request.enabled())
                .param("createdBy", createdBy).update();
    }

    /** 输入: 规则编号、版本和新配置; 输出: 乐观锁更新行数。 */
    public int updateRule(String ruleId, int version, TaskRuleRequest request) {
        return jdbcClient.sql("""
                        UPDATE operations_task_rule SET rule_name = :ruleName, city_code = :cityCode,
                            org_id = :orgId, trigger_type = :triggerType, threshold_value = :thresholdValue,
                            task_type = :taskType, priority = :priority, title_template = :titleTemplate,
                            description_template = :descriptionTemplate, due_minutes = :dueMinutes,
                            cooldown_minutes = :cooldownMinutes, auto_close = :autoClose,
                            enabled = :enabled, version = version + 1, updated_at = now()
                        WHERE rule_id = :ruleId AND version = :version
                        """).param("ruleId", ruleId).param("version", version)
                .param("ruleName", request.ruleName().trim()).param("cityCode", request.cityCode())
                .param("orgId", request.orgId()).param("triggerType", request.triggerType().name())
                .param("thresholdValue", request.thresholdValue()).param("taskType", request.taskType().name())
                .param("priority", request.priority().name()).param("titleTemplate", request.titleTemplate().trim())
                .param("descriptionTemplate", blankToNull(request.descriptionTemplate()))
                .param("dueMinutes", request.dueMinutes()).param("cooldownMinutes", request.cooldownMinutes())
                .param("autoClose", request.autoClose()).param("enabled", request.enabled()).update();
    }

    private TaskRule mapRule(ResultSet rs, int rowNum) throws SQLException {
        return new TaskRule(rs.getString("rule_id"), rs.getString("rule_name"), rs.getString("city_code"),
                rs.getString("org_id"), rs.getString("org_name"),
                TriggerType.valueOf(rs.getString("trigger_type")), nullableInteger(rs, "threshold_value"),
                TaskType.valueOf(rs.getString("task_type")), TaskPriority.valueOf(rs.getString("priority")),
                rs.getString("title_template"), rs.getString("description_template"),
                rs.getInt("due_minutes"), rs.getInt("cooldown_minutes"), rs.getBoolean("auto_close"),
                rs.getBoolean("enabled"), rs.getInt("version"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
