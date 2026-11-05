<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

/** 个人信息（05 §5.10）：资料维护 + 密码修改（敏感操作，留痕） */
const profile = reactive({ name: '', email: '' })
const pwd = reactive({ oldPassword: '', newPassword: '' })
const pwdRef = ref()
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' },
  ],
}

async function load() {
  const d: any = await request.get('/t/profile')
  profile.name = d.name
  profile.email = d.email
}

async function save() {
  if (!profile.name.trim()) { ElMessage.warning('姓名不能为空'); return }
  await request.put('/t/profile', { name: profile.name.trim() })
  ElMessage.success('资料已保存')
}

async function changePwd() {
  await pwdRef.value?.validate().catch(() => Promise.reject())
  await ElMessageBox.confirm('修改密码后需重新登录，确定修改？', '修改密码', { type: 'warning' })
  await request.post('/t/profile/password', { ...pwd })
  ElMessage.success('密码已更新（敏感操作已留痕）')
  pwd.oldPassword = ''; pwd.newPassword = ''
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>个人信息</h2>
        <p class="muted">资料维护与密码管理（敏感操作留痕）</p>
      </div>
    </div>
    <el-card class="block" style="max-width: 520px">
      <template #header><b>基本资料</b></template>
      <el-form label-width="80px">
        <el-form-item label="姓名">
          <el-input v-model="profile.name" maxlength="24" show-word-limit />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profile.email" disabled />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="save">保存资料</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card class="block" style="max-width: 520px">
      <template #header><b>修改密码</b>（敏感操作，留痕）</template>
      <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="80px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwd.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwd.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" @click="changePwd">确认修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
@import './page.css';
</style>
