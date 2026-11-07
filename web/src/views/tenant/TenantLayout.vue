<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import request from '@/api/request'
import { toggleTheme } from '@/utils/theme'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const menus = [
  { path: '/t/overview', label: '用量总览', icon: '📊' },
  { path: '/t/wallet', label: '余额与充值', icon: '💰' },
  { path: '/t/packages', label: '资源包中心', icon: '📦' },
  { path: '/t/bills', label: '账单与明细', icon: '🧾' },
  { path: '/t/api-keys', label: 'API Key', icon: '🔑' },
  { path: '/t/disputes', label: '争议申诉', icon: '⚖️' },
  { path: '/t/org', label: '组织与角色', icon: '🏢' },
  { path: '/t/notifications', label: '通知中心', icon: '🔔', badgeKey: 'unread' },
  { path: '/t/profile', label: '个人信息', icon: '👤' }
]

/** 响应式：窄屏自动折叠侧边栏（规范 #6.5 常用屏幕适配） */
const collapsed = ref(window.innerWidth < 1100)
const onResize = () => { collapsed.value = window.innerWidth < 1100 }

/** 未读通知徽标：进入/切换页面与每 60s 轮询刷新；静默失败不影响使用 */
const badges = ref<Record<string, number>>({})
async function loadBadges() {
  try {
    const list: any = await request.get('/t/notifications', { silent: true })
    badges.value.unread = ((list ?? []) as any[]).filter(n => !n.isRead).length
  } catch { /* 静默 */ }
}
let timer: number | undefined

watch(() => route.path, loadBadges)

onMounted(() => {
  window.addEventListener('resize', onResize)
  loadBadges()
  timer = window.setInterval(loadBadges, 60_000)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (timer) clearInterval(timer)
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div style="display: flex; min-height: 100vh">
    <aside :style="{ width: collapsed ? '60px' : '220px', background: '#0b1220', color: '#94a3b8', padding: '16px 12px', transition: 'width .2s', flexShrink: 0, display: 'flex', flexDirection: 'column' }">
      <div style="color: #fff; font-weight: 700; padding: 8px 4px; white-space: nowrap; overflow: hidden; display: flex; align-items: center; justify-content: space-between; gap: 4px">
        <span v-if="!collapsed">⚡ MeterFlow <el-tag size="small" type="success">租户端</el-tag></span>
        <span v-else>⚡</span>
        <el-button size="small" text style="color: #94a3b8; padding: 0 2px" title="切换主题" @click="toggleTheme()">🌓</el-button>
      </div>
      <div v-for="m in menus" :key="m.path">
        <div
          style="padding: 9px 12px; border-radius: 8px; cursor: pointer; margin: 2px 0; display: flex; align-items: center; gap: 8px; white-space: nowrap; overflow: hidden"
          :style="route.path.startsWith(m.path) ? 'background:#134e4a;color:#5eead4' : ''"
          :title="collapsed ? m.label : undefined"
          @click="router.push(m.path)"
        >
          <span>{{ m.icon }}</span>
          <template v-if="!collapsed">
            <span style="flex: 1">{{ m.label }}</span>
            <el-badge v-if="m.badgeKey && badges[m.badgeKey]" :value="badges[m.badgeKey]" :max="99" />
          </template>
        </div>
      </div>
      <div style="margin-top: auto; padding-top: 24px" :style="collapsed ? 'font-size: 11px; text-align: center' : ''">
        <div style="color: #e2e8f0; font-size: 13px; white-space: nowrap; overflow: hidden" :title="auth.user?.name">
          {{ collapsed ? auth.user?.name?.slice(0, 1) : `${auth.user?.name}（${auth.user?.role}）` }}
        </div>
        <el-button size="small" link style="color: #94a3b8" @click="logout">{{ collapsed ? '⏻' : '退出登录' }}</el-button>
      </div>
    </aside>
    <main style="flex: 1; background: var(--mf-bg); min-width: 0">
      <router-view />
    </main>
  </div>
</template>
