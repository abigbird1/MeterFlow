package com.meterflow.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.billing.dto.IngestDtos.ChainStep;
import com.meterflow.billing.dto.IngestDtos.EventParam;
import com.meterflow.billing.dto.IngestDtos.IngestResultVO;
import com.meterflow.billing.dto.IngestDtos.MetricQty;
import com.meterflow.billing.entity.*;
import com.meterflow.billing.mapper.*;
import com.meterflow.billing.service.KeyCacheService.KeyContext;
import com.meterflow.billing.exception.BillingErrorCode;
import com.meterflow.common.exception.BusinessException;
import com.meterflow.common.utils.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 计量计费核心（热路径，P1/P7）：受理 → 双保险判重（R3/R4）→ 计价（R5/R10/R11）→
 * 抵扣链（赠送→内置→资源包→余额，R13）→ 落明细与流水（R15）→ 停服判定（R22）。
 */
@Service
@RequiredArgsConstructor
public class MeteringService {

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ProductMapper productMapper;
    private final ProductMetricMapper metricMapper;
    private final PriceVersionMapper versionMapper;
    private final PriceItemMapper priceItemMapper;
    private final UsageEventMapper usageEventMapper;
    private final UsageCursorMapper cursorMapper;
    private final FreeQuotaMapper freeQuotaMapper;
    private final GiftGrantMapper giftGrantMapper;
    private final PackageInstanceMapper packageMapper;
    private final BalanceAccountMapper accountMapper;
    private final DeductionRecordMapper recordMapper;
    private final DeductionItemMapper itemMapper;
    private final AccountTransactionMapper transactionMapper;
    private final SuspensionLogMapper suspensionLogMapper;
    private final LocalMessageMapper localMessageMapper;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider;

    /** 事件受理（同租户串行：账户行锁 FOR UPDATE） */
    @Transactional
    public IngestResultVO accept(KeyContext ctx, EventParam ev) {
        Long tenantId = ctx.tenantId();
        Long deptId = ctx.deptAssigned() ? ctx.deptId() : null;
        String deptName = ctx.deptAssigned() ? ctx.deptName() : null;
        LocalDateTime arrive = LocalDateTime.now();          // R5 到达时间计费
        String period = arrive.format(PERIOD);

        // 0) 停服判定（R8/R22：锁账户行，同租户串行）
        BalanceAccount acc = lockAccount(tenantId);
        if ("suspended".equals(acc.getStatus())) throw new BusinessException(BillingErrorCode.SUSPENDED);
        String topProduct = latestTopProduct(tenantId);
        Product product = productMapper.selectById(ev.productId());
        if (product == null || !"on".equals(product.getStatus())) {
            throw new BusinessException(BillingErrorCode.PRODUCT_NOT_FOUND);
        }
        // ② API Key 限定产品集校验（D2：api_key.products；"全部产品"或 NULL 不限）
        if (ctx.products() != null && !ctx.products().isEmpty()
                && !ctx.products().contains("全部产品")
                && !ctx.products().contains(product.getName())) {
            throw new BusinessException(BillingErrorCode.KEY_INVALID, "该 Key 未授权产品 " + product.getName());
        }
        if ("warning".equals(acc.getStatus()) && product.getName().equals(topProduct)) {
            throw new BusinessException(BillingErrorCode.SUSPENDED);
        }

        // 1) 双保险判重（R3/I1）：第一层 Redis SETNX 前置（B1，无 Redis 自动降级）；第二层库内唯一索引
        String idemKey = "meter:idem:" + ev.ingestSystem() + ":" + ev.eventId();
        var redisTpl = redisProvider.getIfAvailable();
        if (redisTpl != null) {
            try {
                Boolean first = redisTpl.opsForValue().setIfAbsent(idemKey, "1", java.time.Duration.ofHours(48));
                if (Boolean.FALSE.equals(first)) return dupResult(dupBySystemEvent(ev.ingestSystem(), ev.eventId()));
            } catch (Exception redisDown) {
                // 降级：仅库内唯一索引兜底
            }
        }

        UsageEvent origin = usageEventMapper.selectOne(new LambdaQueryWrapper<UsageEvent>()
                .eq(UsageEvent::getIngestSystem, ev.ingestSystem())
                .eq(UsageEvent::getEventId, ev.eventId())
                .last("limit 1"));
        if (origin != null) return dupResult(origin);

        // 2) 计价上下文：当前生效版本（R11）
        PriceVersion version = versionMapper.selectList(new LambdaQueryWrapper<PriceVersion>()
                        .eq(PriceVersion::getProductId, product.getId())
                        .le(PriceVersion::getEffectiveAt, arrive)
                        .orderByDesc(PriceVersion::getEffectiveAt)
                        .last("limit 1"))
                .stream().findFirst().orElseThrow(() -> new BusinessException(BillingErrorCode.PRODUCT_NOT_FOUND));
        Map<Long, PriceItem> priceByMetric = priceItemMapper.selectList(new LambdaQueryWrapper<PriceItem>()
                        .eq(PriceItem::getVersionId, version.getId()))
                .stream().collect(java.util.stream.Collectors.toMap(PriceItem::getMetricId, pi -> pi));

        List<DeductionItem> pendingItems = new ArrayList<>();
        List<ChainStep> chain = new ArrayList<>();
        Map<Long, BigDecimal> metricCost = new LinkedHashMap<>();   // 各指标 billable 费用（指定包精准抵扣）
        BigDecimal total = BigDecimal.ZERO;

        // 3) 逐指标：①赠送 → ②内置 → 分段计价（R10/R13，先赠送后内置 A2）
        for (MetricQty mq : ev.metrics()) {
            ProductMetric metric = metricMapper.selectOne(new LambdaQueryWrapper<ProductMetric>()
                    .eq(ProductMetric::getProductId, product.getId())
                    .eq(ProductMetric::getMetricKey, mq.key())
                    .last("limit 1"));
            if (metric == null || mq.qty() == null || mq.qty().signum() <= 0) continue;
            long div = metric.getMetricKey().endsWith("_tok") ? 1000 : 1;
            PriceItem pi = priceByMetric.get(metric.getId());
            if (pi == null) continue;
            BigDecimal unitPrice = pi.getPrice();

            BigDecimal billable = mq.qty();
            // ① 赠送额度（同指标、未过期，先到期先扣）
            List<GiftGrant> gifts = giftGrantMapper.selectList(new LambdaQueryWrapper<GiftGrant>()
                    .eq(GiftGrant::getTenantId, tenantId)
                    .eq(GiftGrant::getMetricId, metric.getId())
                    .gt(GiftGrant::getRemaining, BigDecimal.ZERO)
                    .ge(GiftGrant::getExpireDate, arrive.toLocalDate())
                    .orderByAsc(GiftGrant::getExpireDate));
            for (GiftGrant g : gifts) {
                BigDecimal used = g.getRemaining().min(billable);
                if (used.signum() <= 0) break;
                g.setRemaining(g.getRemaining().subtract(used));
                if (g.getRemaining().signum() == 0) g.setStatus("exhausted");
                giftGrantMapper.updateById(g);
                billable = billable.subtract(used);
                BigDecimal amt = Money.process(used.multiply(unitPrice).divide(BigDecimal.valueOf(div), 6, RoundingMode.HALF_UP));
                chain.add(new ChainStep("①赠送免费额度", g.getName() + " · " + metric.getName() + " " + strip(used), amt.toPlainString()));
                pendingItems.add(item(tenantId, "gift", g.getId(), g.getName(), amt));
            }
            // ② 内置月度额度（惰性建行：本月额度随指标定义）
            FreeQuota fq = freeQuotaMapper.selectOne(new LambdaQueryWrapper<FreeQuota>()
                    .eq(FreeQuota::getTenantId, tenantId)
                    .eq(FreeQuota::getProductId, product.getId())
                    .eq(FreeQuota::getMetricId, metric.getId())
                    .eq(FreeQuota::getPeriod, period)
                    .last("limit 1"));
            if (fq == null) {
                fq = new FreeQuota();
                fq.setTenantId(tenantId); fq.setProductId(product.getId()); fq.setMetricId(metric.getId());
                fq.setPeriod(period); fq.setTotalQuota(metric.getFreeQuota()); fq.setRemaining(metric.getFreeQuota());
                freeQuotaMapper.insert(fq);
            }
            BigDecimal builtinUsed = fq.getRemaining().min(billable);
            if (builtinUsed.signum() > 0) {
                fq.setRemaining(fq.getRemaining().subtract(builtinUsed));
                freeQuotaMapper.updateById(fq);
                billable = billable.subtract(builtinUsed);
                BigDecimal amt = Money.process(builtinUsed.multiply(unitPrice).divide(BigDecimal.valueOf(div), 6, RoundingMode.HALF_UP));
                chain.add(new ChainStep("②内置月度额度", metric.getName() + " " + strip(builtinUsed) + "（本月剩余额度内）", amt.toPlainString()));
                pendingItems.add(item(tenantId, "builtin", fq.getId(), metric.getName(), amt));
            }
            // ③ 分段计价（游标只累计计费用量，R10）
            if (billable.signum() <= 0) continue;
            UsageCursor cur = cursorMapper.selectOne(new LambdaQueryWrapper<UsageCursor>()
                    .eq(UsageCursor::getTenantId, tenantId)
                    .eq(UsageCursor::getProductId, product.getId())
                    .eq(UsageCursor::getMetricId, metric.getId())
                    .eq(UsageCursor::getPeriod, period)
                    .last("limit 1"));
            if (cur == null) {
                cur = new UsageCursor();
                cur.setTenantId(tenantId); cur.setProductId(product.getId()); cur.setMetricId(metric.getId());
                cur.setPeriod(period); cur.setQty(BigDecimal.ZERO);
                cursorMapper.insert(cur);
            }
            BigDecimal cost = tierCost(metric.getTierMode(), versionTier(pi), metric.getTierJson(),
                    cur.getQty(), billable, pi.getPrice(), div);
            cur.setQty(cur.getQty().add(billable));
            cursorMapper.updateById(cur);
            total = total.add(cost);
            metricCost.merge(metric.getId(), cost, BigDecimal::add);
        }

        // 4) 资源包抵扣（③：指定包只抵绑定指标费用 → 通用包兜底，R14/A1）
        BigDecimal rest = Money.process(total);
        List<PackageInstance> specs = packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getTenantId, tenantId)
                .eq(PackageInstance::getPkgType, "spec")
                .eq(PackageInstance::getProductId, product.getId())
                .eq(PackageInstance::getStatus, "active")
                .gt(PackageInstance::getRemaining, BigDecimal.ZERO)
                .orderByAsc(PackageInstance::getExpireDate));
        for (PackageInstance pkg : specs) {
            if (rest.signum() <= 0) break;
            ProductMetric bound = metricOf(product.getId(), pkg.getMetricKey());
            if (bound == null) continue;
            long div = pkg.getMetricKey().endsWith("_tok") ? 1000 : 1;
            PriceItem pi = priceByMetric.get(bound.getId());
            if (pi == null) continue;
            BigDecimal perUnit = pi.getPrice().divide(BigDecimal.valueOf(div), 6, RoundingMode.HALF_UP);
            BigDecimal boundCost = metricCost.getOrDefault(bound.getId(), BigDecimal.ZERO);
            long units = boundCost.divide(perUnit, 0, RoundingMode.DOWN).longValue();
            units = Math.min(units, pkg.getRemaining().longValue());
            if (units <= 0) continue;
            BigDecimal amt = Money.process(BigDecimal.valueOf(units).multiply(perUnit));
            pkg.setRemaining(pkg.getRemaining().subtract(BigDecimal.valueOf(units)));
            if (pkg.getRemaining().signum() == 0) pkg.setStatus("exhausted");
            packageMapper.updateById(pkg);
            chain.add(new ChainStep("③资源包（指定包优先）", pkg.getTitle() + " · 抵扣 " + units + " " + pkg.getUnit(), amt.toPlainString()));
            pendingItems.add(item(tenantId, "pkg_spec", pkg.getId(), pkg.getTitle(), amt));
            rest = Money.process(rest.subtract(amt));
            metricCost.put(bound.getId(), Money.process(boundCost.subtract(amt)));
        }
        List<PackageInstance> commons = packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getTenantId, tenantId)
                .eq(PackageInstance::getPkgType, "common")
                .eq(PackageInstance::getStatus, "active")
                .gt(PackageInstance::getRemaining, BigDecimal.ZERO)
                .orderByAsc(PackageInstance::getExpireDate));
        for (PackageInstance pkg : commons) {
            if (rest.signum() <= 0) break;
            BigDecimal use = pkg.getRemaining().min(rest);
            pkg.setRemaining(pkg.getRemaining().subtract(use));
            if (pkg.getRemaining().signum() == 0) pkg.setStatus("exhausted");
            packageMapper.updateById(pkg);
            chain.add(new ChainStep("③资源包（通用包兜底）", pkg.getTitle() + " · 金额抵扣", use.toPlainString()));
            pendingItems.add(item(tenantId, "pkg_common", pkg.getId(), pkg.getTitle(), use));
            rest = Money.process(rest.subtract(use));
        }

        // 5) 余额（④：允许有限透支 R16；合并后 HALF_UP 入账，ET1）
        long balanceUsedCents = 0;
        if (rest.signum() > 0) {
            balanceUsedCents = Money.toCents(rest);
            acc.setBalanceCents(acc.getBalanceCents() - balanceUsedCents);
            chain.add(new ChainStep("④余额", acc.getBalanceCents() < 0 ? "余额扣至透支" : "余额扣减", Money.process(rest).toPlainString()));
        } else {
            chain.add(new ChainStep("④余额", "费用已全部被额度/资源包覆盖", "0.0000"));
        }

        // 6) 停服判定（分级 R22：转负→预警停最高消费产品；达阈值→全停；回正→恢复 R23）
        String stage = judge(acc, product.getName());

        // 7) 落事件（append-only；唯一索引兜底 R3）
        UsageEvent e = new UsageEvent();
        e.setIngestSystem(ev.ingestSystem());
        e.setEventId(ev.eventId());
        e.setTenantId(tenantId);
        e.setDeptId(deptId);
        e.setDeptName(deptName);
        e.setProductId(product.getId());
        e.setKeyId(ctx.keyId());
        e.setMetrics(toJson(metricsMap(ev.metrics())));
        e.setBizNo(ev.bizNo());
        e.setArriveTime(arrive);
        e.setPriceVersion(version.getVersionNo().toString());
        e.setCostAmount(Money.process(total));
        e.setStatus("normal");
        try {
            usageEventMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // 并发窗口下的兜底：以库内首条为准（R4）
            UsageEvent first = usageEventMapper.selectOne(new LambdaQueryWrapper<UsageEvent>()
                    .eq(UsageEvent::getIngestSystem, ev.ingestSystem())
                    .eq(UsageEvent::getEventId, ev.eventId()).last("limit 1"));
            return dupResult(first);
        }

        // 8) 抵扣流水 + 来源明细（D2 两级，R15）
        DeductionRecord record = new DeductionRecord();
        record.setTenantId(tenantId);
        record.setEventId(e.getId());
        record.setProductId(product.getId());
        record.setCostAmount(Money.process(total));
        record.setChainJson(toJson(chain));
        record.setCreatedAt(arrive);
        recordMapper.insert(record);
        for (DeductionItem it : pendingItems) {
            it.setRecordId(record.getId());
            it.setCreatedAt(arrive);
            itemMapper.insert(it);
        }
        if (balanceUsedCents != 0) {
            AccountTransaction tx = new AccountTransaction();
            tx.setTenantId(tenantId); tx.setTxType("deduct"); tx.setRefType("usage_event");
            tx.setRefId(e.getId()); tx.setAmountCents(-balanceUsedCents);
            tx.setBalanceAfterCents(acc.getBalanceCents()); tx.setCreatedAt(arrive);
            transactionMapper.insert(tx);
        }
        // 9) 出站事件占位（T_BILLING_COMPLETED，消费联动在 MQ 阶段接入）
        LocalMessage msg = new LocalMessage();
        msg.setTopic(com.meterflow.common.event.Topics.T_BILLING_COMPLETED);
        msg.setBizKey(e.getId().toString());
        msg.setPayload(toJson(Map.of("eventId", e.getId(), "tenantId", tenantId, "cost", e.getCostAmount())));
        msg.setStatus("init"); msg.setRetryCount(0);
        localMessageMapper.insert(msg);

        return new IngestResultVO("accepted", e.getId(), Money.process(total).toPlainString(),
                chain, acc.getBalanceCents(), stage, null, null);
    }

    /** 资源包到期清零（A6，R33：状态迁移，@Scheduled 每分钟） */
    @Scheduled(fixedDelay = 60_000)
    public void expirePackages() {
        List<PackageInstance> expired = packageMapper.selectList(new LambdaQueryWrapper<PackageInstance>()
                .eq(PackageInstance::getStatus, "active")
                .lt(PackageInstance::getExpireDate, LocalDate.now()));
        for (PackageInstance p : expired) {
            p.setStatus("expired");
            packageMapper.updateById(p);
        }
    }

    /* ---------- 批量（AF1：≤500 条，逐条幂等） ---------- */
    public List<IngestResultVO> acceptBatch(KeyContext ctx, List<EventParam> events) {
        List<IngestResultVO> out = new ArrayList<>();
        for (EventParam ev : events) {
            try {
                out.add(accept(ctx, ev));
            } catch (BusinessException be) {
                out.add(new IngestResultVO("rejected", null, null, List.of(), null, null, null,
                        be.getCode() + " " + be.getMessage()));
            }
        }
        return out;
    }

    /* ---------- 私有工具 ---------- */

    private BalanceAccount lockAccount(Long tenantId) {
        BalanceAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<BalanceAccount>()
                .eq(BalanceAccount::getTenantId, tenantId).last("FOR UPDATE"));
        if (acc == null) {
            acc = new BalanceAccount();
            acc.setTenantId(tenantId); acc.setBalanceCents(0L);
            acc.setOverdraftCents(-10000L); acc.setStatus("normal"); acc.setVersion(0L);
            accountMapper.insert(acc);
        }
        return acc;
    }

    private String latestTopProduct(Long tenantId) {
        return suspensionLogMapper.selectList(new LambdaQueryWrapper<SuspensionLog>()
                        .eq(SuspensionLog::getTenantId, tenantId)
                        .eq(SuspensionLog::getAction, "warn")
                        .orderByDesc(SuspensionLog::getId).last("limit 1"))
                .stream().findFirst()
                .map(s -> s.getDetail().replaceFirst(".*topProduct=", ""))
                .orElse(null);
    }

    /** 分级停服判定（R22/R23）：转负→预警；达阈值→全停；回正→恢复 */
    private String judge(BalanceAccount acc, String currentProductName) {
        String stage = acc.getStatus();
        long b = acc.getBalanceCents();
        if (b >= 0 && !"normal".equals(stage)) {
            stage = "normal";
            suspensionLogMapper.insert(log(acc.getTenantId(), "restore", "余额回正，全部产品恢复（R23）"));
        } else if (b < 0 && "normal".equals(stage)) {
            stage = "warning";
            suspensionLogMapper.insert(log(acc.getTenantId(), "warn",
                    "余额转负，停用本月消费最高产品：topProduct=" + currentProductName));
        }
        if (b <= acc.getOverdraftCents() && !"suspended".equals(stage)) {
            stage = "suspended";
            suspensionLogMapper.insert(log(acc.getTenantId(), "suspend",
                    "余额低于透支阈值，全部产品停服并催缴（R22）"));
        }
        acc.setStatus(stage);
        accountMapper.updateById(acc);
        return stage;
    }

    private SuspensionLog log(Long tenantId, String action, String detail) {
        SuspensionLog s = new SuspensionLog();
        s.setTenantId(tenantId); s.setAction(action); s.setDetail(detail);
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    private DeductionItem item(Long tenantId, String type, Long sourceId, String name, BigDecimal amount) {
        DeductionItem it = new DeductionItem();
        it.setTenantId(tenantId); it.setSourceType(type); it.setSourceId(sourceId);
        it.setSourceName(name); it.setAmount(amount);
        return it;
    }

    private UsageEvent dupBySystemEvent(String system, String eventId) {
        return usageEventMapper.selectOne(new LambdaQueryWrapper<UsageEvent>()
                .eq(UsageEvent::getIngestSystem, system)
                .eq(UsageEvent::getEventId, eventId).last("limit 1"));
    }

    private IngestResultVO dupResult(UsageEvent origin) {
        DeductionRecord r = deductionRecordOf(origin.getId());
        List<ChainStep> chain = List.of();
        if (r != null) {
            try {
                chain = List.of(objectMapper().readValue(r.getChainJson(), ChainStep[].class));
            } catch (Exception ignored) { }
        }
        return new IngestResultVO("dup", origin.getId(), origin.getCostAmount().toPlainString(),
                chain, null, null, origin.getId(), "重复事件，返回首次处理结果（R4）");
    }

    private DeductionRecord deductionRecordOf(Long eventId) {
        return recordMapper.selectList(new LambdaQueryWrapper<DeductionRecord>()
                        .eq(DeductionRecord::getEventId, eventId).last("limit 1"))
                .stream().findFirst().orElse(null);
    }

    private BusinessException suspended() {
        return new BusinessException(BillingErrorCode.SUSPENDED);
    }

    /** 阶梯计价（R10）：seg=账期内累计分段（游标起算）；flat=档位单价；Token 单价按千单位换算 */
    private BigDecimal tierCost(String mode, String versionTier, String metricTier,
                                BigDecimal cursorStart, BigDecimal qty, BigDecimal flatPrice, long div) {
        String cfg = versionTier != null ? versionTier : metricTier;
        if ("flat".equals(mode) || cfg == null) {
            return Money.process(qty.multiply(flatPrice).divide(BigDecimal.valueOf(div), 6, RoundingMode.HALF_UP));
        }
        try {
            List<Map<String, Object>> segs = objectMapper().readValue(cfg,
                    objectMapper().getTypeFactory().constructCollectionType(List.class, Map.class));
            BigDecimal start = cursorStart, rest = qty, cost = BigDecimal.ZERO;
            for (Map<String, Object> seg : segs) {
                if (rest.signum() <= 0) break;
                BigDecimal from = new BigDecimal(String.valueOf(seg.get("from")));
                BigDecimal to = seg.get("to") == null ? null : new BigDecimal(String.valueOf(seg.get("to")));
                BigDecimal price = new BigDecimal(String.valueOf(seg.get("price")));
                if (to != null && start.compareTo(to) >= 0) continue;   // 游标已越过本档
                BigDecimal segFrom = from.max(start);
                BigDecimal usable = (to == null) ? rest : rest.min(to.subtract(segFrom));
                if (usable.signum() > 0) {
                    cost = cost.add(usable.multiply(price).divide(BigDecimal.valueOf(div), 6, RoundingMode.HALF_UP));
                    rest = rest.subtract(usable);
                }
            }
            return Money.process(cost);
        } catch (Exception e) {
            throw new IllegalStateException("阶梯配置解析失败", e);
        }
    }

    private ProductMetric metricOf(Long productId, String metricKey) {
        return metricMapper.selectOne(new LambdaQueryWrapper<ProductMetric>()
                .eq(ProductMetric::getProductId, productId)
                .eq(ProductMetric::getMetricKey, metricKey).last("limit 1"));
    }

    private String versionTier(PriceItem pi) { return pi.getTierJson(); }

    private Map<String, BigDecimal> metricsMap(List<MetricQty> metrics) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (MetricQty mq : metrics) map.put(mq.key(), mq.qty());
        return map;
    }

    private String toJson(Object o) {
        try { return objectMapper().writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String strip(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }

    private ObjectMapper objectMapper() { return JSON; }
}
