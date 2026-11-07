package com.meterflow.billing.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meterflow.billing.entity.LocalMessage;
import com.meterflow.billing.mapper.LocalMessageMapper;
import com.meterflow.billing.mq.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** billing 出站事件 relay（S4 同构）：LocalMessage(init) → RocketMQ T_BILLING_COMPLETED → confirmed。
 *  消费联动（finance 催缴 / platform 审计入 ES）在对应阶段接入；MAX_RETRY 终态防毒消息。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalMessageRelay {

    private static final int MAX_RETRY = 50;

    private final LocalMessageMapper localMessageMapper;
    private final EventProducer eventProducer;

    @Scheduled(fixedDelay = 3000)
    public void relay() {
        List<LocalMessage> pending = localMessageMapper.selectList(new LambdaQueryWrapper<LocalMessage>()
                .eq(LocalMessage::getStatus, "init")
                .and(w -> w.isNull(LocalMessage::getNextRetryAt).or().le(LocalMessage::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(LocalMessage::getId)
                .last("limit 50"));
        for (LocalMessage msg : pending) {
            try {
                eventProducer.send(msg.getTopic(), msg.getBizKey(), msg.getPayload());
                msg.setStatus("confirmed");
                msg.setUpdatedAt(LocalDateTime.now());
                localMessageMapper.updateById(msg);
            } catch (Exception e) {
                int retry = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;
                msg.setRetryCount(retry);
                msg.setUpdatedAt(LocalDateTime.now());
                if (retry >= MAX_RETRY) {
                    msg.setStatus("failed");   // 毒消息终态：留痕待人工处置
                    log.error("[RELAY] {} 超过最大重试次数（{}），转 failed 终态", msg.getBizKey(), retry);
                } else {
                    msg.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(60, 10L * retry)));
                }
                localMessageMapper.updateById(msg);
            }
        }
    }
}
