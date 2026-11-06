#!/bin/bash
# MeterFlow 功能测试（可重复执行；依赖 5 服务运行于 8080~8084）
BASE=http://localhost:8080
RUN=$(date +%s)
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); echo "PASS $1"; }
bad(){ FAIL=$((FAIL+1)); echo "FAIL $1  ← $2"; }
jget(){ python -c "import sys,json;d=json.load(sys.stdin);print(str(d$1))" 2>/dev/null; }

# 数据重置：每轮从已知种子状态开始（根治跨轮时序）
mysql -uroot -p123456 --default-character-set=utf8mb4 < "$(dirname "$0")/../sql/z-data-demo.sql" 2>/dev/null
mysql -uroot -p123456 --default-character-set=utf8mb4 < "$(dirname "$0")/../sql/z2-billing-demo.sql" 2>/dev/null
mysql -uroot -p123456 --default-character-set=utf8mb4 < "$(dirname "$0")/../sql/z3-finance-demo.sql" 2>/dev/null
# 注册类测试账号修剪：仅保留最近 1 个 E2E 租户/成员（防多轮累积）
mysql -uroot -p123456 -e "DELETE FROM meterflow_platform.member WHERE email LIKE 'e2e-%@reg.cn' AND id NOT IN (SELECT * FROM (SELECT id FROM meterflow_platform.member WHERE email LIKE 'e2e-%@reg.cn' ORDER BY id DESC LIMIT 1) t);" 2>/dev/null
mysql -uroot -p123456 -e "DELETE FROM meterflow_platform.role_permission WHERE role_id IN (SELECT id FROM meterflow_platform.role WHERE tenant_id NOT IN (SELECT DISTINCT tenant_id FROM meterflow_platform.member) AND tenant_id IS NOT NULL); DELETE FROM meterflow_platform.role WHERE tenant_id NOT IN (SELECT DISTINCT tenant_id FROM meterflow_platform.member) AND tenant_id IS NOT NULL; DELETE FROM meterflow_platform.tenant WHERE name LIKE 'E2E-%' AND id NOT IN (SELECT DISTINCT tenant_id FROM meterflow_platform.member);" 2>/dev/null
echo "=== 探活 ==="
for p in 8081 8082 8083 8084 8080; do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$p/actuator/health)
  [ "$code" = "200" ] && ok "T01 服务 $p 存活" || bad "T01 服务 $p 存活" "http=$code"
done

echo "=== 认证 ==="
BAD=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" -d '{"email":"zhangwei@xinglan.com","password":"wrong","portal":"t"}')
echo "$BAD" | grep -q "GWY-0401" && ok "T02 错误口令被拒" || bad "T02 错误口令被拒" "$BAD"
LOGIN=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" -d '{"email":"zhangwei@xinglan.com","password":"demo","portal":"t"}')
TOKEN=$(echo "$LOGIN" | jget "['data']['token']")
PERMS=$(echo "$LOGIN" | jget "['data']['permissions'][0]")
[ -n "$TOKEN" ] && [ -n "$PERMS" ] && ok "T03 登录（权限点首项 $PERMS）" || bad "T03 登录" "$PERMS"
ME=$(curl -s http://localhost:8080/auth/me -H "Authorization: Bearer $TOKEN")
echo "$ME" | grep -q "张伟" && ok "T04 /auth/me" || bad "T04 /auth/me" "$ME"
CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/t/wallet)
[ "$CODE" = "401" ] && ok "T05 未登录 401" || bad "T05 未登录 401" "$CODE"

echo "=== 总览/钱包 ==="
OV=$(curl -s http://localhost:8080/t/overview -H "Authorization: Bearer $TOKEN")
echo "$OV" | grep -q "monthSpendAmount" && ok "T06 总览聚合" || bad "T06 总览聚合" "$OV"
W0=$(curl -s http://localhost:8080/t/wallet -H "Authorization: Bearer $TOKEN" | jget "['data']['balanceCents']")
RC=$(curl -s -X POST http://localhost:8080/t/wallet/recharge -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: test-rc-$RUN" -d '{"amountCents":20000,"method":"alipay-demo"}')
W1=$(curl -s http://localhost:8080/t/wallet -H "Authorization: Bearer $TOKEN" | jget "['data']['balanceCents']")
[ $((W1-W0)) = 20000 ] && ok "T07 充值入账(+200元)" || bad "T07 充值入账" "$W0→$W1"
LIM=$(curl -s -X POST http://localhost:8080/t/wallet/recharge -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"amountCents":99999999}')
echo "$LIM" | grep -q "BIL-4005" && ok "T08 充值限额(R40)" || bad "T08 充值限额" "$LIM"

echo "=== 计费链路（billing） ==="
KEY=$(curl -s -X POST http://localhost:8080/t/api-keys -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"ft-key","note":"ft"}' | jget "['data']['plainKey']")
BADKEY=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/ingest/events -H "X-API-Key: mfk-0000" -H "Content-Type: application/json" -d '{"ingestSystem":"ft","eventId":"ft-k1","productId":1002,"metrics":[{"key":"calls","qty":1}]}')
[ "$BADKEY" = "401" ] && ok "T09 无效 Key 401" || bad "T09 无效 Key" "$BADKEY"
E1=$(curl -s -X POST http://localhost:8080/ingest/events -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"ingestSystem\":\"ft\",\"eventId\":\"ft-$RUN-001\",\"productId\":1002,\"metrics\":[{\"key\":\"calls\",\"qty\":100}]}")
echo "$E1" | grep -q '"status":"accepted"' && ok "T10 事件受理(含抵扣链)" || bad "T10 事件受理" "$E1"
E2=$(curl -s -X POST http://localhost:8080/ingest/events -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"ingestSystem\":\"ft\",\"eventId\":\"ft-$RUN-001\",\"productId\":1002,\"metrics\":[{\"key\":\"calls\",\"qty\":100}]}")
echo "$E2" | grep -q '"status":"dup"' && ok "T11 幂等重放(R4)" || bad "T11 幂等重放" "$E2"
BT=$(curl -s -X POST http://localhost:8080/ingest/events/batch -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"events\":[{\"ingestSystem\":\"ft\",\"eventId\":\"ft-b1-$RUN\",\"productId\":1002,\"metrics\":[{\"key\":\"calls\",\"qty\":10}]},{\"ingestSystem\":\"ft\",\"eventId\":\"ft-$RUN-001\",\"productId\":1002,\"metrics\":[{\"key\":\"calls\",\"qty\":10}]}]}")
CNT=$(echo "$BT" | python -c "import sys,json;print(len(json.load(sys.stdin)['data']))")
DUPC=$(echo "$BT" | grep -o '"status":"dup"' | wc -l)
[ "$CNT" = "2" ] && [ "$DUPC" = "1" ] && ok "T12 批量上报(逐条幂等,AF1)" || bad "T12 批量上报" "$BT"

echo "=== 资源包（equity, S4） ==="
GID=$(curl -s -X POST http://localhost:8080/a/goods -H "Content-Type: application/json" -d '{"goodsType":"spec","name":"FT out pack","spec":"ft","productId":1001,"metricKey":"out_tok","priceCents":48000,"validDays":90,"totalStock":10,"perLimit":5,"orderLimit":1,"windowStart":"2026-11-01","windowEnd":"2026-11-30"}' | jget "['data']['id']")
ORD=$(curl -s -X POST http://localhost:8080/t/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"goodsId\":$GID,\"qty\":1}" | jget "['data']['orderNo']")
[ -n "$ORD" ] && ok "T13 下单锁库存" || bad "T13 下单" "$ORD"
PAY=$(curl -s -X POST http://localhost:8080/t/orders/$ORD/pay -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"method":"alipay-demo"}')
echo "$PAY" | grep -q '"status":"paid"' && ok "T14 模拟支付" || bad "T14 支付" "$PAY"
sleep 5
INST=$(curl -s http://localhost:8080/t/packages/mine -H "Authorization: Bearer $TOKEN")
echo "$INST" | grep -q "$ORD" && ok "T15 S4 实例发放(outbox relay)" || bad "T15 S4 实例发放" "未见 $ORD"
ORD2=$(curl -s -X POST http://localhost:8080/t/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"goodsId\":$GID,\"qty\":1}" | jget "['data']['orderNo']")
curl -s -X POST http://localhost:8080/t/orders/$ORD2/cancel -H "Authorization: Bearer $TOKEN" >/dev/null
ST=$(mysql -uroot -p123456 -N -e "SELECT status FROM meterflow_equity.goods_order WHERE order_no='$ORD2'" 2>/dev/null)
[ "$ST" = "canceled" ] && ok "T16 取消+库存释放" || bad "T16 取消" "$ST"

echo "=== 账单（finance） ==="
FT=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"wucai@meterflow.cn","password":"demo","portal":"a"}' | jget "['data']['token']")
MB1=$(curl -s -X POST http://localhost:8080/a/tasks/monthly-bill -H "Authorization: Bearer $FT" -H "Content-Type: application/json" -d '{"tenantId":1001,"period":"2026-12"}')
MB2=$(curl -s -X POST http://localhost:8080/a/tasks/monthly-bill -H "Authorization: Bearer $FT" -H "Content-Type: application/json" -d '{"tenantId":1001,"period":"2026-12"}')
echo "$MB2" | grep -q "\"billNo\"" && ok "T17 出账幂等(返回已存在,R42)" || bad "T17 出账幂等" "$MB2"
BILLNO=$(echo "$MB1" | jget "['data']['billNo']")
ST=$(curl -s http://localhost:8080/a/bills -H "Authorization: Bearer $FT" | jget "['data'][0]['status']")
[ "$ST" = "settled" ] && ok "T18 状态流转(settled 终态)" || bad "T18 状态流转" "$ST"

echo "=== 对账与冲正（两级审批 I6） ==="
mysql -uroot -p123456 -e "UPDATE meterflow_finance.recon_error SET status='todo' WHERE err_no='REC-20261102-07'" 2>/dev/null
FT=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"wucai@meterflow.cn","password":"demo","portal":"a"}' | jget "['data']['token']")
ADJ=$(curl -s -X POST http://localhost:8080/a/recon/errors/REC-20261102-07/adjustments -H "Authorization: Bearer $FT" -H "Content-Type: application/json" -d '{"adjType":"adjust_red","amountCents":2840,"reason":"ft-adjust"}' | jget "['data']['adjNo']")
[ -n "$ADJ" ] && ok "T19 发起冲正" || bad "T19 发起冲正" "$ADJ"
B0=$(curl -s http://localhost:8082/feign/billing/accounts/1001 | jget "['data']['balanceCents']")
WT=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"zhoushen@meterflow.cn","password":"demo","portal":"a"}' | jget "['data']['token']")
R1=$(curl -s -X POST http://localhost:8080/a/adjustments/$ADJ/review -H "Authorization: Bearer $WT" -H "Content-Type: application/json" -d '{"stage":1,"result":true}')
echo "$R1" | grep -q "review2" && ok "T20 初审→待复审" || bad "T20 初审" "$R1"
WT=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"zhoushen@meterflow.cn","password":"demo","portal":"a"}' | jget "['data']['token']")
R1B=$(curl -s -X POST http://localhost:8080/a/adjustments/$ADJ/review -H "Authorization: Bearer $WT" -H "Content-Type: application/json" -d '{"stage":2,"result":true}')
echo "$R1B" | grep -q "GWY-0403" && ok "T21a 周审无复审岗(岗位拒绝)" || bad "T21a 周审复审岗" "$R1B"
R1C=$(curl -s -X POST http://localhost:8080/a/adjustments/$ADJ/review -H "Authorization: Bearer $WT" -H "Content-Type: application/json" -d '{"stage":1,"result":true}')
echo "$R1C" | grep -q "FIN-4001" && ok "T21b 重复初审被拒(不在待初审)" || bad "T21b 重复初审" "$R1C"
R2=$(curl -s -X POST http://localhost:8080/a/adjustments/$ADJ/review -H "Authorization: Bearer $FT" -H "Content-Type: application/json" -d '{"stage":2,"result":true}')
B1=$(curl -s http://localhost:8082/feign/billing/accounts/1001 | jget "['data']['balanceCents']")
echo "$R2" | grep -q '"stage":"done"' && [ $((B1-B0)) = 2840 ] && ok "T22 复审生效+余额回补(+28.40)" || bad "T22 复审生效" "$R2/$B0→$B1"

echo "=== 争议/隔离/导出 ==="
DSP=$(curl -s -X POST http://localhost:8080/a/disputes/DSP20261103-01/compensate -H "Authorization: Bearer $(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"sunqian@meterflow.cn","password":"demo","portal":"a"}' | jget "['data']['token']")" -H "Content-Type: application/json" -d '{"type":"grant","amountCents":1860,"note":"ft-comp"}' | jget "['data']['compensation']")
echo "$DSP" | grep -q "赠送额度单" && ok "T23 争议补偿(审批制)" || bad "T23 争议补偿" "$DSP"
MT=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"email":"wangfang@xinglan.com","password":"demo","portal":"t"}' | jget "['data']['token']")
VIEW=$(curl -s "http://localhost:8080/t/usage-events?pageNum=1&pageSize=50" -H "Authorization: Bearer $MT")
echo "$VIEW" | grep -q "研发部" && ! echo "$VIEW" | grep -q "算法部" && ok "T24 成员部门隔离(仅研发部)" || bad "T24 部门隔离" "$VIEW"
EXP=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/t/exports -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"taskType":"events"}')
[ "$EXP" = "200" ] && ok "T25 异步导出(AF2)" || bad "T25 异步导出" "$EXP"

echo "=== 注册与账单详情（D1/05 §5.4） ==="
REG=$(curl -s -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" -d "{\"name\":\"E2E-$RUN\",\"email\":\"e2e-$RUN@reg.cn\",\"password\":\"e2e12345\"}")
echo "$REG" | grep -q '"role":"管理员"' && ok "T26 注册即开通(D1,自动登录)" || bad "T26 注册" "$REG"
RGTK=$(echo "$REG" | jget "['data']['token']")
ME2=$(curl -s http://localhost:8080/auth/me -H "Authorization: Bearer $RGTK")
echo "$ME2" | grep -q "\"role\":\"管理员\"" && ok "T27 注册租户 /auth/me" || bad "T27 /auth/me" "$ME2"
PROG=$(curl -s http://localhost:8080/t/bills/progress -H "Authorization: Bearer $TOKEN")
echo "$PROG" | grep -q "spentAmountCents" && ok "T28 本账期进度(分出参)" || bad "T28 进度" "$PROG"
DETAIL=$(curl -s "http://localhost:8080/t/bills/B2026-10/detail" -H "Authorization: Bearer $TOKEN")
echo "$DETAIL" | grep -q '"sections"' && echo "$DETAIL" | grep -q '"deptSubs"' && ok "T29 账单详情聚合(分段+部门小计)" || bad "T29 账单详情" "$DETAIL"

echo "=== 定时任务与限流（R17/R36/#7.3） ==="
mysql -uroot -p123456 -e "INSERT INTO meterflow_finance.dispute (id, dispute_no, tenant_id, bill_no, amount_cents, reason, status, sla_hours, sla_deadline, is_escalated, created_at)
SELECT (UNIX_TIMESTAMP()*1000+9), CONCAT('DSP-E2E-', UNIX_TIMESTAMP()), tenant_id, bill_no, 100, 'e2e sla', 'pending', 24, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0, NOW()
FROM meterflow_finance.dispute WHERE dispute_no='DSP20261103-01' LIMIT 1;" 2>/dev/null
ESC=$(curl -s -X POST http://localhost:8080/a/tasks/sla-escalate -H "Authorization: Bearer $FT" | jget "['data']")
[ -n "$ESC" ] && [ "$ESC" -ge 1 ] 2>/dev/null && ok "T30 SLA 超时升级(R36)" || bad "T30 SLA 升级" "$ESC"
mysql -uroot -p123456 -e "DELETE FROM meterflow_finance.dispute WHERE dispute_no LIKE 'DSP-E2E-%'; DELETE FROM meterflow_platform.notification WHERE title LIKE '%已升级%';" 2>/dev/null
MBA=$(curl -s -X POST http://localhost:8080/a/tasks/monthly-bill-all -H "Authorization: Bearer $FT" -H "Content-Type: application/json" -d '{"period":"2026-11"}')
echo "$MBA" | grep -q '"failed":0' && ok "T31 全员出账(幂等 R17/R42)" || bad "T31 全员出账" "$MBA"
BURST=$(python - <<'PYEOF'
import concurrent.futures, urllib.request, urllib.error, json
URL = "http://localhost:8080/ingest/events"
def hit(i):
    req = urllib.request.Request(URL, data=json.dumps({"ingestSystem":"x","eventId":f"e2e-burst-{i}","productId":1002,"metrics":[]}).encode(),
        headers={"Content-Type":"application/json","X-API-Key":"mfk-none"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as r: return r.status
    except urllib.error.HTTPError as e: return e.code
    except Exception: return "err"
with concurrent.futures.ThreadPoolExecutor(150) as ex:
    codes = list(ex.map(hit, range(600)))
print(429 in codes and "yes" or "no")
PYEOF
)
[ "$BURST" = "yes" ] && ok "T32 ingest 限流 429(#7.3,500 TPS)" || bad "T32 限流" "未触发 429"
sleep 2   # 等待 Sentinel 1s 窗口滑过，避免限流正确拦截后续用例

echo "=== 核心业务规则加固（R8/R22/R13/R31） ==="
W0=$(mysql -uroot -p123456 -N -e "SELECT balance_cents FROM meterflow_billing.balance_account WHERE tenant_id=1001;" 2>/dev/null)
# T33 R8 全量停服拒收：余额低于透支阈值 → 403 + BIL-4031(METER_SUSPENDED)；SQL 恢复原状
mysql -uroot -p123456 -e "UPDATE meterflow_billing.balance_account SET balance_cents=-20000, status='suspended' WHERE tenant_id=1001;" 2>/dev/null
SUSP=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/ingest/events -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"ingestSystem\":\"ft\",\"eventId\":\"ft-susp-$RUN\",\"productId\":1002,\"metrics\":[{\"key\":\"calls\",\"qty\":1}]}")
SUSPB=$(curl -s -X POST http://localhost:8080/ingest/events -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"ingestSystem\":\"ft\",\"eventId\":\"ft-susp2-$RUN\",\"productId\":1002,\"metrics\":[{\"key\":\"calls\",\"qty\":1}]}" | grep -o "METER_SUSPENDED" | head -1)
[ "$SUSP" = "403" ] && [ "$SUSPB" = "METER_SUSPENDED" ] && ok "T33 R8 停服拒收(403+METER_SUSPENDED)" || bad "T33 R8 停服" "http=$SUSP body=$SUSPB"
mysql -uroot -p123456 -e "UPDATE meterflow_billing.balance_account SET balance_cents=$W0, status='normal' WHERE tenant_id=1001;" 2>/dev/null
# T34 R22/R23 预警分级停服：warning 仅停最高消费产品（两产品恰好一拒一放）
mysql -uroot -p123456 -e "UPDATE meterflow_billing.balance_account SET balance_cents=-5000, status='warning' WHERE tenant_id=1001;" 2>/dev/null
R1=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/ingest/events -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"ingestSystem\":\"ft\",\"eventId\":\"ft-w1-$RUN\",\"productId\":1002,\"metrics\":[{\"key\":\"calls\",\"qty\":1}]}")
R2=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/ingest/events -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"ingestSystem\":\"ft\",\"eventId\":\"ft-w2-$RUN\",\"productId\":1001,\"metrics\":[{\"key\":\"calls\",\"qty\":1}]}")
{ [ "$R1" = "403" ] && [ "$R2" = "200" ]; } || { [ "$R1" = "200" ] && [ "$R2" = "403" ]; }
[ "$R1$R2" = "403200" -o "$R1$R2" = "200403" ] && ok "T34 R22 预警停最高消费产品(一拒一放)" || bad "T34 R22 分级" "1002=$R1 1001=$R2"
mysql -uroot -p123456 -e "UPDATE meterflow_billing.balance_account SET balance_cents=$W0, status='normal' WHERE tenant_id=1001;" 2>/dev/null
# T35 R13 抵扣优先级：赠送额度先于免费额度/余额扣减（留痕可查）
GB=$(mysql -uroot -p123456 -N -e "SELECT IFNULL(SUM(remaining),0) FROM meterflow_billing.gift_grant WHERE tenant_id=1001 AND metric_id=1101;" 2>/dev/null)
curl -s -X POST http://localhost:8080/a/grants -H "Content-Type: application/json" -d '{"tenantId":"1001","productMetric":"1001:1101","amount":1000000,"expireDate":"2027-12-31"}' >/dev/null
curl -s -X POST http://localhost:8080/ingest/events -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "{\"ingestSystem\":\"ft\",\"eventId\":\"ft-gift-$RUN\",\"productId\":1001,\"metrics\":[{\"key\":\"calls\",\"qty\":10}]}" >/dev/null
GIFTUSED=$(mysql -uroot -p123456 -N -e "SELECT COUNT(*) FROM meterflow_billing.deduction_item di JOIN meterflow_billing.deduction_record dr ON di.record_id=dr.id JOIN meterflow_billing.usage_event ue ON ue.id=dr.event_id WHERE ue.event_id='ft-gift-$RUN' AND di.source_type='gift';" 2>/dev/null)
[ "$GIFTUSED" -ge 1 ] 2>/dev/null && ok "T35 R13 赠送额度先行抵扣(留痕)" || bad "T35 R13 抵扣链" "gift_used=$GIFTUSED before=$GB"
# T36 R31 每人限购：第 perLimit+1 次下单被拒（EQY-4002）
GID2=$(curl -s -X POST http://localhost:8080/a/goods -H "Content-Type: application/json" -d '{"goodsType":"spec","name":"FT Limit Pack","spec":"e2e","productId":1001,"metricKey":"out_tok","priceCents":100,"validDays":30,"totalStock":10,"perLimit":2,"orderLimit":2,"windowStart":"2026-11-01","windowEnd":"2026-12-31","quantity":1000000,"unit":"Token"}' | jget "['data']['id']")
O1=$(curl -s -X POST http://localhost:8080/t/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"goodsId\":$GID2,\"qty\":1}" | jget "['data']['orderNo']")
O2=$(curl -s -X POST http://localhost:8080/t/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"goodsId\":$GID2,\"qty\":1}" | jget "['data']['orderNo']")
O3=$(curl -s -X POST http://localhost:8080/t/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"goodsId\":$GID2,\"qty\":1}")
echo "$O3" | grep -q "EQY-4002" && ok "T36 R31 每人限购(第3单被拒)" || bad "T36 R31 限购" "$O3"
curl -s -X POST http://localhost:8080/t/orders/$O1/pay -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"method":"alipay-demo"}' >/dev/null
curl -s -X POST http://localhost:8080/t/orders/$O2/pay -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"method":"alipay-demo"}' >/dev/null
mysql -uroot -p123456 -e "DELETE FROM meterflow_billing.package_instance WHERE source_order_no IN ('$O1','$O2'); DELETE FROM meterflow_equity.goods_order WHERE order_no IN ('$O1','$O2'); DELETE FROM meterflow_equity.goods WHERE id=$GID2;" 2>/dev/null

echo "========================"
echo "RESULT: PASS=$PASS FAIL=$FAIL"
