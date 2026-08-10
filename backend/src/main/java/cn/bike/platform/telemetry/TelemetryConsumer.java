package cn.bike.platform.telemetry;

import cn.bike.platform.telemetry.TelemetryModels.YadeaCloudEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("!report-worker")
public class TelemetryConsumer {

    private final JsonMapper jsonMapper;
    private final TelemetryProcessor telemetryProcessor;

    public TelemetryConsumer(JsonMapper jsonMapper, TelemetryProcessor telemetryProcessor) {
        this.jsonMapper = jsonMapper;
        this.telemetryProcessor = telemetryProcessor;
    }

    /**
     * 消费规范化后的车辆事件. 保留 String 消息边界, 便于以后替换雅迪字段映射而不改变 Kafka 主题.
     */
    @KafkaListener(topics = "${app.kafka.telemetry-topic}")
    public void consume(String payload) {
        telemetryProcessor.process(jsonMapper.readValue(payload, YadeaCloudEvent.class));
    }
}
