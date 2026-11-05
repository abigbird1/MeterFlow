<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/api/request'
import { fmtCents, fmtTime, STATUS_TEXT, statusType } from '@/utils/format'

/** 订单管理（05 §6.4，默认约定）：全部资源包订单（含待支付/已支付/取消/超时释放） */
const orders = ref<any[]>([])
const goods = ref<any[]>([])
const status = ref('')
const loading = ref(false)

const FILTERS = [
  { value: '', label: '全部' },
  { value: 'created', label: '待支付' },
  { value: 'paid', label: '已支付' },
  { value: 'canceled', label: '已取消' },
  { value: 'expired', label: '超时释放' },
]

async function load() {
  loading.value = true
  try {
    orders.value = (await request.get('/a/orders', { params: status.value ? { status: status.value } : {} })) ?? []
  } finally {
    loading.value = false
  }
}

function goodsName(id: any) { return goods.value.find(g => String(g.id) === String(id))?.name ?? `#${id}` }
function goodsInfo(id: any) {
  const g = goods.value.find(x => String(x.id) === String(id))
  return g ? `${g.name}（${g.spec || g.metricKey}）` : `#${id}`
}

onMounted(async () => {
  const g: any = await request.get('/a/goods')
  goods.value = g ?? []
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>订单管理</h2>
        <p class="muted">资源包订单全量视图 · 下单锁库存 15 分钟（R32），超时自动释放</p>
      </div>
      <el-radio-group v-model="status" @change="load">
        <el-radio-button v-for="f in FILTERS" :key="f.value" :value="f.value">{{ f.label }}</el-radio-button>
      </el-radio-group>
    </div>
    <el-card class="block">
      <el-table :data="orders" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无订单" :image-size="60" /></template>
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column label="租户" width="90"><template #default="{ row }">#{{ row.tenantId }}</template></el-table-column>
        <el-table-column label="商品" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ goodsInfo(row.goodsId) }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="70" />
        <el-table-column label="金额" align="right" width="110">
          <template #default="{ row }"><b>{{ fmtCents(row.amountCents) }}</b></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="支付截止" width="160"><template #default="{ row }">{{ fmtTime(row.payExpireAt) }}</template></el-table-column>
        <el-table-column label="支付时间" width="160"><template #default="{ row }">{{ fmtTime(row.paidAt) }}</template></el-table-column>
        <el-table-column prop="closeNote" label="关闭原因" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
</style>
