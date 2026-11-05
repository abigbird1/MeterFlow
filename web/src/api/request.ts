import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

/**
 * 统一请求封装（meterflow.md #6.7/#7.3）：
 * - Bearer 令牌注入；响应解包 {code, message, data}
 * - 401 → 回登录页；403 + METER_SUSPENDED → 停服提示（R8）
 * - 非 00000 统一错误提示，业务方按需捕获
 */
const request = axios.create({ baseURL: '/', timeout: 15000 })

// 允许调用方传 { silent: true } 静默失败（axios 透传自定义配置）
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

request.interceptors.request.use(config => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

request.interceptors.response.use(
  res => {
    const { code, message, data } = res.data ?? {}
    if (code === '00000') return data
    // silent 请求（如侧边栏徽标轮询）失败不打扰用户
    if (!res.config?.silent) ElMessage.error(message || '操作失败')
    return Promise.reject(new Error(message))
  },
  err => {
    const status = err.response?.status
    const body = err.response?.data
    if (status === 401) {
      useAuthStore().logout()
      router.push('/login')
    } else if (status === 403 && body?.message?.includes('METER_SUSPENDED')) {
      // R8：停服拒收，业务系统与页面均以此感知
      ElMessage.error('当前产品处于停服状态（METER_SUSPENDED），请充值后重试')
    } else {
      ElMessage.error(body?.message || err.message || '网络异常')
    }
    return Promise.reject(err)
  }
)

export default request
