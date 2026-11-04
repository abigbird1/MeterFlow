package com.meterflow.equity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.meterflow.equity.entity.Goods;
import com.meterflow.equity.entity.GoodsOrder;
import com.meterflow.equity.entity.OutboxMessage;
import com.meterflow.equity.entity.PayRecord;
import com.meterflow.equity.entity.StockLog;
import com.meterflow.equity.exception.EquityErrorCode;
import com.meterflow.equity.feign.BillingCallbackClient;
import com.meterflow.equity.mapper.GoodsMapper;
import com.meterflow.equity.mapper.GoodsOrderMapper;
import com.meterflow.equity.mapper.OutboxMessageMapper;
import com.meterflow.equity.mapper.PayRecordMapper;
import com.meterflow.equity.mapper.StockLogMapper;
import com.meterflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 抢购订单（R31/R32/R43）：下单锁库存（DB 条件更新原子扣减，Redis Lua 预扣在 Redis 可用后启用 AD-T4）
 * → 支付（状态校验防超付）→ outbox（T_ORDER_PAID）→ relay 投递 billing 发放实例（S4）；
 * 超时释放：周期扫描（分钟级 R43）。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final GoodsMapper goodsMapper;
    private final GoodsOrderMapper orderMapper;
    private final PayRecordMapper payRecordMapper;
    private final StockLogMapper stockLogMapper;
    private final OutboxMessageMapper outboxMapper;
    private final BillingCallbackClient billingCallbackClient;
    private final StringRedisTemplate redis;

    /** Redis 预扣（AD-T4）；Redis 不可用时降级 DB 条件更新 */
    private boolean redisReserve(Long goodsId, int qty) {
        try {
            Long left = redis.opsForValue().decrement("equity:stock:" + goodsId, qty);
            return left != null && left >= 0;
        } catch (Exception e) { return true; }
    }

    private void redisRelease(Long goodsId, int qty) {
        try { redis.opsForValue().increment("equity:stock:" + goodsId, qty); } catch (Exception ignored) { }
    }

    public record OrderVO(String orderNo, Long goodsId, String goodsName, Long amountCents,
                          String status, String payExpireAt) {}

    /** 下单锁库存：条件更新原子扣减（I5：sold+qty ≤ total_stock） */
    @Transactional
    public OrderVO place(Long tenantId, Long goodsId, int qty) {
        Goods g = goodsMapper.selectById(goodsId);
        if (g == null || !"on".equals(g.getStatus())) {
            throw new BusinessException(EquityErrorCode.SOLD_OUT, "商品不可购");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(g.getWindowStart()) || today.isAfter(g.getWindowEnd())) {
            throw new BusinessException(EquityErrorCode.WINDOW, "开售窗口 " + g.getWindowStart() + " ~ " + g.getWindowEnd());
        }
        long bought = orderMapper.selectCount(new LambdaQueryWrapper<GoodsOrder>()
                .eq(GoodsOrder::getTenantId, tenantId)
                .eq(GoodsOrder::getGoodsId, goodsId)
                .in(GoodsOrder::getStatus, "pending", "paid"));
        if (bought + qty > g.getPerLimit()) throw new BusinessException(EquityErrorCode.PER_LIMIT);
        qty = Math.min(qty, g.getOrderLimit());

        int locked = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .apply("sold + {0} <= total_stock", qty)
                .setSql("sold = sold + " + qty));
        if (locked == 0) { redisRelease(goodsId, qty); throw new BusinessException(EquityErrorCode.SOLD_OUT); }

        GoodsOrder o = new GoodsOrder();
        o.setOrderNo("PO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        o.setTenantId(tenantId); o.setGoodsId(goodsId); o.setQty(qty);
        o.setAmountCents(g.getPriceCents() * qty); o.setStatus("pending");
        o.setPayExpireAt(LocalDateTime.now().plusMinutes(15)); o.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(o);
        stock(goodsId, o.getOrderNo(), -qty, "lock");
        return toVO(o, g);
    }

    /** 模拟支付（R43：先校验订单状态，防超付）→ outbox 事件（S4） */
    @Transactional
    public OrderVO pay(Long tenantId, String orderNo, String method) {
        GoodsOrder o = orderByNo(orderNo);
        requireOwner(o, tenantId);
        if (!"pending".equals(o.getStatus())) throw new BusinessException(EquityErrorCode.ORDER_CLOSED);
        Goods g = goodsMapper.selectById(o.getGoodsId());
        o.setStatus("paid");
        o.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(o);
        stock(o.getGoodsId(), orderNo, 0, "sale");

        PayRecord p = new PayRecord();
        p.setOrderNo(orderNo); p.setTenantId(tenantId); p.setAmountCents(o.getAmountCents());
        p.setMethod(method == null ? "支付宝（模拟）" : method); p.setStatus("success");
        payRecordMapper.insert(p);

        OutboxMessage msg = new OutboxMessage();
        msg.setTopic("T_ORDER_PAID");
        msg.setBizKey(orderNo);
        msg.setPayload("{\"orderNo\":\"" + orderNo + "\",\"tenantId\":" + tenantId
                + ",\"goodsId\":" + o.getGoodsId() + ",\"qty\":" + o.getQty()
                + ",\"quantity\":" + (g == null || g.getQuantity() == null ? 0 : g.getQuantity())
                + ",\"unit\":\"" + (g == null ? "Token" : g.getUnit()) + "\",\"days\":" + (g == null ? 90 : g.getValidDays()) + "}");
        msg.setStatus("init"); msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now()); msg.setUpdatedAt(LocalDateTime.now());
        outboxMapper.insert(msg);
        return toVO(o, goodsMapper.selectById(o.getGoodsId()));
    }

    /** 主动取消（库存释放） */
    @Transactional
    public OrderVO cancel(Long tenantId, String orderNo) {
        GoodsOrder o = orderByNo(orderNo);
        requireOwner(o, tenantId);
        if (!"pending".equals(o.getStatus())) throw new BusinessException(EquityErrorCode.ORDER_CLOSED);
        o.setStatus("canceled");
        o.setCloseNote("用户主动取消，库存已释放");
        orderMapper.updateById(o);
        release(o);
        return toVO(o, goodsMapper.selectById(o.getGoodsId()));
    }

    /** 超时释放（R43：分钟级周期扫描；由 @Scheduled 驱动） */
    @Scheduled(fixedDelay = 10_000)
    public void releaseExpired() {
        List<GoodsOrder> expired = orderMapper.selectList(new LambdaQueryWrapper<GoodsOrder>()
                .eq(GoodsOrder::getStatus, "pending")
                .le(GoodsOrder::getPayExpireAt, LocalDateTime.now()));
        for (GoodsOrder o : expired) {
            o.setStatus("closed");
            o.setCloseNote("超时未支付，库存自动释放（R43）");
            orderMapper.updateById(o);
            release(o);
        }
    }

    public List<GoodsOrder> page(Long tenantId, String status) {
        LambdaQueryWrapper<GoodsOrder> qw = new LambdaQueryWrapper<>();
        if (tenantId != null) qw.eq(GoodsOrder::getTenantId, tenantId);
        if (status != null && !status.isBlank()) qw.eq(GoodsOrder::getStatus, status);
        return orderMapper.selectList(qw.orderByDesc(GoodsOrder::getId));
    }

    /* ---------- 私有 ---------- */

    private GoodsOrder orderByNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<GoodsOrder>()
                .eq(GoodsOrder::getOrderNo, orderNo).last("limit 1"));
    }

    private void requireOwner(GoodsOrder o, Long tenantId) {
        if (o == null || !o.getTenantId().equals(tenantId)) {
            throw new BusinessException(EquityErrorCode.ORDER_CLOSED, "订单不存在");
        }
    }

    private void release(GoodsOrder o) {
        Goods g = goodsMapper.selectById(o.getGoodsId());
        if (g != null) {
            g.setSold(Math.max(0, g.getSold() - o.getQty()));
            goodsMapper.updateById(g);
        }
        redisRelease(o.getGoodsId(), o.getQty());
        stock(o.getGoodsId(), o.getOrderNo(), o.getQty(), "release");
    }

    private void stock(Long goodsId, String orderNo, int change, String reason) {
        StockLog s = new StockLog();
        s.setGoodsId(goodsId); s.setOrderNo(orderNo); s.setChangeQty(change); s.setReason(reason);
        stockLogMapper.insert(s);
    }

    private OrderVO toVO(GoodsOrder o, Goods g) {
        return new OrderVO(o.getOrderNo(), o.getGoodsId(), g == null ? null : g.getName(),
                o.getAmountCents(), o.getStatus(),
                o.getPayExpireAt() == null ? null : o.getPayExpireAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
