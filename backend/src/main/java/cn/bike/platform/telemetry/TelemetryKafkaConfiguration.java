package cn.bike.platform.telemetry;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@Profile("!report-worker")
public class TelemetryKafkaConfiguration {

    /**
     * 输入: Kafka 生产者; 输出: 遥测消费失败处理器。
     * 处理步骤: 原消息最多间隔 2 秒重试 5 次，仍失败时保留分区写入同名 .DLT 主题，供告警和人工重放。
     */
    @Bean
    public DefaultErrorHandler telemetryErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 5L));
    }
}
