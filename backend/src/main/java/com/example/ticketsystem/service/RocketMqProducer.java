package com.example.ticketsystem.service;

import com.example.ticketsystem.model.TicketOrderMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RocketMqProducer {

    private final ObjectMapper objectMapper;

    @Value("${ticket.rocketmq.namesrv:rocketmq-namesrv:9876}")
    private String namesrvAddr;

    @Value("${ticket.rocketmq.topic:ticket-order-topic}")
    private String topic;

    @Value("${ticket.rocketmq.producer-group:ticket-order-producer-group}")
    private String producerGroup;

    private DefaultMQProducer producer;

    public RocketMqProducer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() throws Exception {
        producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(namesrvAddr);
        producer.setSendMsgTimeout(3000);
        producer.start();
    }

    public void sendCreateOrder(TicketOrderMessage payload) {
        try {
            byte[] body = objectMapper.writeValueAsString(payload)
                    .getBytes(StandardCharsets.UTF_8);

            Message message = new Message(
                    topic,
                    "CREATE_ORDER",
                    payload.messageId(),
                    body
            );

            SendResult result = producer.send(message);

            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException(
                        "RocketMQ send failed: " +
                        (result == null ? "null result" : result.getSendStatus())
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("RocketMQ 訊息送出失敗", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
