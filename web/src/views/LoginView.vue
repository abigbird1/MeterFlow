<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useAuthStore } from '@/stores/auth'

/** 登录 / 注册（D1 租户自助注册：注册即开通 + 管理员成员 + 内置免费额度） */
const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const form = reactive({ email: '', password: '', portal: 't' as 't' | 'a' })
const reg = reactive({ name: '', email: '', password: '', confirm: '' })

const demoAccounts = [
  { email: 'zhangwei@xinglan.com', label: '张伟 · 租户管理员', portal: 't' as const },
  { email: 'wangfang@xinglan.com', label: '王芳 · 成员(研发部)', portal: 't' as const },
  { email: 'root@meterflow.cn', label: '系统管理员 · 平台', portal: 'a' as const },
  { email: 'wucai@meterflow.cn', label: '吴财 · 财务', portal: 'a' as const }
]

function pick(email: string, portal: 't' | 'a') {
  mode.value = 'login'
  form.email = email
  form.portal = portal
  form.password = 'demo'
}

async function submit() {
  if (!form.email) { ElMessage.warning('请输入邮箱'); return }
  const data: any = await request.post('/auth/login', { ...form, password: form.password || 'demo' })
  auth.saveSession(data)                      // 令牌入 store（路由守卫依据）
  ElMessage.success(`欢迎，${data.user.name}`)
  router.push(data.user.portal === 'a' ? '/a/dashboard' : '/t/overview')
}

async function submitRegister() {
  if (!reg.name.trim()) { ElMessage.warning('请填写企业/团队名称'); return }
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(reg.email)) { ElMessage.warning('邮箱格式不正确'); return }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(reg.password)) {
    ElMessage.warning('密码需 ≥8 位且同时包含字母与数字（E2）'); return
  }
  if (reg.password !== reg.confirm) { ElMessage.warning('两次输入的密码不一致'); return }
  // 注册即开通（D1/4.1）：创建租户 + 管理员成员，并直接登录进入租户控制台
  const data: any = await request.post('/auth/register', {
    name: reg.name.trim(), email: reg.email.trim(), password: reg.password,
  })
  auth.saveSession(data)
  ElMessage.success(`注册成功，欢迎 ${data.user.name}！租户已开通`)
  router.push('/t/overview')
}
</script>

<template>
  <div style="min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(160deg, #0b1220, #134e4a)">
    <el-card style="width: 420px">
      <template #header>
        <b>MeterFlow {{ mode === 'login' ? '登录' : '注册' }}</b>
        <div class="small">{{ mode === 'login' ? '真实后端：POST /auth/login（Sa-Token）' : '注册即开通：创建租户 + 管理员成员（POST /auth/register）' }}</div>
      </template>

      <template v-if="mode === 'login'">
        <el-form label-width="64">
          <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="邮箱" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" type="password" placeholder="演示口令 demo" /></el-form-item>
          <el-form-item label="端">
            <el-radio-group v-model="form.portal">
              <el-radio-button value="t">租户控制台</el-radio-button>
              <el-radio-button value="a">管理后台</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-button type="primary" style="width: 100%" @click="submit">登 录</el-button>
        </el-form>
        <div style="text-align: right; margin: 8px 0 4px">
          <el-link type="primary" @click="mode = 'register'">没有租户？立即注册 →</el-link>
        </div>
        <el-divider>演示账号（点击填充）</el-divider>
        <div v-for="a in demoAccounts" :key="a.email" style="margin: 4px 0">
          <el-button size="small" style="width: 100%" @click="pick(a.email, a.portal)">{{ a.label }}</el-button>
        </div>
      </template>

      <template v-else>
        <el-form label-width="90">
          <el-form-item label="企业名称">
            <el-input v-model="reg.name" placeholder="租户名称，如：星辰科技" maxlength="32" />
          </el-form-item>
          <el-form-item label="管理员邮箱">
            <el-input v-model="reg.email" placeholder="用于登录的管理员邮箱" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="reg.password" type="password" show-password placeholder="≥8 位，含字母与数字" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="reg.confirm" type="password" show-password placeholder="再次输入密码" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" @click="submitRegister">注册并开通租户</el-button>
        </el-form>
        <el-alert type="info" :closable="false" style="margin-top: 12px"
          title="注册即开通（D1）：内置免费额度在首次计价时自动生效" />
        <div style="text-align: right; margin-top: 8px">
          <el-link @click="mode = 'login'">← 返回登录</el-link>
        </div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.small { color: #94a3b8; font-size: 12px; }
</style>
