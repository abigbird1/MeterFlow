<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { fmtCents, fmtTime, STATUS_TEXT, statusType } from '@/utils/format'

/** 账单管理与催缴（05 §6.3）：日切 → 月末出账（幂等 R42）→ 确认 → 结清（模拟收款）→ 欠费催缴 */
const bills = ref<any[]>([])
const debts = ref<any[]>([])
const tenants = ref<any[]>([])
const loading = ref(false)

// 出账对话框（单租户）
const dialog = ref(false)
const form = reactive({ tenantId: 1001, period: '2026-11' })

// 全员出账（月末自动任务的手动补跑）
const allBilling = ref(false)
async function monthlyBillAll() {
  const period = new Date().toISOString().slice(0, 7)
  allBilling.value = true
  try {
    const r: any = await request.post('/a/tasks/monthly-bill-all', { period })
    const recon = r.recon ?? {}
    const reconText = recon.errors >= 0 ? ` · 全量复核差错 ${recon.errors} 笔` : ' · 复核未执行'
    const text = `全员出账完成（${r.period}）：租户 ${r.tenants} 户 · 新建 ${r.created} · 已存在 ${r.existed} · 失败 ${r.failed}${reconText}`
    if (r.failed > 0 || recon.errors > 0) ElMessage.warning(text)
    else ElMessage.success(text)
    await load()
  } finally {
    allBilling.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const b: any = await request.get('/a/bills')
    const d: any = await request.get('/a/debts')
    const t: any = await request.get('/a/tenants')
    bills.value = b ?? []
    debts.value = d ?? []
    tenants.value = t?.list ?? t ?? []
  } finally {
    loading.value = false
  }
}

function tenantName(id: any) { return tenants.value.find(t => t.id === id)?.name ?? `租户 #${id}` }

async function openDialog() {
  form.tenantId = tenants.value[0]?.id ?? 1001
  form.period = new Date().toISOString().slice(0, 7)
  dialog.value = true
}

async function monthlyBill() {
  const r: any = await request.post('/a/tasks/monthly-bill', { tenantId: form.tenantId, period: form.period })
  ElMessage.success(`出账完成：${r.billNo}（幂等 R42，重复出账返回已存在账单）`)
  dialog.value = false
  await load()
}

async function dailyCut() {
  const r: any = await request.post('/a/tasks/daily-cut')
  ElMessage.success(`日切完成：事件 ${r.eventCount} 笔，差异 A→B ${r.diffAb} / B→C ${r.diffBc}（${r.result}）`)
  await load()
}

async function confirmBill(b: any) {
  await request.post(`/a/bills/${b.billNo}/confirm`)
  ElMessage.success(`${b.billNo} 已确认`)
  await load()
}

async function settleBill(b: any) {
  await ElMessageBox.confirm(`确认已收到「${tenantName(b.tenantId)}」账单 ${b.billNo} 的款项并结清？`, '结清账单', { type: 'warning' })
  await request.post(`/a/bills/${b.billNo}/settle`)
  ElMessage.success(`${b.billNo} 已结清（模拟收款，账务只进不改 P2）`)
  await load()
}

async function urge(d: any) {
  const r: any = await request.post(`/a/debts/${d.tenantId}/urge`)
  ElMessage.success(`催缴单已生成并发送站内通知（余额 ${fmtCents(r.balanceCents)}）`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>账单管理与催缴</h2>
        <p class="muted">日切快照（00:05）→ 月末出账（幂等 R42）→ 确认 → 结清；账务只进不改（P2）</p>
      </div>
      <div style="display: flex; gap: 8px">
        <el-tooltip content="每日 00:05 由定时任务自动执行日切对账（幂等）；此按钮为手动补跑" placement="top">
          <el-button @click="dailyCut">执行日切任务</el-button>
        </el-tooltip>
        <el-tooltip content="对本月有用量事件的全部租户逐户出账（R17/R42 幂等）；月末最后一天 23:30 自动执行" placement="top">
          <el-button :loading="allBilling" @click="monthlyBillAll">全员出账</el-button>
        </el-tooltip>
        <el-button type="primary" @click="openDialog">单租户出账…</el-button>
      </div>
    </div>

    <el-tabs type="border-card">
      <el-tab-pane label="账单列表">
        <el-table :data="bills" size="small" v-loading="loading">
          <template #empty><el-empty description="暂无账单" :image-size="60" /></template>
          <el-table-column prop="billNo" label="账单号" min-width="100" />
          <el-table-column label="租户" min-width="130"><template #default="{ row }">{{ tenantName(row.tenantId) }}</template></el-table-column>
          <el-table-column prop="period" label="账期" width="90" />
          <el-table-column label="原额" align="right"><template #default="{ row }">{{ fmtCents(row.originalCents) }}</template></el-table-column>
          <el-table-column label="调整" align="right"><template #default="{ row }">{{ fmtCents(row.adjustCents) }}</template></el-table-column>
          <el-table-column label="应付" align="right"><template #default="{ row }"><b>{{ fmtCents(row.payableCents) }}</b></template></el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="确认时间" width="150"><template #default="{ row }">{{ fmtTime(row.confirmedAt) }}</template></el-table-column>
          <el-table-column label="结清时间" width="150"><template #default="{ row }">{{ fmtTime(row.settledAt) }}</template></el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'draft'" size="small" type="primary" @click="confirmBill(row)">确认</el-button>
              <el-button v-if="row.status === 'confirmed'" size="small" type="success" @click="settleBill(row)">结清</el-button>
              <span v-if="row.status === 'settled'" class="muted">已完成</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`欠费与催缴 (${debts.length})`">
        <el-alert type="warning" :closable="false" title="余额为负的租户（欠费清单实时来自 billing）；催缴发送站内通知并留痕" style="margin-bottom: 12px" />
        <el-table :data="debts" size="small" v-loading="loading">
          <template #empty><el-empty description="当前无欠费租户" :image-size="60" /></template>
          <el-table-column label="租户" min-width="140"><template #default="{ row }">{{ tenantName(row.tenantId) }}</template></el-table-column>
          <el-table-column label="欠费金额" align="right">
            <template #default="{ row }"><b class="neg">{{ fmtCents(row.balanceCents) }}</b></template>
          </el-table-column>
          <el-table-column label="停服阶段" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'suspended' ? 'danger' : 'warning'">{{ row.status === 'suspended' ? '已全停服' : '预警停产品' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" type="warning" plain @click="urge(row)">发送催缴</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog" title="月末出账（幂等 R42）" width="440px">
      <el-form label-width="80px">
        <el-form-item label="租户">
          <el-select v-model="form.tenantId" style="width: 100%">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账期">
          <el-date-picker v-model="form.period" type="month" value-format="YYYY-MM" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="monthlyBill">出账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
.neg { color: #dc2626; }
</style>
