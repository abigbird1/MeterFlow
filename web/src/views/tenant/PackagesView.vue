<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { fmtCents, fmtTime, STATUS_TEXT, statusType } from '@/utils/format'

/** 资源包中心（05 §5.4）：商品抢购（三重限制 R31/R32，下单锁库存 15 分钟）+ 我的实例 + 抵扣留痕（R34） */
const hall = ref<any[]>([])
const mine = ref<any[]>([])
const loading = ref(false)

// 实例抵扣明细抽屉（D2 来源明细）
const drawer = ref(false)
const inst = ref<any>(null)
const ded = ref<any[]>([])
const dedTotal = ref(0)
const dedPage = ref(1)
const dedLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const h: any = await request.get('/t/packages')
    const m: any = await request.get('/t/packages/mine')
    hall.value = h ?? []
    mine.value = m ?? []
  } finally {
    loading.value = false
  }
}

async function buy(g: any) {
  await ElMessageBox.confirm(
    `确认以 ${fmtCents(g.priceCents)} 购买「${g.name}」×1？下单将锁定库存，15 分钟内未支付自动释放（R32）`,
    '确认下单', { confirmButtonText: '去支付（模拟）' }
  )
  const o: any = await request.post('/t/orders', { goodsId: g.id, qty: 1 })
  ElMessage.success(`下单成功（已锁库存），订单 ${o.orderNo}`)
  const paid: any = await request.post(`/t/orders/${o.orderNo}/pay`, { method: 'alipay-demo' })
  ElMessage.success(`支付成功 → 实例发放中（outbox 异步，S4）`)
  await load()
}

async function openDeductions(row: any) {
  inst.value = row
  drawer.value = true
  dedPage.value = 1
  await loadDeductions()
}

async function loadDeductions() {
  dedLoading.value = true
  try {
    const d: any = await request.get(`/t/package-instances/${inst.value.id}/deductions`, {
      params: { pageNum: dedPage.value, pageSize: 10 },
    })
    ded.value = d.list ?? []
    dedTotal.value = d.total ?? 0
  } finally {
    dedLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>资源包中心</h2>
        <p class="muted">限量抢购 · 三重限制（窗口/限购/库存）· 下单锁库存 15 分钟（R31/R32）</p>
      </div>
    </div>

    <div class="cards" v-loading="loading">
      <el-empty v-if="!hall.length" description="暂无在售资源包" style="grid-column: 1 / -1" />
      <el-card v-for="g in hall" :key="g.id" class="goods">
        <div class="b">{{ g.name }} <el-tag size="small">{{ g.goodsType === 'spec' ? '指定包' : '通用包' }}</el-tag></div>
        <div class="muted">{{ g.spec }} · 指标 {{ g.metricKey }}</div>
        <div class="price">{{ fmtCents(g.priceCents) }}</div>
        <div class="muted">库存 {{ g.sold }}/{{ g.totalStock }} · 限购 {{ g.perLimit }}/人 · 窗口 {{ g.windowStart }} ~ {{ g.windowEnd }}</div>
        <el-progress :percentage="g.totalStock ? Math.round(g.sold / g.totalStock * 100) : 0" :stroke-width="6" style="margin: 8px 0" />
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span class="muted">有效期 {{ g.validDays }} 天</span>
          <el-button type="primary" :disabled="g.sold >= g.totalStock || g.status !== 'on'" @click="buy(g)">
            {{ g.sold >= g.totalStock ? '已售罄' : '立即抢购' }}
          </el-button>
        </div>
      </el-card>
    </div>

    <el-card class="block">
      <template #header><b>我的资源包</b>（点击行查看抵扣留痕）</template>
      <el-table :data="mine" size="small" v-loading="loading" @row-click="openDeductions" style="cursor: pointer">
        <template #empty><el-empty description="暂无资源包实例" :image-size="60" /></template>
        <el-table-column prop="title" label="资源包" min-width="160" />
        <el-table-column label="余量" width="110">
          <template #default="{ row }"><b>{{ row.remaining }}</b> / {{ row.total }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="水位" min-width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.total ? Math.round(Number(row.remaining) / Number(row.total) * 100) : 0" :stroke-width="6" />
          </template>
        </el-table-column>
        <el-table-column prop="productId" label="适用产品" width="90">
          <template #default="{ row }">{{ row.productId ? `#${row.productId}` : '通用' }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="有效期至" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="sourceOrderNo" label="来源订单" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-drawer v-model="drawer" :title="`抵扣留痕 · ${inst?.title ?? ''}`" size="520px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="余量">{{ inst?.remaining }} {{ inst?.unit }}</el-descriptions-item>
        <el-descriptions-item label="到期">{{ inst?.expireDate }}</el-descriptions-item>
      </el-descriptions>
      <p class="muted" style="margin-bottom: 8px">每次事件消费从本资源包扣减的明细（D2 来源留痕，R34）：</p>
      <el-table :data="ded" size="small" v-loading="dedLoading" border>
        <template #empty><el-empty description="尚无抵扣记录" :image-size="60" /></template>
        <el-table-column prop="sourceName" label="扣减来源" min-width="120" />
        <el-table-column label="扣减量" width="100" align="right">
          <template #default="{ row }">-{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right">
        <el-pagination v-model:current-page="dedPage" :total="dedTotal" :page-size="10"
          layout="prev, pager, next" @current-change="loadDeductions" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
@import './page.css';
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.goods .b { font-weight: 700; }
.goods .price { color: #0d9488; font-size: 20px; font-weight: 700; margin: 6px 0; }
@media (max-width: 1100px) { .cards { grid-template-columns: 1fr 1fr; } }
</style>
