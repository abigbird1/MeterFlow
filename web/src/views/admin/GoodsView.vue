<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { fmtCents } from '@/utils/format'

/** 资源包商品（05 §6.3）：三重限制（R31）+ 下单锁库存（R32）· 上下架敏感操作留痕 */
const goods = ref<any[]>([])
const loading = ref(false)

const dialog = ref(false)
const form = reactive({
  goodsType: 'spec', name: '', spec: '', productId: 1001, metricKey: 'out_tok',
  priceCents: 48000, validDays: 90, totalStock: 500, perLimit: 2, orderLimit: 1,
  windowStart: '2026-11-01', windowEnd: '2026-11-30', quantity: 100000000, unit: 'Token',
})

async function load() {
  loading.value = true
  try { goods.value = (await request.get('/a/goods')) ?? [] } finally { loading.value = false }
}

async function create() {
  if (!form.name.trim()) { ElMessage.warning('请填写商品名'); return }
  await request.post('/a/goods', { ...form })
  ElMessage.success('商品已上架（三重限制 R31）')
  dialog.value = false
  await load()
}

async function toggle(g: any) {
  await ElMessageBox.confirm(
    g.status === 'on' ? `下架后租户无法抢购「${g.name}」，已有实例不受影响，确定下架？` : `重新上架「${g.name}」？`,
    g.status === 'on' ? '下架商品' : '上架商品',
    { type: 'warning' }
  )
  await request.post(`/a/goods/${g.id}/status`, { status: g.status === 'on' ? 'off' : 'on' })
  ElMessage.success('已' + (g.status === 'on' ? '下架' : '上架') + '（留痕）')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>资源包商品</h2>
        <p class="muted">三重限制（R31）：抢购窗口 + 每人限购 + 总库存 · 下单锁库存 15 分钟（R32）</p>
      </div>
      <el-button type="primary" @click="dialog = true">+ 新建商品</el-button>
    </div>
    <el-card class="block">
      <el-table :data="goods" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无商品" :image-size="60" /></template>
        <el-table-column prop="name" label="商品" min-width="140" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.goodsType === 'spec' ? '指定包' : '通用包' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="售价" align="right" width="100">
          <template #default="{ row }"><b>{{ fmtCents(row.priceCents) }}</b></template>
        </el-table-column>
        <el-table-column label="库存水位" min-width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.totalStock ? Math.round(row.sold / row.totalStock * 100) : 0" :stroke-width="6" />
            <span class="muted">{{ row.sold }}/{{ row.totalStock }}</span>
          </template>
        </el-table-column>
        <el-table-column label="限购" width="70"><template #default="{ row }">{{ row.perLimit }}/人</template></el-table-column>
        <el-table-column label="窗口" width="190">
          <template #default="{ row }">{{ row.windowStart }} ~ {{ row.windowEnd }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'on' ? 'success' : 'info'">{{ row.status === 'on' ? '在售' : '已下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 'on' ? 'danger' : 'success'" plain @click="toggle(row)">
              {{ row.status === 'on' ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建商品" width="540px">
      <el-form label-width="100px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.goodsType">
            <el-radio-button value="spec">指定产品包</el-radio-button>
            <el-radio-button value="common">通用包</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="规格说明">
          <el-input v-model="form.spec" maxlength="64" />
        </el-form-item>
        <el-form-item label="产品 ID / 指标">
          <el-input-number v-model="form.productId" :min="1" style="width: 130px" />
          <el-input v-model="form.metricKey" style="width: 130px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="售价（分）">
          <el-input-number v-model="form.priceCents" :min="100" :step="1000" />
          <span class="muted" style="margin-left: 8px">{{ fmtCents(form.priceCents) }}</span>
        </el-form-item>
        <el-form-item label="总量 / 单位">
          <el-input-number v-model="form.quantity" :min="1" :step="1000000" style="width: 160px" />
          <el-input v-model="form.unit" style="width: 90px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="库存 / 限购">
          <el-input-number v-model="form.totalStock" :min="1" style="width: 130px" />
          <el-input-number v-model="form.perLimit" :min="1" style="width: 110px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="抢购窗口">
          <el-date-picker v-model="form.windowStart" type="date" value-format="YYYY-MM-DD" />
          <span style="margin: 0 4px">~</span>
          <el-date-picker v-model="form.windowEnd" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="有效期（天）">
          <el-input-number v-model="form.validDays" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="create">创建并上架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
</style>
