<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '@/api/request'
import { fmtCents } from '@/utils/format'

/** 用量与消费总览（05 §5.1.1）：统计卡 + 7 日消费趋势 + 产品消费分布 + 额度/资源包水位 */
const data = ref<any>(null)
const trendEl = ref<HTMLElement>()
const distEl = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let distChart: echarts.ECharts | null = null

const stageText: Record<string, string> = { normal: '正常', warning: '预警（停高消费产品）', suspended: '已停服' }

function renderTrend(byDay: Record<string, string>) {
  const days = Object.keys(byDay).sort()
  trendChart?.setOption({
    grid: { left: 48, right: 16, top: 24, bottom: 28 },
    tooltip: { trigger: 'axis', valueFormatter: (v: any) => '¥' + Number(v).toFixed(2) },
    xAxis: { type: 'category', data: days.map(d => d.slice(5)) },
    yAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
    series: [{ type: 'line', smooth: true, areaStyle: { opacity: 0.12 }, lineStyle: { color: '#0d9488' }, itemStyle: { color: '#0d9488' }, data: days.map(d => Number(byDay[d])) }],
  })
}

function renderDist(events: any[]) {
  // 产品消费分布：事件级数据前端聚合（真实明细，无独立聚合接口）
  const byProduct = new Map<number, number>()
  for (const e of events) byProduct.set(e.productId, (byProduct.get(e.productId) ?? 0) + Number(e.costAmount ?? 0))
  const rows = [...byProduct.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6)
  distChart?.setOption({
    tooltip: { trigger: 'item', valueFormatter: (v: any) => '¥' + Number(v).toFixed(2) },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      label: { show: false },
      data: rows.map(([pid, amt]) => ({ name: `产品 #${pid}`, value: Number(amt.toFixed(4)) })),
    }],
  })
}

async function load() {
  data.value = await request.get('/t/overview')
  await nextTick()
  if (trendEl.value && !trendChart) trendChart = echarts.init(trendEl.value)
  if (distEl.value && !distChart) distChart = echarts.init(distEl.value)
  renderTrend(data.value?.dailyTrend ?? {})
  const d: any = await request.get('/t/usage-events', { params: { pageNum: 1, pageSize: 100 } })
  renderDist((d.list ?? []) as any[])
}

function onResize() { trendChart?.resize(); distChart?.resize() }

onMounted(async () => {
  await load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose(); distChart?.dispose()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>用量与消费总览</h2>
        <p class="muted">实时逐笔计费 · 成员仅统计本部门（部门级数据隔离）</p>
      </div>
    </div>

    <el-skeleton v-if="!data" :rows="4" animated />

    <template v-else>
      <div class="cards">
        <el-card v-if="data.balanceCents != null" class="stat-card">
          <div class="k">当前余额</div>
          <div class="v" :class="{ neg: data.balanceCents < 0 }">{{ fmtCents(data.balanceCents) }}</div>
          <div class="s">
            <el-tag size="small" :type="data.stage === 'normal' ? 'success' : data.stage === 'warning' ? 'warning' : 'danger'">
              {{ stageText[data.stage] ?? data.stage }}
            </el-tag>
            <span v-if="data.stage !== 'normal'" class="muted" style="margin-left: 6px">充值回正即时恢复（R22/R23）</span>
          </div>
        </el-card>
        <el-card class="stat-card">
          <div class="k">本月消费</div>
          <div class="v">¥{{ data.monthSpendAmount }}</div>
          <div class="s">实时逐笔累计（日切快照定案前）</div>
        </el-card>
        <el-card class="stat-card">
          <div class="k">本月事件数</div>
          <div class="v">{{ data.eventCount }}</div>
          <div class="s">受理即计价，幂等重放不计费（R4）</div>
        </el-card>
        <el-card class="stat-card">
          <div class="k">生效资源包</div>
          <div class="v">{{ data.packages?.length ?? 0 }}</div>
          <div class="s">免费额度 {{ data.quotas?.length ?? 0 }} 项 · 赠送 {{ data.gifts?.length ?? 0 }} 笔</div>
        </el-card>
      </div>

      <div class="charts">
        <el-card>
          <template #header><b>近 7 日消费趋势</b></template>
          <div ref="trendEl" style="height: 260px" />
        </el-card>
        <el-card>
          <template #header><b>产品消费分布（本月事件聚合）</b></template>
          <div ref="distEl" style="height: 260px" />
        </el-card>
      </div>

      <el-card class="block">
        <template #header><b>免费额度 / 赠送额度 / 生效资源包</b></template>
        <el-empty v-if="!data.quotas?.length && !data.gifts?.length && !data.packages?.length" description="暂无额度与资源包" :image-size="60" />
        <div v-for="q in data.quotas ?? []" :key="'q' + q.id" class="quota">
          <span class="tag-icon">🎟️ 免费额度 #{{ q.metricId }}</span>
          <el-progress :percentage="q.totalQuota ? Math.round(q.remaining / q.totalQuota * 100) : 0" style="flex: 1" />
          <span class="muted">{{ q.remaining }} / {{ q.totalQuota }}</span>
        </div>
        <div v-for="g in data.gifts ?? []" :key="'g' + g.id" class="quota">
          <span class="tag-icon gift">🎁 {{ g.name }}</span>
          <span class="muted" style="flex: 1">剩 {{ g.remaining }} · {{ g.expireDate }} 到期（先赠后免，抵扣优先级 R13）</span>
        </div>
        <div v-for="p in data.packages ?? []" :key="'p' + p.id" class="quota">
          <span class="tag-icon">📦 {{ p.title }}</span>
          <el-progress :percentage="p.total ? Math.round(p.remaining / p.total * 100) : 0" style="flex: 1" />
          <span class="muted">{{ p.remaining }} {{ p.unit }} · {{ p.expireDate }} 到期</span>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
@import './page.css';
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card .k { color: #64748b; font-size: 13px; }
.stat-card .v { font-size: 24px; font-weight: 700; margin: 6px 0; }
.stat-card .s { color: #94a3b8; font-size: 12px; }
.v.neg { color: #dc2626; }
.charts { display: grid; grid-template-columns: 3fr 2fr; gap: 16px; margin-bottom: 16px; }
.quota { display: flex; align-items: center; gap: 12px; margin: 8px 0; font-size: 13px; }
.tag-icon { min-width: 150px; }
.gift { color: #7c3aed; }
@media (max-width: 1100px) { .cards { grid-template-columns: 1fr 1fr; } .charts { grid-template-columns: 1fr; } }
</style>
