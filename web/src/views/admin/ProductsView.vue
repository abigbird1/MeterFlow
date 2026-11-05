<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { fmtTime, fmtAmount } from '@/utils/format'

/** 产品与计价（05 §6.2）：调价生成新版本即时生效（R11）· 账期内分段计费 · 审计留痕 */
const products = ref<any[]>([])
const loading = ref(false)

// 产品详情抽屉（指标 + 价格版本历史）
const drawer = ref(false)
const detail = ref<any>(null)
const detailLoading = ref(false)

// 调价对话框
const dialog = ref(false)
const form = reactive({ productId: null as number | null, productName: '', metricId: null as number | null, price: 0.0018, note: '' })
const formRef = ref()
const rules = {
  metricId: [{ required: true, message: '请选择计价指标', trigger: 'change' }],
  price: [{ required: true, message: '请填写新单价', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try { products.value = (await request.get('/a/products')) ?? [] } finally { loading.value = false }
}

async function openDetail(p: any) {
  drawer.value = true
  detailLoading.value = true
  try { detail.value = await request.get(`/a/products/${p.id}`) } finally { detailLoading.value = false }
}

function openReprice(p: any) {
  form.productId = p.id
  form.productName = p.name
  form.metricId = null
  form.price = 0.0018
  form.note = '调价（R11 即时生效）'
  dialog.value = true
  // 预取指标列表供下拉选择
  request.get(`/a/products/${p.id}`).then((d: any) => { detail.value = d })
}

async function submit() {
  await formRef.value?.validate().catch(() => Promise.reject())
  const d: any = await request.post(`/a/products/${form.productId}/price-versions`, {
    items: [{ metricId: form.metricId, price: form.price }],
    note: form.note,
    operator: '钱运（运营）',
  })
  ElMessage.success(`新版本 v${d.versionNo} 已即时生效（R11），账期内按版本分段计费`)
  dialog.value = false
  if (drawer.value && detail.value?.product?.id === form.productId) {
    detail.value = await request.get(`/a/products/${form.productId}`)
  }
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>产品与计价</h2>
        <p class="muted">调价生成新版本即时生效（R11）· 账期内分段计费 · 点击卡片查看指标与版本历史</p>
      </div>
    </div>
    <div class="cards" v-loading="loading">
      <el-card v-for="p in products" :key="p.id" class="prod" @click="openDetail(p)">
        <div class="b">{{ p.name }} <el-tag size="small" :type="p.status === 'on' ? 'success' : 'info'">{{ p.status === 'on' ? '在售' : p.status }}</el-tag></div>
        <div class="muted">{{ p.description || '—' }}</div>
        <div style="margin-top: 10px; display: flex; justify-content: space-between; align-items: center">
          <span class="muted">产品 #{{ p.id }}</span>
          <el-button size="small" type="primary" plain @click.stop="openReprice(p)">调价</el-button>
        </div>
      </el-card>
    </div>

    <el-drawer v-model="drawer" :title="`产品详情 · ${detail?.product?.name ?? ''}`" size="600px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="产品 ID">{{ detail.product.id }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.product.description || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">计价指标</h4>
        <el-table :data="detail.metrics" size="small" border>
          <template #empty><el-empty description="暂无指标" :image-size="50" /></template>
          <el-table-column prop="id" label="指标ID" width="80" />
          <el-table-column prop="metricKey" label="指标 Key" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="unit" label="单位" width="80" />
        </el-table>

        <h4 class="sec">价格版本（按生效时间倒序，首行即当前生效）</h4>
        <el-table :data="detail.versions" size="small" border>
          <template #empty><el-empty description="暂无版本" :image-size="50" /></template>
          <el-table-column label="版本" width="70">
            <template #default="{ row, $index }">
              <el-tag size="small" :type="$index === 0 ? 'success' : 'info'">v{{ row.versionNo }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="单价明细（指标 × 单价）">
            <template #default="{ row }">
              <div v-for="it in (detail.items ?? []).filter((x: any) => x.versionId === row.id)" :key="it.id" class="price-line">
                {{ detail.metrics.find((m: any) => m.id === it.metricId)?.metricKey ?? '指标#' + it.metricId }}
                <b>{{ fmtAmount(it.price) }}</b> / {{ detail.metrics.find((m: any) => m.id === it.metricId)?.unit ?? '' }}
              </div>
              <span v-if="!(detail.items ?? []).some((x: any) => x.versionId === row.id)" class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" show-overflow-tooltip />
          <el-table-column label="生效时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.effectiveAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="dialog" :title="`调价 · ${form.productName}`" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="计价指标" prop="metricId">
          <el-select v-model="form.metricId" placeholder="选择要调价的指标" style="width: 100%">
            <el-option v-for="m in detail?.metrics ?? []" :key="m.id"
              :label="`${m.metricKey}（${m.name}）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="新单价" prop="price">
          <el-input-number v-model="form.price" :step="0.0001" :precision="4" :min="0.0001" />
          <span class="muted" style="margin-left: 8px">4 位小数（R15 精度）</span>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.note" maxlength="64" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="新版本即时生效：已发生事件按旧版本计价，此后事件按新版本（账期内分段，R11）" />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">发布新版本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.prod { cursor: pointer; }
.prod:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }
.b { font-weight: 700; }
.sec { margin: 18px 0 8px; }
.price-line { font-size: 12px; padding: 2px 0; }
@media (max-width: 1100px) { .cards { grid-template-columns: 1fr 1fr; } }
</style>
