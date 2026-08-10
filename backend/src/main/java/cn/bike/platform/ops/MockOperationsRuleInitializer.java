package cn.bike.platform.ops;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Profile("mock")
@Component
@Order(20)
public class MockOperationsRuleInitializer implements ApplicationRunner {

    private final JdbcClient jdbcClient;

    public MockOperationsRuleInitializer(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 输入: 启动参数; 输出: 无, 在车辆遥测载入前准备默认自动任务规则。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCity("BJ", "110000", "ORG-BJ");
        seedCity("SH", "310000", "ORG-SH");
    }

    private void seedCity(String prefix, String cityCode, String orgId) {
        insert("RULE-" + prefix + "-LOW", "低电量自动换电", cityCode, orgId,
                "LOW_BATTERY", 20, "BATTERY_SWAP", "URGENT",
                "车辆{vehicleId}低电量换电", "当前电量{batteryPercent}%，请尽快更换电池", 30, 60, true);
        insert("RULE-" + prefix + "-FAULT", "车辆故障自动报修", cityCode, orgId,
                "VEHICLE_FAULT", null, "REPAIR", "HIGH",
                "车辆{vehicleId}故障检修", "故障码：{faultCodes}", 120, 60, false);
        insert("RULE-" + prefix + "-OFFLINE", "离线车辆自动寻车", cityCode, orgId,
                "VEHICLE_OFFLINE", null, "RETRIEVAL", "HIGH",
                "车辆{vehicleId}离线寻车", "车辆已离线，请按最后定位现场核查", 60, 120, true);
        insert("RULE-" + prefix + "-GEO", "围栏违规自动回收", cityCode, orgId,
                "GEO_VIOLATION", null, "RETRIEVAL", "HIGH",
                "车辆{vehicleId}围栏违规处理", "车辆位于运营区外或禁停区域", 60, 30, true);
    }

    private void insert(
            String ruleId,
            String ruleName,
            String cityCode,
            String orgId,
            String triggerType,
            Integer threshold,
            String taskType,
            String priority,
            String title,
            String description,
            int dueMinutes,
            int cooldownMinutes,
            boolean autoClose
    ) {
        jdbcClient.sql("""
                        INSERT INTO operations_task_rule (
                            rule_id, rule_name, city_code, org_id, trigger_type, threshold_value,
                            task_type, priority, title_template, description_template,
                            due_minutes, cooldown_minutes, auto_close, enabled, created_by
                        ) VALUES (
                            :ruleId, :ruleName, :cityCode, :orgId, :triggerType, :threshold,
                            :taskType, :priority, :title, :description,
                            :dueMinutes, :cooldownMinutes, :autoClose, true, 'USR-ADMIN'
                        ) ON CONFLICT DO NOTHING
                        """).param("ruleId", ruleId).param("ruleName", ruleName).param("cityCode", cityCode)
                .param("orgId", orgId).param("triggerType", triggerType).param("threshold", threshold)
                .param("taskType", taskType).param("priority", priority).param("title", title)
                .param("description", description).param("dueMinutes", dueMinutes)
                .param("cooldownMinutes", cooldownMinutes).param("autoClose", autoClose).update();
    }
}
