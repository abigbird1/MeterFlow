<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '@/api/request'
import { chartPalette } from '@/utils/theme'
import { fmtCents } from '@/utils/format'

/** 消费报表中心（05 §5.2 扩展）：月度趋势 / 产品 / 部门 / Key 四维聚合，一页纵览 */
const monthEl = ref<HTMLElement>()
const prodEl = ref<HTMLElement>()
const deptEl = ref<HTMLElement>()
const keyEl = ref<HTMLElement>()
let charts: echarts.ECharts[] = []
let distEvents: any[] = []

const overview = ref<any>(null)
const deptStats = ref<any[]>([])
const keyStats = ref<any[]>([])
const loading = ref(false)
/** 报表月份（缺省本月；产品分布/部门/Key 三维随月份联动查询） */
const reportMonth = ref<string>(new Date().toISOString().slice(0, 7))

const prodRows = computed(() => {
  const m = new Map<number, { productId: number; events: number; cost: number }>()
  for (const e of distEvents) {
    const cur = m.get(e.productId) ?? { productId: e.productId, events: 0, cost: 0 }
    cur.events += 1
    cur.cost += Number(e.costAmount ?? 0)
    m.set(e.productId, cur)
  }
  return [...m.values()].sort((a, b) => b.cost - a.cost)
})

const totalMonth = computed(() => Number(overview.value?.monthSpendAmount ?? 0))

function baseOption() {
  const pal = chartPalette()
  return {
    tooltip: { backgroundColor: pal.tooltipBg, borderColor: pal.tooltipBorder, textStyle: { color: pal.tooltipText } },
    grid: { left: 64, right: 20, top: 24, bottom: 40 },
    xAxis: { type: 'category', axisLabel: { color: pal.axisLabel } },
    yAxis: { type: 'value', axisLabel: { formatter: '¥{value}', color: pal.axisLabel }, splitLine: { lineStyle: { color: pal.splitLine } } },
  }
}

function renderAll() {
  const pal = chartPalette()
  const ov = overview.value ?? {}
  // ① 月度趋势
  const months = Object.keys(ov.monthlyTrend ?? {}).sort()
  charts[0]?.setOption({
    ...baseOption(),
    xAxis: { ...baseOption().xAxis, data: months },
    series: [{ type: 'bar', barWidth: 30, itemStyle: { color: '#6366f1', borderRadius: [4, 4, 0, 0] },
      data: months.map(m => Number(ov.monthlyTrend[m])) }],
  })
  // ② 产品分布（本月事件聚合）
  const prodAgg = new Map<number, number>()
  for (const e of distEvents) prodAgg.set(e.productId, (prodAgg.get(e.productId) ?? 0) + Number(e.costAmount ?? 0))
  charts[1]?.setOption({
    tooltip: { trigger: 'item', valueFormatter: (v: any) => '¥' + Number(v).toFixed(2),
      backgroundColor: pal.tooltipBg, borderColor: pal.tooltipBorder, textStyle: { color: pal.tooltipText } },
    legend: { bottom: 0, textStyle: { color: pal.axisLabel, fontSize: 11 } },
    series: [{ type: 'pie', radius: ['40%', '66%'], center: ['50%', '44%'],
      label: { show: false },
      data: [...prodAgg.entries()].map(([pid, amt]) => ({ name: `产品 #${pid}`, value: Number(amt.toFixed(2)) })) }],
  })
  // ③ 部门排名（横向条形）
  const deptRows = [...deptStats.value].sort((a, b) => a.costCents - b.costCents)
  charts[2]?.setOption({
    ...baseOption(),
    grid: { left: 90, right: 30, top: 16, bottom: 30 },
    xAxis: { ...baseOption().xAxis, type: 'value' },
    yAxis: { type: 'category', data: deptRows.map(r => r.deptName), axisLabel: { color: pal.axisLabel } },
    series: [{ type: 'bar', barWidth: 18, itemStyle: { color: '#0d9488', borderRadius: [0, 4, 4, 0] },
      data: deptRows.map(r => Number((r.costCents / 100).toFixed(2))) }],
  })
  // ④ Key 排名（横向条形）
  const keyRows = [...keyStats.value].filter(r => r.costCents > 0)
    .sort((a, b) => a.costCents - b.costCents).slice(-8)
  charts[3]?.setOption({
    ...baseOption(),
    grid: { left: 90, right: 30, top: 16, bottom: 30 },
    xAxis: { ...baseOption().xAxis, type: 'value' },
    yAxis: { type: 'category', data: keyRows.map(r => `Key#${String(r.keyId).slice(-4)}`), axisLabel: { color: pal.axisLabel } },
    series: [{ type: 'bar', barWidth: 18, itemStyle: { color: '#f59e0b', borderRadius: [0, 4, 4, 0] },
      data: keyRows.map(r => Number((r.costCents / 100).toFixed(2))) }],
  })
}

async function load() {
  loading.value = true
  try {
    overview.value = await request.get('/t/overview')
    deptStats.value = (await request.get('/t/depts/stats', { params: { period: reportMonth.value } })) ?? []
    keyStats.value = (await request.get('/t/keys/stats', { params: { period: reportMonth.value } })) ?? []
    const d: any = await request.get('/t/usage-events', { params: { pageNum: 1, pageSize: 100 } })
    distEvents = (d.list ?? []) as any[]
    await nextTick()
    const els = [monthEl.value, prodEl.value, deptEl.value, keyEl.value]
    els.forEach((el, i) => { if (el && !charts[i]) charts[i] = echarts.init(el) })
    renderAll()
  } finally {
    loading.value = false
  }
}

function onResize() { charts.forEach(c => c?.resize()) }
async function onMonthChange() {
  loading.value = true
  try {
    deptStats.value = (await request.get('/t/depts/stats', { params: { period: reportMonth.value } })) ?? []
    keyStats.value = (await request.get('/t/keys/stats', { params: { period: reportMonth.value } })) ?? []
    renderAll()
  } finally {
    loading.value = false
  }
}
const onThemeChange = () => { if (overview.value) renderAll() }
window.addEventListener('mf-theme-change', onThemeChange)
onMounted(async () => {
  window.addEventListener('resize', onResize)
  await load()
})
onBeforeUnmount(() => {
  window.removeEventListener('mf-theme-change', onThemeChange)
  window.removeEventListener('resize', onResize)
  charts.forEach(c => c?.dispose())
  charts = []
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>消费报表中心</h2>
        <p class="muted">月度趋势 / 产品 / 部门 / Key 四维聚合 · 数据实时来自计费明细（成员仅本部门口径）</p>
      </div>
    </div>

    <el-skeleton v-if="loading && !overview" :rows="5" animated />
    <template v-else>
      <el-card class="block">
        <template #header>
          <div class="flex-between">
            <b>汇总</b>
            <el-date-picker v-model="reportMonth" type="month" value-format="YYYY-MM" :clearable="false"
              placeholder="选择报表月份" style="width: 140px" @change="onMonthChange" />
          </div>
        </template>
        <div class="flex" style="gap: 40px; font-size: 13px">
          <span>本月消费 <b style="font-size: 18px">{{ fmtCents(totalMonth * 100) }}</b></span>
          <span>本月事件 <b>{{ overview?.eventCount ?? 0 }}</b></span>
          <span v-if="overview?.lastMonthSpendAmount">上月同期 <b>{{ fmtCents(Number(overview.lastMonthSpendAmount) * 100) }}</b></span>
        </div>
      </el-card>

      <div class="grid2">
        <el-card><template #header><b>月度消费趋势（近 6 个月）</b></template><div ref="monthEl" style="height: 260px" /></el-card>
        <el-card><template #header><b>产品消费分布（本月）</b></template><div ref="prodEl" style="height: 260px" /></el-card>
      </div>
      <div class="grid2">
        <el-card><template #header><b>部门消费排名（本月）</b></template><div ref="deptEl" style="height: 260px" /></el-card>
        <el-card><template #header><b>Key 消费排名（本月 Top8）</b></template><div ref="keyEl" style="height: 260px" /></el-card>
      </div>

      <el-card class="block">
        <template #header><b>产品消费明细（本月 Top）</b></template>
        <el-table :data="prodRows" size="small">
          <template #empty><el-empty description="本月暂无消费明细" :image-size="50" /></template>
          <el-table-column prop="productId" label="产品" width="100"><template #default="{ row }">产品 #{{ row.productId }}</template></el-table-column>
          <el-table-column prop="events" label="事件数" width="100" align="right" />
          <el-table-column label="消费金额" width="130" align="right">
            <template #default="{ row }"><b>{{ fmtCents(row.cost * 100) }}</b></template>
          </el-table-column>
          <el-table-column label="占比" min-width="240">
            <template #default="{ row }">
              <el-progress :percentage="totalMonth ? Math.min(100, Math.round(row.cost / totalMonth * 100)) : 0" :stroke-width="8" />
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
@import './page.css';
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
@media (max-width: 1100px) { .grid2 { grid-template-columns: 1fr; } }
</style>
