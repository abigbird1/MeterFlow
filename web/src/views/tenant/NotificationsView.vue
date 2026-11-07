<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { fmtTime } from '@/utils/format'

/** 通知中心（05 §5.11）：欠费/停服、资源包到期、出账、争议进展（仅站内渠道，默认约定 #8） */
const notifications = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try { notifications.value = (await request.get('/t/notifications')) ?? [] } finally { loading.value = false }
}

async function readAll() {
  await request.post('/t/notifications/read-all')
  ElMessage.success('已全部标记已读')
  await load()
}

function icon(t: string) { return t === 'warn' ? '⚠️' : t === 'success' ? '✅' : 'ℹ️' }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>通知中心</h2>
        <p class="muted">站内通知：欠费/停服、资源包到期、出账、争议进展（仅站内渠道，默认约定 #8）</p>
      </div>
      <el-button :disabled="!notifications.some(n => !n.isRead)" @click="readAll">全部已读</el-button>
    </div>
    <el-card class="block">
      <div v-for="n in notifications" :key="n.id" class="item" :class="{ unread: !n.isRead }">
        <span style="font-size: 18px">{{ icon(n.type) }}</span>
        <div style="flex: 1">
          <div class="b">{{ n.title }} <el-tag v-if="!n.isRead" size="small" type="danger">未读</el-tag></div>
          <div class="muted">{{ n.content }}</div>
        </div>
        <span class="time">{{ fmtTime(n.createdAt) }}</span>
      </div>
      <el-empty v-if="!loading && !notifications.length" description="暂无通知" :image-size="80" />
    </el-card>
  </div>
</template>

<style scoped>
@import './page.css';
.item { display: flex; gap: 12px; padding: 12px; border-bottom: 1px solid var(--mf-line); align-items: flex-start; }
.item:last-child { border-bottom: none; }
.item.unread { background: #f0fdfa; }
.b { font-size: 13px; font-weight: 600; }
.item .muted { color: #64748b; font-size: 12.5px; margin-top: 2px; }
.time { color: #94a3b8; font-size: 11px; white-space: nowrap; }
</style>
