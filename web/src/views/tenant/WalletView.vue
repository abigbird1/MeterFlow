<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const wallet = ref<any>(null)
const recharges = ref<any[]>([])
const amount = ref(20000)

const fmtCents = (c: any) => c == null ? '—' : '¥' + (c / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

async function load() {
  wallet.value = await request.get('/t/wallet')
  recharges.value = (await request.get('/t/wallet/recharges') as any).list ?? []
}

async function recharge() {
  const r: any = await request.post('/t/wallet/recharge', { amountCents: amount.value, method: 'alipay-demo' })
  ElMessage.success(r.recovered ? '充值成功，余额回正，停服已全部恢复！' : `充值成功，余额 ${fmtCents(r.balanceCents)}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div style="padding: 24px">
    <h2>余额与充值</h2>
    <p class="muted">有限透支 · 回正即时恢复（R16/R22/R23）</p>
    <el-alert v-if="wallet?.stage === 'warning'" type="warning" :closable="false"
      title="分级停服预警：余额为负，已停用本月消费最高产品；充值回正后全部恢复" />
    <el-alert v-if="wallet?.stage === 'suspended'" type="error" :closable="false"
      title="已全停服（METER_SUSPENDED）：余额低于透支阈值，充值回正后立即恢复" />
    <div class="grid" style="margin-top: 16px">
      <el-card>
        <div class="k">当前余额</div>
        <div class="v" :class="{ neg: wallet?.balanceCents < 0 }">{{ fmtCents(wallet?.balanceCents) }}</div>
        <div class="s">透支阈值 {{ fmtCents(wallet?.overdraftCents) }} · 阶段 {{ wallet?.stage }}</div>
        <div style="margin-top: 12px; display: flex; gap: 8px">
          <el-input-number v-model="amount" :min="100" :max="10000000" :step="10000" />
          <el-button type="primary" @click="recharge">充值（模拟支付）</el-button>
        </div>
      </el-card>
      <el-card>
        <template #header><b>充值记录</b></template>
        <el-table :data="recharges" size="small">
          <el-table-column prop="payNo" label="流水号" />
          <el-table-column label="金额"><template #default="{ row }">{{ fmtCents(row.amountCents) }}</template></el-table-column>
          <el-table-column prop="method" label="方式" />
          <el-table-column prop="status" label="状态" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.k { color: #64748b; font-size: 13px; }
.v { font-size: 26px; font-weight: 700; }
.v.neg { color: #dc2626; }
.s { color: #94a3b8; font-size: 12px; }
</style>
