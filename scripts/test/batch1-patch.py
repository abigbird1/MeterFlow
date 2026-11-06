# 批次一补丁：dept_sub 补写 / 到期任务 / Redis 判重 / 抢购预扣 / 权限校验
import re

def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    changed = False
    for old, new in pairs:
        if old in s:
            s = s.replace(old, new, 1)
            changed = True
    if changed:
        open(path, 'w', encoding='utf-8').write(s)
    return changed

BASE = 'server/'

# 1) finance: 幂等出账补写 dept_sub
p = BASE + 'meterflow-finance/src/main/java/com/meterflow/finance/service/BillService.java'
patch(p, [
("""        if (existing != null) return existing;   // R42 幂等：uk 冲突即返回已存在账单""",
 """        if (existing != null) { ensureDeptSub(existing); return existing; }"""),
("""        billMapper.insert(b);""",
 """        ensureDeptSub(b);
        billMapper.insert(b);"""),
("""    private void touchTask(String bizKey, String remark) {""",
 """    /** 部门小计补写（幂等：已有则跳过） */
    private void ensureDeptSub(Bill b) {
        if (billDeptSubMapper.selectCount(new LambdaQueryWrapper<com.meterflow.finance.entity.BillDeptSub>()
                .eq(com.meterflow.finance.entity.BillDeptSub::getBillId, b.getId())) > 0) return;
        var resp = billingClient.monthDeptSub(b.getTenantId(), b.getPeriod());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.get("data");
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            com.meterflow.finance.entity.BillDeptSub ds = new com.meterflow.finance.entity.BillDeptSub();
            ds.setBillId(b.getId());
            ds.setDeptName(String.valueOf(row.get("deptName")));
            ds.setAmountCents(BigDecimal.valueOf(Long.parseLong(String.valueOf(row.get("amountCents")))));
            ds.setCreatedAt(LocalDateTime.now());
            billDeptSubMapper.insert(ds);
        }
    }

    private void touchTask(String bizKey, String remark) {"""),
])

# 2) billing: 到期清零任务
p = BASE + 'meterflow-billing/src/main/java/com/meterflow/billing/service/MeteringService.java'
patch(p, [
("import org.springframework.stereotype.Service;",
 "import org.springframework.scheduling.annotation.Scheduled;\nimport org.springframework.stereotype.Service;"),
("""    /* ---------- 批量（AF1：≤500 条，逐条幂等） ---------- */""",
 """    /** 资源包到期清零（A6，R33：状态迁移，@Scheduled 每分钟） */
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

    /* ---------- 批量（AF1：≤500 条，逐条幂等） ---------- */"""),
])
p = BASE + 'meterflow-billing/src/main/java/com/meterflow/billing/BillingApplication.java'
patch(p, [
("import org.springframework.boot.autoconfigure.SpringBootApplication;",
 "import org.springframework.boot.autoconfigure.SpringBootApplication;\nimport org.springframework.scheduling.annotation.EnableScheduling;"),
("@SpringBootApplication", "@SpringBootApplication\n@EnableScheduling"),
])

# 3) billing: Redis 判重前置（ObjectProvider 降级）
p = BASE + 'meterflow-billing/pom.xml'
patch(p, [
("    <dependency>\n      <groupId>org.springframework.cloud</groupId>\n      <artifactId>spring-cloud-starter-openfeign</artifactId>\n    </dependency>",
 "    <dependency>\n      <groupId>org.springframework.cloud</groupId>\n      <artifactId>spring-cloud-starter-openfeign</artifactId>\n    </dependency>\n    <dependency>\n      <groupId>org.springframework.boot</groupId>\n      <artifactId>spring-boot-starter-data-redis</artifactId>\n    </dependency>"),
])
p = BASE + 'meterflow-billing/src/main/resources/application.yml'
patch(p, [("mybatis-plus:", "  data:\n    redis:\n      host: 127.0.0.1\n      port: 6379\n\nmybatis-plus:")])
p = BASE + 'meterflow-billing/src/main/java/com/meterflow/billing/service/MeteringService.java'
patch(p, [
("    private final LocalMessageMapper localMessageMapper;",
 "    private final LocalMessageMapper localMessageMapper;\n    private final org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider;"),
("""        // 1) 双保险判重（R3）：库内唯一索引兜底；Redis SETNX 前置在 Redis 环境接入后启用（AD-T5 待办）""",
 """        // 1) 双保险判重（R3/I1）：第一层 Redis SETNX 前置（B1，无 Redis 自动降级）；第二层库内唯一索引
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
"""),
])

# 4) equity: 抢购 Redis 预扣（降级 DB）
p = BASE + 'meterflow-equity/pom.xml'
patch(p, [
("    <dependency>\n      <groupId>org.springframework.cloud</groupId>\n      <artifactId>spring-cloud-starter-openfeign</artifactId>\n    </dependency>",
 "    <dependency>\n      <groupId>org.springframework.cloud</groupId>\n      <artifactId>spring-cloud-starter-openfeign</artifactId>\n    </dependency>\n    <dependency>\n      <groupId>org.springframework.boot</groupId>\n      <artifactId>spring-boot-starter-data-redis</artifactId>\n    </dependency>"),
])
p = BASE + 'meterflow-equity/src/main/resources/application.yml'
patch(p, [("mybatis-plus:", "  data:\n    redis:\n      host: 127.0.0.1\n      port: 6379\n\nmybatis-plus:")])
p = BASE + 'meterflow-equity/src/main/java/com/meterflow/equity/service/OrderService.java'
patch(p, [
("import org.springframework.scheduling.annotation.Scheduled;",
 "import org.springframework.data.redis.core.StringRedisTemplate;\nimport org.springframework.scheduling.annotation.Scheduled;"),
("    private final BillingCallbackClient billingCallbackClient;",
 """    private final BillingCallbackClient billingCallbackClient;
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
    }"""),
("""        if (locked == 0) throw new BusinessException(EquityErrorCode.SOLD_OUT);""",
 """        if (locked == 0) { redisRelease(goodsId2(goodsId), qty); throw new BusinessException(EquityErrorCode.SOLD_OUT); }"""),
("""    private void release(GoodsOrder o) {
        Goods g = goodsMapper.selectById(o.getGoodsId());
        if (g != null) {
            g.setSold(Math.max(0, g.getSold() - o.getQty()));
            goodsMapper.updateById(g);
        }
        stock(o.getGoodsId(), o.getOrderNo(), o.getQty(), "release");
    }""",
 """    private void release(GoodsOrder o) {
        Goods g = goodsMapper.selectById(o.getGoodsId());
        if (g != null) {
            g.setSold(Math.max(0, g.getSold() - o.getQty()));
            goodsMapper.updateById(g);
        }
        redisRelease(o.getGoodsId(), o.getQty());
        stock(o.getGoodsId(), o.getOrderNo(), o.getQty(), "release");
    }"""),
])
p = BASE + 'meterflow-equity/src/main/java/com/meterflow/equity/service/OrderService.java'
s = open(p, encoding='utf-8').read()
s = s.replace('redisRelease(goodsId2(goodsId), qty)', 'redisRelease(goodsId, qty)')
open(p, 'w', encoding='utf-8').write(s)

# 5) A5: finance 账单操作权限校验
p = BASE + 'meterflow-finance/src/main/java/com/meterflow/finance/controller/FinanceAdminController.java'
patch(p, [
("""    private Me me(String authorization) {
        Me me = identityService.require(authorization);
        if (me == null) throw new BusinessException("GWY-0401", "登录已失效，请重新登录");
        return me;
    }""",
 """    private Me me(String authorization) {
        Me me = identityService.require(authorization);
        if (me == null) throw new BusinessException("GWY-0401", "登录已失效，请重新登录");
        return me;
    }

    /** A5：账单操作权限（6.2 矩阵：财务/管理员） */
    private Me requireFinance(String authorization) {
        Me me = me(authorization);
        if (!"fin".equals(me.role()) && !"sadmin".equals(me.role())) {
            throw new BusinessException("GWY-0403", "账单操作岗为财务/管理员");
        }
        return me;
    }"""),
])
s = open(p, encoding='utf-8').read()
for frag in [
    ('public Result<Bill> confirm(@RequestHeader("Authorization") String authorization,', '        requireFinance(authorization);'),
    ('public Result<Bill> settle(@RequestHeader("Authorization") String authorization,', '        requireFinance(authorization);'),
    ('public Result<Bill> monthlyBill(@RequestHeader("Authorization") String authorization,', '        requireFinance(authorization);'),
]:
    idx = s.find(frag)
    if idx >= 0:
        # 在方法体第一行前插入校验：找到该方法签名行结尾
        end = s.find('\n', idx)
        brace = s.find('{', end)
        s = s[:brace+1] + '\n        requireFinance(authorization);' + s[brace+1:]
open(p, 'w', encoding='utf-8').write(s)
print('batch1-patch-done')
