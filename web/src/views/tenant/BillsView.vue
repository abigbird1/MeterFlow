<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { fmtCents, fmtAmount, fmtTime, STATUS_TEXT, statusType, copyText } from '@/utils/format'

/** 账单与明细（05 §5.4）：本账期进度 + 历史账单（详情抽屉）+ 事件明细 + 异步导出（AF2） */
const progress = ref<any>(null)
const bills = ref<any[]>([])
const events = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 20, productId: undefined as number | undefined, range: [] as string[] })

// 账单详情抽屉
const drawer = ref(false)
const bundle = ref<any>(null)
const detailLoading = ref(false)

// 导出任务轮询
const exporting = ref(false)

async function loadBills() {
  const d: any = await request.get('/t/bills')
  bills.value = d ?? []
}

async function loadEvents() {
  loading.value = true
  try {
    const params: any = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.productId != null) params.productId = query.productId
    if (query.range?.length === 2) { params.startDate = query.range[0]; params.endDate = query.range[1] }
    const d: any = await request.get('/t/usage-events', { params })
    events.value = d.list ?? []
    total.value = d.total ?? 0
  } finally {
    loading.value = false
  }
}

async function load() {
  progress.value = await request.get('/t/bills/progress')
  await Promise.all([loadBills(), loadEvents()])
}

function resetQuery() {
  query.pageNum = 1
  query.productId = undefined
  query.range = []
  loadEvents()
}

async function openDetail(row: any) {
  drawer.value = true
  detailLoading.value = true
  try {
    bundle.value = await request.get(`/t/bills/${row.billNo}/detail`)
  } finally {
    detailLoading.value = false
  }
}

/** 异步导出（AF2）：创建任务 → 轮询至完成 → 拉取文件流下载 */
async function exportEvents() {
  exporting.value = true
  try {
    const task: any = await request.post('/t/exports', {
      taskType: 'events',
      startDate: query.range?.[0],
      endDate: query.range?.[1],
    })
    let done: any = null
    for (let i = 0; i < 10; i++) {
      await new Promise(r => setTimeout(r, 1500))
      const list: any[] = (await request.get('/t/exports')) ?? []
      done = list.find(t => String(t.id) === String(task.id))
      if (done && done.status !== 'pending') break
    }
    if (!done || done.status === 'failed') { ElMessage.error('导出任务失败，请稍后重试'); return }
    const blob = await request.get(`/t/exports/${done.id}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(blob as unknown as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `usage-events-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功（文件保留 7 天，可在任务列表重复下载）')
  } finally {
    exporting.value = false
  }
}

async function copyCost(row: any) {
  await copyText(String(row.costAmount))
  ElMessage.success('费用已复制')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>账单与明细</h2>
        <p class="muted">本账期进度（日切快照定案前）+ 历史账单（版本分段/部门小计）+ 事件级明细</p>
      </div>
      <el-button type="primary" plain :loading="exporting" @click="exportEvents">导出明细 CSV</el-button>
    </div>

    <el-card class="block">
      <template #header><b>本账期进度</b></template>
      <el-skeleton v-if="!progress" :rows="1" animated style="width: 320px" />
      <div v-else class="flex">
        <el-tag effect="dark" round>{{ progress.period }}</el-tag>
        <span class="stat">已消费 <b>{{ fmtCents(progress.spentAmountCents) }}</b></span>
        <span class="stat">事件 <b>{{ progress.eventCount }}</b> 笔</span>
      </div>
    </el-card>

    <el-card class="block">
      <template #header><b>历史账单</b>（点击行查看费用构成）</template>
      <el-table :data="bills" size="small" v-loading="!bills.length" @row-click="openDetail" style="cursor: pointer">
        <template #empty><el-empty description="暂无账单（月末出账任务生成）" :image-size="60" /></template>
        <el-table-column prop="billNo" label="账单号" min-width="110" />
        <el-table-column prop="period" label="账期" width="100" />
        <el-table-column label="原额" align="right"><template #default="{ row }">{{ fmtCents(row.originalCents) }}</template></el-table-column>
        <el-table-column label="调整" align="right">
          <template #default="{ row }">
            <span :class="{ neg: row.adjustCents < 0 }">{{ fmtCents(row.adjustCents) }}</span>
            <el-tag v-if="row.isAdjusted" size="small" type="warning" style="margin-left: 4px">有调整</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应付" align="right"><template #default="{ row }"><b>{{ fmtCents(row.payableCents) }}</b></template></el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="争议" width="70">
          <template #default="{ row }"><el-tag v-if="row.isDisputed" size="small" type="danger">申诉中</el-tag><span v-else class="muted">—</span></template>
        </el-table-column>
        <el-table-column label="结清时间" width="160"><template #default="{ row }">{{ fmtTime(row.settledAt) }}</template></el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>
        <div class="flex-between">
          <b>消费明细（事件级）</b>
          <div class="flex" style="gap: 8px">
            <el-input-number v-model="query.productId" placeholder="产品ID" :controls="false" style="width: 120px" size="small" />
            <el-date-picker v-model="query.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" size="small" style="width: 240px" />
            <el-button size="small" @click="resetQuery">重置</el-button>
          </div>
        </div>
      </template>
      <el-table :data="events" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无消费明细" :image-size="60" /></template>
        <el-table-column prop="eventId" label="事件ID" min-width="140" show-overflow-tooltip />
        <el-table-column label="到达时间" width="160"><template #default="{ row }">{{ fmtTime(row.arriveTime) }}</template></el-table-column>
        <el-table-column prop="deptName" label="部门" width="100">
          <template #default="{ row }">{{ row.deptName || '未分配' }}</template>
        </el-table-column>
        <el-table-column prop="productId" label="产品" width="80" />
        <el-table-column prop="priceVersion" label="计价版本" width="90" />
        <el-table-column label="费用" align="right" width="120">
          <template #default="{ row }">
            <span style="cursor: pointer" title="点击复制" @click="copyCost(row)">{{ fmtAmount(row.costAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div class="flex-between" style="margin-top: 12px">
        <span class="muted">共 {{ total }} 条（成员仅可见本部门明细）</span>
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" @change="loadEvents" />
      </div>
    </el-card>

    <!-- 账单详情抽屉：费用构成（原额+调整=应付）+ 版本分段 + 部门小计 -->
    <el-drawer v-model="drawer" :title="`账单详情 ${bundle?.bill?.billNo ?? ''}`" size="560px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="bundle">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="账期">{{ bundle.bill.period }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusType(bundle.bill.status)">{{ STATUS_TEXT[bundle.bill.status] ?? bundle.bill.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ fmtTime(bundle.bill.confirmedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结清时间">{{ fmtTime(bundle.bill.settledAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">费用构成（原额 + 调整 = 应付）</h4>
        <div class="calc">
          <div class="calc-item"><span>原额</span><b>{{ fmtCents(bundle.bill.originalCents) }}</b></div>
          <div class="calc-op">+</div>
          <div class="calc-item"><span>调整<span v-if="bundle.bill.isAdjusted" class="muted">（冲正/补偿）</span></span><b :class="{ neg: bundle.bill.adjustCents < 0 }">{{ fmtCents(bundle.bill.adjustCents) }}</b></div>
          <div class="calc-op">=</div>
          <div class="calc-item total"><span>应付</span><b>{{ fmtCents(bundle.bill.payableCents) }}</b></div>
        </div>

        <h4 class="sec">版本分段（R11：账期内调价分段计费）</h4>
        <el-table :data="bundle.sections" size="small" border>
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column label="区间" min-width="150">
            <template #default="{ row }">{{ row.rangeStart }} ~ {{ row.rangeEnd }}</template>
          </el-table-column>
          <el-table-column label="金额" align="right"><template #default="{ row }">{{ fmtCents(row.amountCents) }}</template></el-table-column>
        </el-table>

        <h4 class="sec">部门小计</h4>
        <el-table :data="bundle.deptSubs" size="small" border>
          <template #empty><el-empty description="暂无部门小计数据" :image-size="50" /></template>
          <el-table-column prop="deptName" label="部门" />
          <el-table-column label="金额" align="right"><template #default="{ row }">{{ fmtCents(row.amountCents) }}</template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
@import './page.css';
.calc { display: flex; align-items: center; gap: 12px; background: #f8fafc; border-radius: 8px; padding: 12px 16px; }
.calc-item { display: flex; flex-direction: column; gap: 2px; min-width: 90px; }
.calc-item span { color: #64748b; font-size: 12px; }
.calc-item.total b { color: #0d9488; font-size: 18px; }
.calc-op { color: #94a3b8; font-size: 18px; }
.sec { margin: 18px 0 8px; }
.neg { color: #dc2626; }
</style>
