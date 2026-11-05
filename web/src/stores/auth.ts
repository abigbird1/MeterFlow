import { defineStore } from 'pinia'
import request from '@/api/request'

/**
 * 会话状态（骨架版）：登录走 POST /auth/login（platform）；
 * 路由守卫仅做前端导航控制（meterflow.md #6.8），真实鉴权由后端执行。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('mf_token') ?? '',
    user: JSON.parse(localStorage.getItem('mf_user') ?? 'null') as
      | { name: string; email: string; role: string; portal: 't' | 'a' }
      | null,
    permissions: JSON.parse(localStorage.getItem('mf_perms') ?? '[]') as string[]
  }),
  getters: {
    isLogined: s => !!s.token,
    portal: s => s.user?.portal ?? null
  },
  actions: {
    /** 保存登录响应（LoginView 已自行发请求时复用） */
    saveSession(data: any) {
      this.token = data.token
      this.user = data.user
      this.permissions = data.permissions ?? []
      localStorage.setItem('mf_token', this.token)
      localStorage.setItem('mf_user', JSON.stringify(this.user))
      localStorage.setItem('mf_perms', JSON.stringify(this.permissions))
    },
    async login(email: string, password: string, portal: 't' | 'a') {
      const data: any = await request.post('/auth/login', { email, password, portal })
      this.saveSession(data)
    },
    logout() {
      this.token = ''
      this.user = null
      this.permissions = []
      localStorage.removeItem('mf_token')
      localStorage.removeItem('mf_user')
      localStorage.removeItem('mf_perms')
    }
  }
})
