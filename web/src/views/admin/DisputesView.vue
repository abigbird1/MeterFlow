<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { fmtCents, fmtTime, STATUS_TEXT, statusType } from '@/utils/format'

/** 争议工单（05 §6.5）：受理 → 代查（R37 留痕）→ 回复 → 补偿（审批制 R38）→ 结案；SLA 超时升级（R36） */
const disputes = ref<any[]>([])
const loading = ref(false)

// 工单详情抽屉（含代查所得账单概览）
const drawer = ref(false)
const current = ref<any>(null)
const proxyBills = ref<any[] | null>(null)

// 补偿对话框
const compDialog = ref(false)
const compForm = reactive({ type: 'grant', amountCents: 0, note: '' })
const compTarget = ref<any>(null)

// 回复对话框
const replyDialog = ref(false)
const replyForm = reactive({ content: '' })
const replyTarget = ref<any>(null)

async function load() {
  loading.value = true
  try { disputes.value = (await request.get('/a/disputes')) ?? [] } finally { loading.value = false }
}

async function openDetail(d: any) {
  drawer.value = true
  proxyBills.value = null
  current.value = (await request.get(`/a/disputes/${d.disputeNo}`)) ?? d
}

async function accept(d: any) {
  await request.post(`/a/disputes/${d.disputeNo}/accept`)
  ElMessage.success('已受理（审计留痕：操作人/时间/动作/对象）')
  await load()
  if (current.value?.disputeNo === d.disputeNo) current.value.status = 'processing'
}

async function openProxy(d: any) {
  // 代查（R37）：客服代查租户账单概览，调用留审计四要素
  const bills = (await request.post(`/a/disputes/${d.disputeNo}/proxy-query`, { scope: 'bills' })) as any[]
  proxyBills.value = bills ?? []
  ElMessage.success('代查完成（已留痕：操作人/时间/动作/对象）')
}

function openComp(d: any) {
  compTarget.value = d
  compForm.type = 'grant'
  compForm.amountCents = Number(d.amountCents ?? 0)
  compForm.note = '客服核验后补偿'
  compDialog.value = true
}

async function submitComp() {
  if (compForm.amountCents <= 0) { ElMessage.warning('请填写补偿金额'); return }
  const r: any = await request.post(`/a/disputes/${compTarget.value.disputeNo}/compensate`, { ...compForm })
  ElMessage.success(`补偿已生效：${r.compensation ?? '赠送额度单已发放'}`)
  compDialog.value = false
  drawer.value = false
  await load()
}

function openReply(d: any) {
  replyTarget.value = d
  replyForm.content = ''
  replyDialog.value = true
}

async function submitReply() {
  if (!replyForm.content.trim()) { ElMessage.warning('请填写回复内容'); return }
  await request.post(`/a/disputes/${replyTarget.value.disputeNo}/reply`, { content: replyForm.content.trim() })
  ElMessage.success('已回复（站内通知触达租户）')
  replyDialog.value = false
  await load()
}

async function close(d: any) {
  await ElMessageBox.confirm(`确认结案工单 ${d.disputeNo}？结案后不可再操作。`, '结案确认', { type: 'warning' })
  await request.post(`/a/disputes/${d.disputeNo}/close`)
  ElMessage.success('工单已结案')
  drawer.value = false
  await load()
}

/** SLA 超时扫描手动触发（自动任务每 5 分钟执行一次，R36） */
const slaScanning = ref(false)
async function slaEscalate() {
  slaScanning.value = true
  try {
    const n: any = await request.post('/a/tasks/sla-escalate')
    ElMessage.success(n > 0 ? `本次扫描升级 ${n} 单超期工单（已通知租户）` : '无超期工单，全部正常')
    await load()
  } finally {
    slaScanning.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>争议工单处理</h2>
        <p class="muted">受理 → 代查（R37 留痕）→ 回复/补偿（审批制 R38）→ 结案；SLA 超时自动升级（R36，每 5 分钟扫描）</p>
      </div>
      <el-button :loading="slaScanning" @click="slaEscalate">立即扫描 SLA 超时</el-button>
    </div>
    <el-card class="block">
      <el-table :data="disputes" size="small" v-loading="loading" @row-click="openDetail" style="cursor: pointer">
        <template #empty><el-empty description="暂无争议工单" :image-size="60" /></template>
        <el-table-column prop="disputeNo" label="工单号" min-width="150" />
        <el-table-column prop="billNo" label="关联账单" min-width="100" />
        <el-table-column prop="reason" label="申诉理由" min-width="180" show-overflow-tooltip />
        <el-table-column label="争议金额" align="right" width="110">
          <template #default="{ row }">{{ fmtCents(row.amountCents) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="SLA" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isEscalated" size="small" type="danger">已升级</el-tag>
            <span v-else class="muted">{{ row.slaHours }}h</span>
          </template>
        </el-table-column>
        <el-table-column label="补偿结果" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.compensation || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" size="small" type="primary" @click.stop="accept(row)">受理</el-button>
            <template v-if="row.status === 'processing'">
              <el-button size="small" @click.stop="openProxy(row)">代查</el-button>
              <el-button size="small" @click.stop="openReply(row)">回复</el-button>
              <el-button size="small" type="success" @click.stop="openComp(row)">补偿</el-button>
              <el-button size="small" type="warning" plain @click.stop="close(row)">结案</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawer" :title="`工单详情 · ${current?.disputeNo ?? ''}`" size="560px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusType(current.status)">{{ STATUS_TEXT[current.status] ?? current.status }}</el-tag>
            <el-tag v-if="current.isEscalated" size="small" type="danger" style="margin-left: 6px">SLA 已升级</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关联账单">{{ current.billNo }}</el-descriptions-item>
          <el-descriptions-item label="争议金额">{{ fmtCents(current.amountCents) }}</el-descriptions-item>
          <el-descriptions-item label="申诉理由">{{ current.reason }}</el-descriptions-item>
          <el-descriptions-item label="补偿结果">{{ current.compensation || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(current.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">代查 · 该租户账单概览（R37，调用留审计四要素）</h4>
        <el-button v-if="!proxyBills" size="small" type="primary" plain @click="openProxy(current)">执行代查</el-button>
        <el-table v-else :data="proxyBills" size="small" border>
          <el-table-column prop="billNo" label="账单号" />
          <el-table-column prop="period" label="账期" width="90" />
          <el-table-column label="应付" align="right"><template #default="{ row }">{{ fmtCents(row.payableCents) }}</template></el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 20px; display: flex; gap: 8px">
          <el-button v-if="current.status === 'pending'" type="primary" @click="accept(current)">受理工单</el-button>
          <el-button v-if="current.status === 'processing'" @click="openReply(current)">回复租户</el-button>
          <el-button v-if="current.status === 'processing'" type="success" @click="openComp(current)">发起补偿</el-button>
          <el-button v-if="current.status === 'processing'" type="warning" plain @click="close(current)">结案</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="compDialog" :title="`发起补偿 · ${compTarget?.disputeNo ?? ''}`" width="460px">
      <el-form label-width="90px">
        <el-form-item label="补偿方式">
          <el-radio-group v-model="compForm.type">
            <el-radio-button value="grant">赠送额度单</el-radio-button>
            <el-radio-button value="balance">余额返还</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="补偿金额">
          <el-input-number v-model="compForm.amountCents" :min="1" :step="100" />
          <span class="muted" style="margin-left: 8px">{{ fmtCents(compForm.amountCents) }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="compForm.note" type="textarea" :rows="2" maxlength="120" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="补偿经审批制（R38）：赠送额度单直接发放；余额返还走调整单资金通道" />
      <template #footer>
        <el-button @click="compDialog = false">取消</el-button>
        <el-button type="primary" @click="submitComp">确认补偿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="replyDialog" :title="`回复租户 · ${replyTarget?.disputeNo ?? ''}`" width="460px">
      <el-input v-model="replyForm.content" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="回复内容将发送至租户站内通知" />
      <template #footer>
        <el-button @click="replyDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
.sec { margin: 18px 0 8px; }
</style>
