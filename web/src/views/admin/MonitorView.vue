<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import request from '@/api/request'
import { fmtCents, fmtCents as fc } from '@/utils/format'

/** 运营监控驾驶舱（管理端）：欠费/停服/预算告警/升级争议 跨租户聚合视图 */
const debts = ref<any[]>([])          // 欠费租户（含停服阶段）
const budgetAlerts = ref<any[]>([])  // 预算告警租户（预警/用尽）
const disputes = ref<any[]>([])      // 争议工单
const adjustments = ref<any[]>([])   // 待审调整单
const bills = ref<any[]>([])         // 待确认/结清账单
const loading = ref(false)

const tenants = ref<any[]>([])
function tenantName(id: any) { return tenants.value.find(t => t.id === id)?.name ?? `租户 #${id}` }

const suspended = computed(() => debts.value.filter(d => d.status === 'suspended'))
const escalated = computed(() => disputes.value.filter(d => d.isEscalated))
const pendingDisputes = computed(() => disputes.value.filter(d => d.status === 'pending'))
const pendingAdj = computed(() => adjustments.value.filter(a => a.stage === 'review1' || a.stage === 'review2'))
const pendingBills = computed(() => bills.value.filter(b => b.status === 'draft' || b.status === 'confirmed'))

/** 跨租户告警总清单：欠费/预算（数据态）与升级争议（流程态）合并为告警行 */
const alarmRows = computed(() => {
  const rows: any[] = []
  for (const d of debts.value) {
    rows.push({
      kind: d.status === 'suspended' ? '已停服' : '欠费预警',
      level: d.status === 'suspended' ? 'danger' : 'warning',
      tenantId: d.tenantId,
      detail: `余额 ${fmtCents(d.balanceCents)}（透支阈值 ${fmtCents(d.overdraftCents)}）`,
      action: '账单管理 → 催缴',
    })
  }
  for (const b of budgetAlerts.value) {
    rows.push({
      kind: `预算${b.level}`,
      level: b.level === '用尽' ? 'danger' : 'warning',
      tenantId: b.tenantId,
      detail: `消费 ${fmtCents(b.spentCents)} / 预算 ${fmtCents(b.budgetCents)}（${b.percent}%）`,
      action: '通知已自动发送',
    })
  }
  for (const d of escalated.value) {
    rows.push({
      kind: '争议升级',
      level: 'danger',
      tenantId: d.tenantId,
      detail: `工单 ${d.disputeNo} SLA 超时升级（${d.reason ?? ''}）`,
      action: '争议工单 → 处理',
    })
  }
  return rows.sort((a, b) => (a.level === 'danger' ? -1 : 1) - (b.level === 'danger' ? -1 : 1))
})

async function load() {
  loading.value = true
  try {
    const jobs = [
      request.get('/a/debts', { silent: true }).then((d: any) => { debts.value = d ?? [] }).catch(() => {}),
      request.get('/a/budget-alerts', { silent: true }).then((d: any) => { budgetAlerts.value = d ?? [] }).catch(() => {}),
      request.get('/a/disputes', { silent: true }).then((d: any) => { disputes.value = d ?? [] }).catch(() => {}),
      request.get('/a/adjustments', { silent: true }).then((d: any) => { adjustments.value = d ?? [] }).catch(() => {}),
      request.get('/a/bills', { silent: true }).then((d: any) => { bills.value = d ?? [] }).catch(() => {}),
      request.get('/a/tenants', { silent: true }).then((d: any) => { tenants.value = d?.list ?? d ?? [] }).catch(() => {}),
    ]
    await Promise.allSettled(jobs)
  } finally {
    loading.value = false
  }
}

let timer: number | undefined
onMounted(() => {
  load()
  timer = window.setInterval(load, 60_000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>运营监控驾驶舱</h2>
        <p class="muted">跨租户告警聚合：欠费/停服 · 预算 · 升级争议（60s 自动刷新）</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-skeleton v-if="loading && !debts.length && !disputes.length" :rows="4" animated />
    <template v-else>
      <div class="cards">
        <el-card><div class="k">欠费/停服租户</div><div class="v" :class="{ neg: debts.length > 0 }">{{ debts.length }}</div><div class="s">余额为负（R22/R23 分级）</div></el-card>
        <el-card><div class="k">预算告警租户</div><div class="v" :class="{ neg: budgetAlerts.some(b => b.level === '用尽') ? true : false }">{{ budgetAlerts.length }}</div><div class="s">达预警/用尽阈值</div></el-card>
        <el-card><div class="k">SLA 升级争议</div><div class="v" :class="{ neg: escalated.length > 0 }">{{ escalated.length }}</div><div class="s">超时升级（R36）</div></el-card>
        <el-card><div class="k">待审调整单</div><div class="v">{{ pendingAdj.length }}</div><div class="s">两级审批待办（I6）</div></el-card>
      </div>

      <el-card class="block">
        <template #header><b>跨租户告警清单</b>（{{ alarmRows.length }} 条，danger 优先置顶）</template>
        <el-table :data="alarmRows" size="small">
          <template #empty><el-empty description="当前无告警，一切正常" :image-size="60" /></template>
          <el-table-column label="级别" width="90">
            <template #default="{ row }"><el-tag size="small" :type="row.level">{{ row.kind }}</el-tag></template>
          </el-table-column>
          <el-table-column label="租户" width="160">
            <template #default="{ row }">{{ tenantName(row.tenantId) }}</template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="280" />
          <el-table-column prop="action" label="处置入口" min-width="150" />
        </el-table>
      </el-card>

      <div class="grid2">
        <el-card>
          <template #header><b>待受理争议（{{ pendingDisputes.length }}）</b></template>
          <el-table :data="pendingDisputes" size="small">
            <template #empty><el-empty description="无待受理争议" :image-size="40" /></template>
            <el-table-column prop="disputeNo" label="工单号" min-width="140" />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">{{ fmtCents(row.amountCents) }}</template>
            </el-table-column>
            <el-table-column label="SLA" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.isEscalated" size="small" type="danger">已升级</el-tag>
                <span v-else class="muted">{{ row.slaHours }}h</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card>
          <template #header><b>待审调整单（{{ pendingAdj.length }}）</b></template>
          <el-table :data="pendingAdj" size="small">
            <template #empty><el-empty description="无待审调整单" :image-size="40" /></template>
            <el-table-column prop="adjNo" label="单号" min-width="140" />
            <el-table-column prop="stage" label="阶段" width="90" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">{{ fc(row.amountCents) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </template>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.cards .k { color: #64748b; font-size: 13px; }
.cards .v { font-size: 26px; font-weight: 700; margin: 6px 0; }
.cards .s { color: #94a3b8; font-size: 12px; }
.v.neg { color: #dc2626; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 1100px) { .cards { grid-template-columns: 1fr 1fr; } .grid2 { grid-template-columns: 1fr; } }
</style>
