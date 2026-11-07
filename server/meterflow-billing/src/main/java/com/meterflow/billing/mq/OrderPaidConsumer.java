package com.meterflow.billing.mq;

import com.meterflow.billing.service.GrantService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** RocketMQ 消费者（S4）：订阅 T_ORDER_PAID → 实例发放（与 Feign 通道共用 GrantService，幂等键=orderNo）。
 *  消费失败自动重试（RocketMQ 机制，最大 16 次）；消费位移由集群管理。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPaidConsumer {

    private final GrantService grantService;

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer("meterflow-billing-grant-group");
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe("T_ORDER_PAID", "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (List<MessageExt> msgs, ConsumeConcurrentlyContext ctx) -> {
            for (MessageExt m : msgs) {
                String payload = new String(m.getBody(), StandardCharsets.UTF_8);
                try {
                    Map<String, Object> req = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    Map<String, Object> result = grantService.grant(req);
                    log.info("[MQ-CONSUME] T_ORDER_PAID {} → {}", String.valueOf(req.get("orderNo")), result);
                } catch (Exception e) {
                    log.warn("[MQ-CONSUME] 发放失败将重试 bizKey={}：{}", m.getKeys(), e.getMessage());
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;   // 稍后重试（幂等键保证不重复发放）
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("[MQ-CONSUME] T_ORDER_PAID 消费者已启动（namesrv={}）", nameServer);
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) consumer.shutdown();
    }
}
