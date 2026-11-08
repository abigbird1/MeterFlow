import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Placeholder from '@/views/Placeholder.vue'
import TenantLayout from '@/views/tenant/TenantLayout.vue'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import DashboardView from '@/views/admin/DashboardView.vue'
import MonitorView from '@/views/admin/MonitorView.vue'
import ProductsView from '@/views/admin/ProductsView.vue'
import GoodsView from '@/views/admin/GoodsView.vue'
import ReconView from '@/views/admin/ReconView.vue'
import BillingView from '@/views/admin/BillingView.vue'
import DisputesView from '@/views/admin/DisputesView.vue'
import TenantsView from '@/views/admin/TenantsView.vue'
import AuditView from '@/views/admin/AuditView.vue'
import OrdersView from '@/views/admin/OrdersView.vue'
import GrantsView from '@/views/admin/GrantsView.vue'
import UsersView from '@/views/admin/UsersView.vue'

/**
 * 页面组件注册（Vite 要求动态导入可静态分析）：
 * import.meta.glob 预注册全部视图，路由按 key 取懒加载函数。
 */
const pages: Record<string, () => Promise<any>> = import.meta.glob('../views/**/*.vue')
const page = (view: string) => pages[`../views/${view}.vue`] ?? Placeholder

const routes: RouteRecordRaw[] = [
  { path: '/login', component: () => import('@/views/LoginView.vue') },
  {
    path: '/t',
    component: TenantLayout,
    redirect: '/t/overview',
    children: [
      ['overview', '用量总览', 'tenant/OverviewView'],
      ['wallet', '余额与充值', 'tenant/WalletView'],
      ['packages', '资源包中心', 'tenant/PackagesView'],
      ['bills', '账单与明细', 'tenant/BillsView'],
      ['reports', '消费报表中心', 'tenant/ReportsView'],
      ['api-keys', 'API Key', 'tenant/ApiKeysView'],
      ['disputes', '争议申诉', 'tenant/DisputesView'],
      ['org', '组织与角色', 'tenant/OrgView'],
      ['notifications', '通知中心', 'tenant/NotificationsView'],
      ['profile', '个人信息', 'tenant/ProfileView']
    ].map(([path, title, view]) => ({
      path,
      component: view === 'Placeholder' ? Placeholder : page(view as string),
      meta: { title }
    }))
  },
  {
    path: '/a',
    component: AdminLayout,
    redirect: '/a/dashboard',
    children: [
      { path: 'dashboard', component: DashboardView, meta: { title: '数据看板' } },
      { path: 'monitor', component: MonitorView, meta: { title: '运营监控' } },
      { path: 'tenants', component: TenantsView, meta: { title: '租户管理' } },
      { path: 'products', component: ProductsView, meta: { title: '产品与计价' } },
      { path: 'goods', component: GoodsView, meta: { title: '资源包商品' } },
      { path: 'orders', component: OrdersView, meta: { title: '订单管理' } },
      { path: 'billing', component: BillingView, meta: { title: '账单管理' } },
      { path: 'recon', component: ReconView, meta: { title: '对账中心' } },
      { path: 'disputes', component: DisputesView, meta: { title: '争议工单' } },
      { path: 'grants', component: GrantsView, meta: { title: '额度与赠送' } },
      { path: 'users', component: UsersView, meta: { title: '系统用户' } },
      { path: 'audit', component: AuditView, meta: { title: '审计日志' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(to => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.isLogined) return '/login'
  if (to.path === '/login' && auth.isLogined) return auth.portal === 'a' ? '/a/dashboard' : '/t/overview'
  return true
})

export default router
