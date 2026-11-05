<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { fmtCents } from '@/utils/format'

/** 租户管理（05 §6.1）：停用/恢复 + 透支白名单（R24，敏感操作留痕） */
const tenants = ref<any[]>([])
const loading = ref(false)
const stageText: Record<string, string> = { normal: '正常', warning: '预警', suspended: '停服' }

async function load() {
  loading.value = true
  try { tenants.value = (await request.get('/a/tenants')) ?? [] } finally { loading.value = false }
}

async function toggle(t: any) {
  const disabling = t.status !== 'disabled'
  await ElMessageBox.confirm(
    disabling ? `停用租户「${t.name}」后其全部成员无法登录，确定停用？` : `恢复租户「${t.name}」？`,
    disabling ? '停用租户' : '恢复租户',
    { type: 'warning' }
  )
  await request.post(`/a/tenants/${t.id}/${disabling ? 'disable' : 'enable'}`)
  ElMessage.success(`${t.name} 已${disabling ? '停用' : '恢复'}（留痕）`)
  await load()
}

// 透支白名单（R24）：白名单租户可配置更低透支阈值
const wlDialog = ref(false)
const wlTarget = ref<any>(null)
const wlForm = reactive({ isWhitelist: false, overdraftCents: -10000 })

function openWl(row: any) {
  wlTarget.value = row
  wlForm.isWhitelist = !!row.isWhitelist
  wlForm.overdraftCents = Number(row.overdraftCents ?? -10000)
  wlDialog.value = true
}

async function saveWl() {
  await request.put(`/a/tenants/${wlTarget.value.id}/whitelist`, { ...wlForm })
  ElMessage.success('白名单已更新（留痕）')
  wlDialog.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>租户管理</h2>
        <p class="muted">余额经 billing 聚合 · 停用/白名单为敏感操作（审计留痕）</p>
      </div>
    </div>
    <el-card class="block">
      <el-table :data="tenants" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无租户" :image-size="60" /></template>
        <el-table-column prop="name" label="租户" min-width="140" />
        <el-table-column prop="email" label="管理员邮箱" min-width="180" />
        <el-table-column label="余额" align="right" width="120">
          <template #default="{ row }">
            <span :class="{ neg: Number(row.balanceCents) < 0 }">{{ fmtCents(row.balanceCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="停服阶段" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.stage === 'normal' ? 'success' : row.stage === 'warning' ? 'warning' : 'danger'">
              {{ stageText[row.stage] ?? row.stage }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="白名单" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isWhitelist ? 'success' : 'info'">{{ row.isWhitelist ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 'disabled' ? 'success' : 'danger'" plain @click="toggle(row)">
              {{ row.status === 'disabled' ? '恢复' : '停用' }}
            </el-button>
            <el-button size="small" @click="openWl(row)">白名单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="wlDialog" title="透支白名单（R24，留痕）" width="420px">
      <el-form label-width="90px">
        <el-form-item label="租户"><b>{{ wlTarget?.name }}</b></el-form-item>
        <el-form-item label="白名单"><el-switch v-model="wlForm.isWhitelist" /></el-form-item>
        <el-form-item label="透支阈值">
          <el-input-number v-model="wlForm.overdraftCents" :step="1000" />
          <span class="muted" style="margin-left: 8px">{{ fmtCents(wlForm.overdraftCents) }}（低于此值触发停服）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wlDialog = false">取消</el-button>
        <el-button type="primary" @click="saveWl">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
.neg { color: #dc2626; }
</style>
