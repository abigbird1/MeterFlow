<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const disputes = ref<any[]>([])
const detail = ref<any>(null)
const form = reactive({ billNo: 'B2026-11', amountCents: 1860, reason: '' })

const fmtCents = (c: any) => '¥' + (c / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

async function load() { disputes.value = await request.get('/t/disputes') }

async function openDetail(no: string) {
  const d: any = await request.get(`/t/disputes/${no}`)
  detail.value = { ...d.dispute, replies: d.replies }
}

async function submit() {
  if (!form.reason) { ElMessage.warning('请填写争议理由'); return }
  const d: any = await request.post('/t/disputes', { ...form })
  ElMessage.success(`申诉已提交（${d.disputeNo}），SLA ${d.slaHours}h`)
  form.reason = ''
  await load()
}

onMounted(load)
</script>

<template>
  <div style="padding: 24px">
    <h2>争议申诉</h2>
    <p class="muted">分级 SLA（&lt;1000 元 48h / ≥1000 元 24h，R36）· 补偿审批制（R38）· 已结清账单不可发起（R44）</p>
    <el-card style="margin: 12px 0">
      <template #header><b>发起申诉</b></template>
      <div style="display: flex; gap: 8px; flex-wrap: wrap">
        <el-input v-model="form.billNo" placeholder="账单号" style="width: 140px" />
        <el-input-number v-model="form.amountCents" :min="100" :step="1000" placeholder="争议金额(分)" />
        <el-input v-model="form.reason" placeholder="争议理由（R44：已出账/已确认可发起）" style="flex: 1" />
        <el-button type="primary" @click="submit">提交申诉</el-button>
      </div>
    </el-card>
    <el-card>
      <template #header><b>我的争议工单</b></template>
      <el-table :data="disputes" size="small" @row-click="openDetail" style="cursor: pointer">
        <el-table-column prop="disputeNo" label="工单号" />
        <el-table-column prop="billNo" label="账单" />
        <el-table-column label="金额"><template #default="{ row }">{{ fmtCents(row.amountCents) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" />
        <el-table-column label="SLA"><template #default="{ row }">{{ row.slaHours }}h{{ row.isEscalated ? ' · 已升级' : '' }}</template></el-table-column>
        <el-table-column prop="compensation" label="补偿" />
      </el-table>
    </el-card>
    <el-card v-if="detail" style="margin-top: 16px">
      <template #header><b>工单 {{ detail.disputeNo }}</b> — {{ detail.status }}</template>
      <div class="muted">理由：{{ detail.reason }}</div>
      <div v-for="r in detail.replies" :key="r.id" class="reply">
        <b>{{ r.replier }}</b>：{{ r.content }}
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.muted { color: #94a3b8; font-size: 12px; }
.reply { padding: 6px 0; border-bottom: 1px dashed #f1f5f9; font-size: 13px; }
</style>
