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
  { path: '/a/dashboard', label: '数据看板', icon: '📊' },
  { path: '/a/tenants', label: '租户管理', icon: '🏢' },
  { path: '/a/products', label: '产品与计价', icon: '🏷️' },
  { path: '/a/goods', label: '资源包商品', icon: '📦' },
  { path: '/a/orders', label: '订单管理', icon: '🧾' },
  { path: '/a/billing', label: '账单管理', icon: '💳', badgeKey: 'billing' },
  { path: '/a/recon', label: '对账中心', icon: '🔍', badgeKey: 'recon' },
  { path: '/a/disputes', label: '争议工单', icon: '⚖️', badgeKey: 'disputes' },
  { path: '/a/grants', label: '额度与赠送', icon: '🎁' },
  { path: '/a/users', label: '系统用户', icon: '👥' },
  { path: '/a/audit', label: '审计日志', icon: '📜' }
]

/** 响应式：窄屏自动折叠侧边栏（规范 #6.5 常用屏幕适配） */
const collapsed = ref(window.innerWidth < 1100)
const onResize = () => { collapsed.value = window.innerWidth < 1100 }

/** 待办徽标（六岗位工作台口径）：待办账单+欠费、待审调整单、待受理争议；静默失败不影响使用 */
const badges = ref<Record<string, number>>({})
async function loadBadges() {
  const jobs = [
    request.get('/a/bills', { silent: true }).then((bills: any) => {
      const debts: any = request.get('/a/debts', { silent: true }).catch(() => [])
      return Promise.all([Promise.resolve(bills ?? []), debts]).then(([bl, db]) => {
        const pendingBills = (bl as any[]).filter(x => x.status === 'draft' || x.status === 'confirmed').length
        badges.value.billing = pendingBills + ((db as any[]) ?? []).length
      })
    }).catch(() => {}),
    request.get('/a/adjustments', { silent: true }).then((a: any) => {
      badges.value.recon = ((a ?? []) as any[]).filter(x => x.stage === 'review1' || x.stage === 'review2').length
    }).catch(() => {}),
    request.get('/a/disputes', { silent: true }).then((d: any) => {
      badges.value.disputes = ((d ?? []) as any[]).filter(x => x.status === 'pending').length
    }).catch(() => {}),
  ]
  await Promise.allSettled(jobs)
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
        <span v-if="!collapsed">⚡ MeterFlow <el-tag size="small" >平台端</el-tag></span>
        <span v-else>⚡</span>
        <el-button size="small" text style="color: #94a3b8; padding: 0 2px" title="切换主题" @click="toggleTheme()">🌓</el-button>
      </div>
      <div v-for="m in menus" :key="m.path">
        <div
          style="padding: 9px 12px; border-radius: 8px; cursor: pointer; margin: 2px 0; display: flex; align-items: center; gap: 8px; white-space: nowrap; overflow: hidden"
          :style="route.path.startsWith(m.path) ? 'background:#312e81;color:#a5b4fc' : ''"
          :title="collapsed ? m.label : undefined"
          @click="router.push(m.path)"
        >
          <span>{{ m.icon }}</span>
          <template v-if="!collapsed">
            <span style="flex: 1">{{ m.label }}</span>
            <el-badge v-if="m.badgeKey && badges[m.badgeKey]" :value="badges[m.badgeKey]" :max="99" type="warning" />
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
