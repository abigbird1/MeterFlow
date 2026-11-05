<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { fmtCents, fmtTime, STATUS_TEXT, statusType } from '@/utils/format'

/** 对账中心（05 §6.4）：日切对账（R27）→ 差错 → 冲正调整单（R28）→ 两级审批：初审+复审，初审≠复审同岗（I6） */
const daily = ref<any[]>([])
const errors = ref<any[]>([])
const adjustments = ref<any[]>([])
const loading = ref(false)

// 发起冲正对话框
const adjDialog = ref(false)
const adjForm = reactive({ errNo: '', adjType: 'adjust_red', amountCents: 0, reason: '' })
const adjTarget = ref<any>(null)

async function load() {
  loading.value = true
  try {
    const d: any = await request.get('/a/recon/daily')
    const e: any = await request.get('/a/recon/errors')
    const a: any = await request.get('/a/adjustments')
    daily.value = d ?? []
    errors.value = e ?? []
    adjustments.value = a ?? []
  } finally {
    loading.value = false
  }
}

const ERR_TYPE_TEXT: Record<string, string> = {
  count_mismatch: '事件数不平', amount_mismatch: '金额不平', missing_c: 'C 表缺失', duplicate: '重复入账',
}

function openAdjust(e: any) {
  adjTarget.value = e
  adjForm.errNo = e.errNo
  adjForm.adjType = e.impactCents < 0 ? 'adjust_red' : 'adjust_blue'
  adjForm.amountCents = Math.abs(Number(e.impactCents ?? 0))
  adjForm.reason = `差错冲正（${ERR_TYPE_TEXT[e.errType] ?? e.errType}）`
  adjDialog.value = true
}

async function submitAdjust() {
  if (!adjForm.reason.trim()) { ElMessage.warning('请填写冲正理由'); return }
  await request.post(`/a/recon/errors/${adjForm.errNo}/adjustments`, { ...adjForm })
  ElMessage.success('调整单已生成，待审核员初审（I6 两级审批）')
  adjDialog.value = false
  await load()
}

async function manual(e: any) {
  await request.post(`/a/recon/errors/${e.errNo}/manual`)
  ElMessage.success('差错单已转人工处置')
  await load()
}

async function closeManual(e: any) {
  await ElMessageBox.confirm(`确认人工核验完成并关闭差错单 ${e.errNo}？`, '转人工处置完成', { type: 'warning' })
  await request.post(`/a/recon/errors/${e.errNo}/close-manual`)
  ElMessage.success('差错单已关闭（审计留痕）')
  await load()
}

async function review(a: any, stage: number, result: boolean) {
  if (!result) {
    await ElMessageBox.confirm(`驳回后将退回发起人重新处理，确定驳回调整单 ${a.adjNo}？`, '驳回确认', { type: 'warning' })
  }
  const r: any = await request.post(`/a/adjustments/${a.adjNo}/review`, { stage, result })
  if (!result) ElMessage.success('已驳回（留痕）')
  else ElMessage.success(stage === 1 ? '初审通过，待财务复审（初审≠复审同岗）' : `复审通过，已生效：${r.effectNote ?? '余额已回补'}`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>对账中心 · 差错与冲正</h2>
        <p class="muted">日切三方对账（R27）→ 差错 → 冲正调整单（R28）→ 两级审批：初审 ≠ 复审同岗（I6）</p>
      </div>
    </div>

    <el-tabs type="border-card" v-loading="loading">
      <el-tab-pane :label="`差错清单 (${errors.length})`">
        <el-table :data="errors" size="small">
          <template #empty><el-empty description="暂无差错" :image-size="60" /></template>
          <el-table-column prop="errNo" label="差错编号" min-width="150" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ ERR_TYPE_TEXT[row.errType] ?? row.errType }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column label="影响金额" align="right" width="110">
            <template #default="{ row }"><b :class="{ neg: Number(row.impactCents) < 0 }">{{ fmtCents(row.impactCents) }}</b></template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'todo' ? 'warning' : row.status === 'manual' ? 'danger' : 'success'">
                {{ row.status === 'todo' ? '待处理' : row.status === 'manual' ? '人工处置中' : row.status === 'adjusted' ? '已冲正' : '已关闭' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="adjNo" label="关联调整单" min-width="150" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'todo'" size="small" type="primary" @click="openAdjust(row)">发起冲正</el-button>
              <el-button v-if="row.status === 'todo'" size="small" @click="manual(row)">转人工</el-button>
              <el-button v-if="row.status === 'manual'" size="small" type="warning" @click="closeManual(row)">处置完成</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`调整单审批工作台 (${adjustments.length})`">
        <el-alert type="info" :closable="false" title="两级审批（I6）：初审与复审不得同岗；复审通过后资金调整才生效（P3）" style="margin-bottom: 12px" />
        <el-table :data="adjustments" size="small">
          <template #empty><el-empty description="暂无调整单" :image-size="60" /></template>
          <el-table-column prop="adjNo" label="调整单号" min-width="150" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.adjType === 'adjust_red' ? 'danger' : 'success'">
                {{ row.adjType === 'adjust_red' ? '红冲' : '蓝补' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" align="right" width="110">
            <template #default="{ row }"><b>{{ fmtCents(row.amountCents) }}</b></template>
          </el-table-column>
          <el-table-column prop="reason" label="理由" min-width="160" show-overflow-tooltip />
          <el-table-column prop="initiator" label="发起人" width="100" />
          <el-table-column label="审批进度" width="200">
            <template #default="{ row }">
              <el-steps :active="row.stage === 'done' ? 2 : row.stage === 'review2' ? 1 : 0" size="small" align-center style="width: 180px">
                <el-step :title="row.review1By ? `初审 ${row.review1By}` : '待初审'" />
                <el-step :title="row.review2By ? `复审 ${row.review2By}` : '待复审'" />
              </el-steps>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.stage)">{{ STATUS_TEXT[row.stage] ?? row.stage }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <template v-if="row.stage === 'review1'">
                <el-button size="small" type="primary" @click="review(row, 1, true)">初审通过</el-button>
                <el-button size="small" type="danger" plain @click="review(row, 1, false)">驳回</el-button>
              </template>
              <template v-else-if="row.stage === 'review2'">
                <el-button size="small" type="primary" @click="review(row, 2, true)">复审通过</el-button>
                <el-button size="small" type="danger" plain @click="review(row, 2, false)">驳回</el-button>
              </template>
              <span v-else-if="row.stage === 'done'" class="muted">{{ row.effectNote ?? '已生效' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`对账日报 (${daily.length})`">
        <el-table :data="daily" size="small">
          <template #empty><el-empty description="暂无对账日报，执行日切任务后生成" :image-size="60" /></template>
          <el-table-column prop="reconDate" label="对账日" width="110" />
          <el-table-column prop="eventCount" label="事件数" width="100" />
          <el-table-column label="A→B 差异" width="110">
            <template #default="{ row }"><el-tag size="small" :type="row.diffAb ? 'danger' : 'success'">{{ row.diffAb }}</el-tag></template>
          </el-table-column>
          <el-table-column label="B→C 差异" width="110">
            <template #default="{ row }"><el-tag size="small" :type="row.diffBc ? 'danger' : 'success'">{{ row.diffBc }}</el-tag></template>
          </el-table-column>
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 'ok' ? 'success' : 'danger'">{{ row.result === 'ok' ? '平账' : row.result }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="执行时间" min-width="150"><template #default="{ row }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="adjDialog" :title="`发起冲正 · ${adjForm.errNo}`" width="460px">
      <el-form label-width="90px">
        <el-form-item label="调整类型">
          <el-radio-group v-model="adjForm.adjType">
            <el-radio-button value="adjust_red">红冲（退回）</el-radio-button>
            <el-radio-button value="adjust_blue">蓝补（补收）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整金额">
          <el-input-number v-model="adjForm.amountCents" :min="1" :step="100" />
          <span class="muted" style="margin-left: 8px">{{ fmtCents(adjForm.amountCents) }}</span>
        </el-form-item>
        <el-form-item label="理由">
          <el-input v-model="adjForm.reason" type="textarea" :rows="2" maxlength="120" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" title="提交后需两级审批（初审+复审）通过才生效；红冲退余额、蓝补扣余额（P3 资金入口）" />
      <template #footer>
        <el-button @click="adjDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
.neg { color: #dc2626; }
</style>
