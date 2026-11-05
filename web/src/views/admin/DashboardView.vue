<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '@/api/request'
import { fmtCents } from '@/utils/format'

/** 数据看板（05 §6.1）：平台统计卡 + 近 7 日消费趋势（billing 聚合）+ 租户余额水位 */
const stats = ref<any>(null)
const tenants = ref<any[]>([])
const trendEl = ref<HTMLElement>()
const balanceEl = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let balanceChart: echarts.ECharts | null = null

async function load() {
  stats.value = await request.get('/a/dashboard')
  tenants.value = ((await request.get('/a/tenants')) as any) ?? []
  await nextTick()
  if (trendEl.value && !trendChart) trendChart = echarts.init(trendEl.value)
  if (balanceEl.value && !balanceChart) balanceChart = echarts.init(balanceEl.value)

  const trend = (stats.value?.trend ?? []) as any[]
  trendChart?.setOption({
    grid: { left: 60, right: 20, top: 30, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: any) => '¥' + Number(v).toFixed(2),
    },
    xAxis: { type: 'category', data: trend.map(t => String(t.date).slice(5)) },
    yAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
    series: [
      {
        name: '消费金额', type: 'line', smooth: true,
        areaStyle: { opacity: 0.12 }, itemStyle: { color: '#0d9488' },
        data: trend.map(t => Number(t.costCents) / 100),
      },
      {
        name: '事件数', type: 'bar', yAxisIndex: 0, barWidth: 12, itemStyle: { color: '#94a3b8', opacity: 0.35 },
        data: trend.map(t => t.eventCount),
      },
    ],
  })

  balanceChart?.setOption({
    grid: { left: 70, right: 20, top: 30, bottom: 28 },
    tooltip: { valueFormatter: (v: any) => '¥' + Number(v).toFixed(2) },
    xAxis: { type: 'category', data: tenants.value.map(t => t.name), axisLabel: { interval: 0 } },
    yAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
    series: [{
      type: 'bar', barWidth: 44,
      data: tenants.value.map(t => ({
        value: Number(t.balanceCents) / 100,
        itemStyle: { color: Number(t.balanceCents) < 0 ? '#dc2626' : '#0d9488' },
      })),
    }],
  })
}

function onResize() { trendChart?.resize(); balanceChart?.resize() }

onMounted(async () => {
  await load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose(); balanceChart?.dispose()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>数据看板</h2>
        <p class="muted">平台级统计 · 趋势数据实时来自 billing 事件聚合（billing 不可用时趋势降级为空）</p>
      </div>
    </div>

    <el-skeleton v-if="!stats" :rows="4" animated />
    <template v-else>
      <div class="cards">
        <el-card><div class="k">租户总数</div><div class="v">{{ stats.tenantCount }}</div></el-card>
        <el-card><div class="k">欠费租户</div><div class="v" :class="{ neg: stats.debtTenants > 0 }">{{ stats.debtTenants }}</div></el-card>
        <el-card><div class="k">审计日志条数</div><div class="v">{{ stats.auditCount }}</div><div class="s">敏感操作全留痕（P5）</div></el-card>
        <el-card>
          <div class="k">近 7 日平台消费</div>
          <div class="v">{{ fmtCents((stats.trend ?? []).reduce((s: number, t: any) => s + Number(t.costCents), 0)) }}</div>
          <div class="s">事件 {{ (stats.trend ?? []).reduce((s: number, t: any) => s + Number(t.eventCount), 0) }} 笔</div>
        </el-card>
      </div>

      <div class="charts">
        <el-card>
          <template #header><b>近 7 日平台消费趋势</b></template>
          <div ref="trendEl" style="height: 300px" />
        </el-card>
        <el-card>
          <template #header><b>租户余额（负值 = 欠费）</b></template>
          <div ref="balanceEl" style="height: 300px" />
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
.charts { display: grid; grid-template-columns: 3fr 2fr; gap: 16px; }
@media (max-width: 1100px) { .cards { grid-template-columns: 1fr 1fr; } .charts { grid-template-columns: 1fr; } }
</style>
