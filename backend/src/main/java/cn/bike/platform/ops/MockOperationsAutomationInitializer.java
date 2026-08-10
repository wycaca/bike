package cn.bike.platform.ops;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Profile("mock")
@Component
@Order(40)
public class MockOperationsAutomationInitializer implements ApplicationRunner {

    private final OperationsAutomationService service;

    public MockOperationsAutomationInitializer(OperationsAutomationService service) {
        this.service = service;
    }

    /** 输入: 启动参数; 输出: 无, 用最新Mock状态演示规则自动生成与去重。 */
    @Override
    public void run(ApplicationArguments args) {
        service.scanCity("110000");
        service.scanCity("310000");
    }
}
