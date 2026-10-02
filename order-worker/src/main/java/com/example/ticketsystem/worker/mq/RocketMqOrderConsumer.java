package com.example.ticketsystem.worker.mq;

import com.example.ticketsystem.worker.model.TicketOrderMessage;
import com.example.ticketsystem.worker.service.OrderProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class RocketMqOrderConsumer {

    private final ObjectMapper objectMapper;
    private final OrderProcessingService orderProcessingService;

    @Value("${ticket.rocketmq.namesrv:rocketmq-namesrv:9876}")
    private String namesrvAddr;

    @Value("${ticket.rocketmq.topic:ticket-order-topic}")
    private String topic;

    @Value("${ticket.rocketmq.consumer-group:ticket-order-consumer-group}")
    private String consumerGroup;

    @Value("${ticket.rocketmq.consumer-delay-ms:0}")
    private long consumerDelayMs;

    private DefaultMQPushConsumer consumer;

    public RocketMqOrderConsumer(
            ObjectMapper objectMapper,
            OrderProcessingService orderProcessingService
    ) {
        this.objectMapper = objectMapper;
        this.orderProcessingService = orderProcessingService;
    }

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(1);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setMaxReconsumeTimes(8);
        consumer.subscribe(topic, "CREATE_ORDER");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (var msg : msgs) {
                try {
                    TicketOrderMessage payload = objectMapper.readValue(
                            msg.getBody(),
                            TicketOrderMessage.class
                    );

                    if (consumerDelayMs > 0) {
                        Thread.sleep(consumerDelayMs);
                    }

                    var result = orderProcessingService.process(payload);

                    System.out.printf(
                            "[order-worker] messageId=%s eventId=%d userId=%s result=%s reconsumeTimes=%d%n",
                            payload.messageId(),
                            payload.eventId(),
                            payload.userId(),
                            result,
                            msg.getReconsumeTimes()
                    );
                } catch (DuplicateKeyException e) {
                    System.out.printf(
                            "[order-worker] duplicate message, ACK. msgId=%s%n",
                            msg.getMsgId()
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                } catch (Exception e) {
                    System.err.printf(
                            "[order-worker] failed, will retry. msgId=%s error=%s%n",
                            msg.getMsgId(),
                            e.getMessage()
                    );
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
        System.out.printf(
                "[order-worker] consumer started. namesrv=%s topic=%s group=%s%n",
                namesrvAddr,
                topic,
                consumerGroup
        );
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
