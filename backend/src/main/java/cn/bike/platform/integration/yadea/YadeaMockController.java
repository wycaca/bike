package cn.bike.platform.integration.yadea;

import cn.bike.platform.common.ApiResponse;
import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@Profile("mock")
@RestController
@RequestMapping("/api/v1/mock/yadea")
public class YadeaMockController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String telemetryTopic;

    public YadeaMockController(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${app.kafka.telemetry-topic}") String telemetryTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.telemetryTopic = telemetryTopic;
    }

    /**
     * 模拟雅迪云推送单条车辆事件. 该接口只在 mock 环境存在, 不能作为正式雅迪协议使用.
     */
    @PostMapping("/events")
    public ApiResponse<AcceptedEvent> acceptEvent(@Valid @RequestBody YadeaCloudEvent event) {
        kafkaTemplate.send(telemetryTopic, event.vehicleId(), jsonMapper.writeValueAsString(event));
        return ApiResponse.ok(new AcceptedEvent(event.eventId(), "QUEUED"));
    }

    public record AcceptedEvent(String eventId, String status) {
    }
}
