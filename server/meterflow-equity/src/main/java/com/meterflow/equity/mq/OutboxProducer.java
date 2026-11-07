package com.meterflow.equity.mq;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** RocketMQ 生产者（S4）：outbox 投递通道（T_ORDER_PAID）；发送失败由 relay 重试机制兜底 */
@Component
@Slf4j
public class OutboxProducer {

    @Value("${rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    private DefaultMQProducer producer;

    @PostConstruct
    public void start() throws Exception {
        producer = new DefaultMQProducer("meterflow-equity-producer");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(5000);
        producer.setRetryTimesWhenSendFailed(1);
        producer.start();
        log.info("[MQ-PRODUCE] 生产者已启动（namesrv={}）", nameServer);
    }

    @PreDestroy
    public void stop() {
        if (producer != null) producer.shutdown();
    }

    /** 同步发送；失败抛出由调用方走重试/终态流程 */
    public SendResult send(String topic, String bizKey, String payload) throws Exception {
        Message m = new Message(topic, null, bizKey, payload.getBytes(StandardCharsets.UTF_8));
        SendResult r = producer.send(m);
        log.info("[MQ-PRODUCE] {} {} → {}", topic, bizKey, r.getSendStatus());
        return r;
    }
}
