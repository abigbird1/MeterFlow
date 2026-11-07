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
    private final BillingCallbackClient billingCallbackClient;   // 回补/人工处置通道保留
    private final com.meterflow.equity.mq.OutboxProducer outboxProducer;

    /** 重试上限：超过后转入 failed 终态（毒消息隔离，保留留痕供人工处置；MQ 阶段对应死信） */
    private static final int MAX_RETRY = 50;

    @Scheduled(fixedDelay = 2000)
    public void relay() {
        List<OutboxMessage> pending = outboxMapper.selectList(new LambdaQueryWrapper<OutboxMessage>()
                .eq(OutboxMessage::getStatus, "init")
                .and(w -> w.isNull(OutboxMessage::getNextRetryAt).or().le(OutboxMessage::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(OutboxMessage::getId)
                .last("limit 20"));
        for (OutboxMessage msg : pending) {
            try {
                // 投递通道：RocketMQ（03 设计基线）；发送失败走下方重试/终态机制
                outboxProducer.send(msg.getTopic(), msg.getBizKey(), msg.getPayload());
                msg.setStatus("confirmed");
                msg.setUpdatedAt(LocalDateTime.now());
                outboxMapper.updateById(msg);
            } catch (Exception e) {
                int retry = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;
                msg.setRetryCount(retry);
                msg.setUpdatedAt(LocalDateTime.now());
                if (retry >= MAX_RETRY) {
                    // 毒消息终态：来源单据已不存在/持续失败，停止重试避免拖垮投递通道（留痕待人工处置）
                    msg.setStatus("failed");
                    log.error("[OUTBOX] {} 超过最大重试次数（{}），转 failed 终态", msg.getBizKey(), retry);
                } else {
                    msg.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(60, 10L * retry)));   // 递增退避
                    log.warn("[OUTBOX] 投递失败 {} retry={}: {}", msg.getBizKey(), retry, e.getMessage());
                }
                outboxMapper.updateById(msg);
            }
        }
    }
}
