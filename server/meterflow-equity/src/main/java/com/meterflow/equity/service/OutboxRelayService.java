package com.meterflow.equity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.equity.entity.OutboxMessage;
import com.meterflow.equity.feign.BillingCallbackClient;
import com.meterflow.equity.mapper.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox Relay（S4）：扫描 outbox → 投递 billing 发放实例 → confirmed。
 * 当前投递通道为 Feign HTTP（本地消息表语义完整：至少一次 + 消费幂等）；
 * 接入 RocketMQ 后仅替换投递通道为 MQ 发送，其余不变。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxMessageMapper outboxMapper;
    private final BillingCallbackClient billingCallbackClient;

    @Scheduled(fixedDelay = 2000)
    public void relay() {
        List<OutboxMessage> pending = outboxMapper.selectList(new LambdaQueryWrapper<OutboxMessage>()
                .eq(OutboxMessage::getStatus, "init")
                .orderByAsc(OutboxMessage::getId)
                .last("limit 20"));
        for (OutboxMessage msg : pending) {
            try {
                Map<String, Object> resp = billingCallbackClient.grantInstance(
                        Map.of("payload", msg.getPayload()));
                log.info("[OUTBOX] {} {} → {}", msg.getTopic(), msg.getBizKey(),
                        String.valueOf(resp.get("data")));
                msg.setStatus("confirmed");
                msg.setUpdatedAt(LocalDateTime.now());
                outboxMapper.updateById(msg);
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() == null ? 1 : msg.getRetryCount() + 1);
                msg.setNextRetryAt(LocalDateTime.now().plusSeconds(10));
                msg.setUpdatedAt(LocalDateTime.now());
                outboxMapper.updateById(msg);
                log.warn("[OUTBOX] 投递失败 {} retry={}: {}", msg.getBizKey(), msg.getRetryCount(), e.getMessage());
            }
        }
    }
}
