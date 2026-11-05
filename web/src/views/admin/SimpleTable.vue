<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/api/request'

/** 通用管理表格页：路径 + 列定义即可渲染（平台端批量接入用） */
const props = defineProps<{ path: string; title: string; columns: any[] }>()
const rows = ref<any[]>([])

onMounted(async () => {
  const r: any = await request.get(props.path)
  rows.value = r.list ?? r ?? []
})
</script>

<template>
  <div style="padding: 24px">
    <h2>{{ props.title }}</h2>
    <p class="muted">数据来自 {{ props.path }}（真实后端）</p>
    <el-card style="margin-top: 12px">
      <el-table :data="rows" size="small">
        <el-table-column v-for="c in props.columns" :key="c.prop" :prop="c.prop" :label="c.label" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.muted { color: #94a3b8; font-size: 12px; }
</style>
