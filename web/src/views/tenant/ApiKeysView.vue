<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { fmtTime, fmtCents, STATUS_TEXT, statusType, copyText } from '@/utils/format'

/** API Key 管理（05 §5.6）：hash 存储 · 明文仅创建/轮换时一次性返回 · 轮换并行 24h · 敏感操作留痕 · Key 维度用量统计 */
const keys = ref<any[]>([])
const depts = ref<any[]>([])
const stats = ref<Map<number, any>>(new Map())
const loading = ref(false)
const createVisible = ref(false)
const formRef = ref()
const form = reactive({ name: '', note: '', deptId: null as number | null })
const rules = { name: [{ required: true, message: '请填写 Key 名称', trigger: 'blur' }] }

// 明文 Key 一次性展示
const plainKey = ref('')
const plainVisible = ref(false)
const plainTitle = ref('')

// 部门树扁平化（名称路径，用于下拉展示）
function flatten(list: any[], prefix = ''): any[] {
  const out: any[] = []
  for (const d of list ?? []) {
    const label = prefix + d.name
    out.push({ id: d.id, label })
    out.push(...flatten(d.children, label + ' / '))
  }
  return out
}

async function load() {
  loading.value = true
  try {
    keys.value = (await request.get('/t/api-keys')) ?? []
    depts.value = flatten((await request.get('/t/org/departments')) ?? [])
    // Key 维度用量统计（本月，billing 聚合；key_id 上线前的历史事件不计入）
    const st: any = await request.get('/t/keys/stats', { silent: true })
    stats.value = new Map(((st ?? []) as any[]).map(r => [Number(r.keyId), r]))
  } finally {
    loading.value = false
  }
}

function keyStat(id: any) { return stats.value.get(Number(id)) }

async function submitCreate() {
  await formRef.value?.validate().catch(() => Promise.reject())
  await request.post('/t/api-keys', { ...form, products: null })
  createVisible.value = false
  form.name = ''; form.note = ''; form.deptId = null
  await load()
}

async function toggle(k: any) {
  const disabling = k.status === 'enabled'
  await ElMessageBox.confirm(
    disabling ? `禁用后使用该 Key 的事件上报将立即被拒收（401），确定禁用「${k.name}」？` : `重新启用「${k.name}」？`,
    disabling ? '禁用 Key' : '启用 Key',
    { type: 'warning', confirmButtonText: disabling ? '确认禁用' : '确认启用' }
  )
  await request.post(`/t/api-keys/${k.id}/status`, { action: disabling ? 'disable' : 'enable' })
  ElMessage.success('已更新（敏感操作已留痕）')
  await load()
}

async function rotate(k: any) {
  await ElMessageBox.confirm(
    `将为「${k.name}」生成新 Key：旧 Key 进入 24h 并行期后失效，期间新旧 Key 均可上报。确定轮换？`,
    '轮换 Key',
    { type: 'warning', confirmButtonText: '确认轮换' }
  )
  const d: any = await request.post(`/t/api-keys/${k.id}/rotate`)
  plainTitle.value = `新 Key（旧 Key 24 小时后失效）`
  plainKey.value = d.plainKey
  plainVisible.value = true
  await load()
}

async function copyKey() {
  if (await copyText(plainKey.value)) ElMessage.success('已复制到剪贴板')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>API Key 管理</h2>
        <p class="muted">hash 存储（明文仅创建/轮换时一次性返回）· 轮换并行 24h · 敏感操作留痕 · 本月用量按上报 Key 聚合（历史事件未记录 Key，不计入）</p>
      </div>
      <el-button type="primary" @click="createVisible = true">创建 Key</el-button>
    </div>

    <el-card class="block">
      <el-table :data="keys" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无 API Key，点击右上角创建" :image-size="60" /></template>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="keyMask" label="Key（掩码）" min-width="160" />
        <el-table-column label="绑定部门" width="110">
          <template #default="{ row }">{{ depts.find(d => d.id === row.deptId)?.label ?? '全租户' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="本月事件" width="90" align="right">
          <template #default="{ row }">{{ keyStat(row.id)?.events ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="本月消费" width="110" align="right">
          <template #default="{ row }">{{ keyStat(row.id) ? fmtCents(keyStat(row.id).costCents) : '—' }}</template>
        </el-table-column>
        <el-table-column label="最近使用" width="160"><template #default="{ row }">{{ fmtTime(row.lastUsedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 'enabled' ? 'danger' : 'success'" plain @click="toggle(row)">
              {{ row.status === 'enabled' ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" @click="rotate(row)">轮换</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="创建 API Key" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：订单中台上报" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="备注用途">
          <el-input v-model="form.note" placeholder="用途说明（可选）" maxlength="64" />
        </el-form-item>
        <el-form-item label="绑定部门">
          <el-select v-model="form.deptId" placeholder="全租户（不区分部门）" clearable style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="plainVisible" :title="plainTitle" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" title="明文仅此一次显示，请立即复制保存；关闭后无法再次查看" style="margin-bottom: 12px" />
      <el-input :model-value="plainKey" readonly>
        <template #append><el-button @click="copyKey">复制</el-button></template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="plainVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import './page.css';
</style>
