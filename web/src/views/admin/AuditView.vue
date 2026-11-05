<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import request from '@/api/request'
import { fmtTime } from '@/utils/format'

/** 审计日志（05 §6.6）：敏感操作全留痕（审计四要素：操作人/时间/动作/对象）· 保留 1 年 */
const audits = ref<any[]>([])
const loading = ref(false)
const onlySensitive = ref(false)
const roleFilter = ref('')
const page = ref(1)
const pageSize = 20

const ROLE_TEXT: Record<string, string> = {
  sadmin: '系统管理员', ops: '运营', fin: '财务', auditor: '审核员', cs: '客服', read: '只读', member: '成员', '—': '—',
}

async function load() {
  loading.value = true
  try {
    const r: any = await request.get('/a/audit-logs', { params: { limit: 200 } })
    audits.value = (r?.list ?? r ?? []) as any[]
  } finally {
    loading.value = false
  }
}

const roles = computed(() => [...new Set(audits.value.map(a => a.actorRole))])

const filtered = computed(() =>
  audits.value.filter(a =>
    (!onlySensitive.value || a.isSensitive) && (!roleFilter.value || a.actorRole === roleFilter.value)))

const paged = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>审计日志</h2>
        <p class="muted">敏感操作全留痕（代查/冲正/停服/调价/白名单）· 审计四要素：操作人/时间/动作/对象 · 保留 1 年</p>
      </div>
      <div style="display: flex; gap: 12px; align-items: center">
        <el-select v-model="roleFilter" placeholder="全部岗位" clearable style="width: 140px" size="small">
          <el-option v-for="r in roles" :key="r" :label="ROLE_TEXT[r] ?? r" :value="r" />
        </el-select>
        <el-switch v-model="onlySensitive" active-text="仅敏感操作" />
      </div>
    </div>
    <el-card class="block">
      <el-table :data="paged" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无审计日志" :image-size="60" /></template>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="actorName" label="操作人" width="110" />
        <el-table-column label="岗位" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ROLE_TEXT[row.actorRole] ?? row.actorRole }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" min-width="140">
          <template #default="{ row }">
            {{ row.action }}
            <el-tag v-if="row.isSensitive" size="small" type="danger" style="margin-left: 4px">敏感</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="对象" min-width="160" show-overflow-tooltip />
        <el-table-column prop="scope" label="范围/说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="String(row.result).includes('成功') ? 'success' : 'warning'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="flex-between" style="margin-top: 12px">
        <span class="muted">共 {{ filtered.length }} 条（近 200 条，按时间倒序）</span>
        <el-pagination v-model:current-page="page" :total="filtered.length" :page-size="pageSize"
          layout="prev, pager, next" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
</style>
