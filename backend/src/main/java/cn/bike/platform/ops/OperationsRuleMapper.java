package cn.bike.platform.ops;

import cn.bike.platform.ops.OperationsModels.TaskRule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 自动运维规则持久化 Mapper.
 * 规则更新使用 version 条件完成乐观锁校验, 防止管理端并发编辑相互覆盖.
 */
@Mapper
public interface OperationsRuleMapper {

    String RULE_SELECT = """
            SELECT rule.rule_id, rule.rule_name, rule.city_code, rule.org_id, org.org_name,
                   rule.trigger_type, rule.threshold_value, rule.task_type, rule.priority,
                   rule.title_template, rule.description_template, rule.due_minutes,
                   rule.cooldown_minutes, rule.auto_close, rule.enabled, rule.version,
                   rule.created_at, rule.updated_at
            FROM operations_task_rule rule
            JOIN organization org ON org.org_id = rule.org_id
            """;

    @Select(RULE_SELECT + " WHERE rule.city_code = #{cityCode} ORDER BY rule.created_at")
    List<TaskRule> findRules(@Param("cityCode") String cityCode);

    @Select(RULE_SELECT + """
            WHERE rule.city_code = #{cityCode} AND rule.enabled = true
            ORDER BY rule.created_at
            """)
    List<TaskRule> findEnabledRules(@Param("cityCode") String cityCode);

    @Select(RULE_SELECT + " WHERE rule.rule_id = #{ruleId}")
    TaskRule findRule(@Param("ruleId") String ruleId);

    @Insert("""
            INSERT INTO operations_task_rule (
                rule_id, rule_name, city_code, org_id, trigger_type, threshold_value,
                task_type, priority, title_template, description_template,
                due_minutes, cooldown_minutes, auto_close, enabled, created_by
            ) VALUES (
                #{ruleId}, #{ruleName}, #{cityCode}, #{orgId}, #{triggerType}, #{thresholdValue},
                #{taskType}, #{priority}, #{titleTemplate}, #{descriptionTemplate},
                #{dueMinutes}, #{cooldownMinutes}, #{autoClose}, #{enabled}, #{createdBy}
            )
            """)
    int insertRule(
            @Param("ruleId") String ruleId,
            @Param("ruleName") String ruleName,
            @Param("cityCode") String cityCode,
            @Param("orgId") String orgId,
            @Param("triggerType") String triggerType,
            @Param("thresholdValue") Integer thresholdValue,
            @Param("taskType") String taskType,
            @Param("priority") String priority,
            @Param("titleTemplate") String titleTemplate,
            @Param("descriptionTemplate") String descriptionTemplate,
            @Param("dueMinutes") int dueMinutes,
            @Param("cooldownMinutes") int cooldownMinutes,
            @Param("autoClose") boolean autoClose,
            @Param("enabled") boolean enabled,
            @Param("createdBy") String createdBy
    );

    @Update("""
            UPDATE operations_task_rule SET rule_name = #{ruleName}, city_code = #{cityCode},
                org_id = #{orgId}, trigger_type = #{triggerType}, threshold_value = #{thresholdValue},
                task_type = #{taskType}, priority = #{priority}, title_template = #{titleTemplate},
                description_template = #{descriptionTemplate}, due_minutes = #{dueMinutes},
                cooldown_minutes = #{cooldownMinutes}, auto_close = #{autoClose},
                enabled = #{enabled}, version = version + 1, updated_at = now()
            WHERE rule_id = #{ruleId} AND version = #{version}
            """)
    int updateRule(
            @Param("ruleId") String ruleId,
            @Param("version") int version,
            @Param("ruleName") String ruleName,
            @Param("cityCode") String cityCode,
            @Param("orgId") String orgId,
            @Param("triggerType") String triggerType,
            @Param("thresholdValue") Integer thresholdValue,
            @Param("taskType") String taskType,
            @Param("priority") String priority,
            @Param("titleTemplate") String titleTemplate,
            @Param("descriptionTemplate") String descriptionTemplate,
            @Param("dueMinutes") int dueMinutes,
            @Param("cooldownMinutes") int cooldownMinutes,
            @Param("autoClose") boolean autoClose,
            @Param("enabled") boolean enabled
    );

    @Insert("""
            INSERT INTO operations_task_rule (
                rule_id, rule_name, city_code, org_id, trigger_type, threshold_value,
                task_type, priority, title_template, description_template,
                due_minutes, cooldown_minutes, auto_close, enabled, created_by
            ) VALUES (
                #{ruleId}, #{ruleName}, #{cityCode}, #{orgId}, #{triggerType}, #{threshold},
                #{taskType}, #{priority}, #{title}, #{description},
                #{dueMinutes}, #{cooldownMinutes}, #{autoClose}, true, 'USR-ADMIN'
            ) ON CONFLICT DO NOTHING
            """)
    int insertMockRule(
            @Param("ruleId") String ruleId,
            @Param("ruleName") String ruleName,
            @Param("cityCode") String cityCode,
            @Param("orgId") String orgId,
            @Param("triggerType") String triggerType,
            @Param("threshold") Integer threshold,
            @Param("taskType") String taskType,
            @Param("priority") String priority,
            @Param("title") String title,
            @Param("description") String description,
            @Param("dueMinutes") int dueMinutes,
            @Param("cooldownMinutes") int cooldownMinutes,
            @Param("autoClose") boolean autoClose
    );
}
