package com.meterflow.billing.mq;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** RocketMQ 生产者（billing 出站事件总线）：T_BILLING_COMPLETED 等 topic 的统一发送通道 */
@Component
@Slf4j
public class EventProducer {

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    private DefaultMQProducer producer;

    @PostConstruct
    public void start() throws Exception {
        producer = new DefaultMQProducer("meterflow-billing-event-producer");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(5000);
        producer.setRetryTimesWhenSendFailed(1);
        producer.start();
        log.info("[MQ-PRODUCE] billing 事件生产者已启动（namesrv={}）", nameServer);
    }

    @PreDestroy
    public void stop() {
        if (producer != null) producer.shutdown();
    }

    /** 同步发送；失败抛出由调用方走重试/终态流程 */
    public void send(String topic, String bizKey, String payload) throws Exception {
        org.apache.rocketmq.common.message.Message m =
                new org.apache.rocketmq.common.message.Message(topic, null, bizKey, payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var r = producer.send(m);
        log.info("[MQ-PRODUCE] {} {} → {}", topic, bizKey, r.getSendStatus());
    }
}
