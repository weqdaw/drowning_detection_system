package com.hc.drown.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka消息生产者服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送异步消息
     */
    public void sendMessage(String topic, Object message) {
        log.info("Sending message to topic: {}, message: {}", topic, message);
        kafkaTemplate.send(topic, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message to topic: {}", topic, ex);
                    } else {
                        log.debug("Successfully sent message to topic: {}, partition: {}, offset: {}",
                                topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * 发送异步消息（含Key）
     */
    public void sendMessage(String topic, String key, Object message) {
        log.info("Sending message to topic: {}, key: {}, message: {}", topic, key, message);
        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message to topic: {}", topic, ex);
                    } else {
                        log.debug("Successfully sent message to topic: {}, partition: {}, offset: {}",
                                topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
