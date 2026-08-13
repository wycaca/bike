package cn.bike.platform.ops;

import cn.bike.platform.ops.rule.OperationsRuleMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mock 环境的自动运维规则初始化器.
 * 固定规则编号允许重复启动, 也便于遥测触发场景稳定复用.
 */
@Profile("mock")
@Component
@Order(20)
public class MockOperationsRuleInitializer implements ApplicationRunner {

    private final OperationsRuleMapper mapper;

    public MockOperationsRuleInitializer(OperationsRuleMapper mapper) {
        this.mapper = mapper;
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
        mapper.insertMockRule(ruleId, ruleName, cityCode, orgId, triggerType, threshold, taskType, priority,
                title, description, dueMinutes, cooldownMinutes, autoClose);
    }
}
