package com.meterflow.billing.dto;

import java.math.BigDecimal;
import java.util.List;

/** 事件上报出入参（05 §4.1） */
public final class IngestDtos {

    private IngestDtos() {}

    /** 单条事件入参（复合事件 R1；计费以到达时间为准 R5） */
    public record EventParam(String ingestSystem, String eventId, String bizNo, Long productId,
                             String eventTime, List<MetricQty> metrics) {}

    public record MetricQty(String key, BigDecimal qty) {}

    /** 抵扣链步骤（①赠送 ②内置 ③资源包 ④余额） */
    public record ChainStep(String source, String note, String amount) {}

    /** 受理结果：accepted / dup（幂等返回首次结果 R4） */
    public record IngestResultVO(String status, Long eventId, String costAmount,
                                 List<ChainStep> chain, Long balanceCents, String stage,
                                 Long originEventId, String message) {}
}
