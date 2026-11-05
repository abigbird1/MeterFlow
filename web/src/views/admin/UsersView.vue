<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { STATUS_TEXT, statusType } from '@/utils/format'

/** 系统用户（05 §6.1）：平台六岗位（运营/财务/审核/客服/管理员…），岗位即权限矩阵（6.2） */
const users = ref<any[]>([])
const loading = ref(false)

const ROLE_TEXT: Record<string, string> = {
  sadmin: '系统管理员', ops: '运营', fin: '财务', auditor: '审核员', cs: '客服', read: '只读',
}

const dialog = ref(false)
const form = reactive({ name: '', email: '', role: 'ops' })
const formRef = ref()
const rules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请填写邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择岗位', trigger: 'change' }],
}

async function load() {
  loading.value = true
  try { users.value = (await request.get('/a/platform-users')) ?? [] } finally { loading.value = false }
}

async function submit() {
  await formRef.value?.validate().catch(() => Promise.reject())
  await request.post('/a/platform-users/new', { ...form })
  ElMessage.success('平台用户已创建（初始口令 demo，敏感操作留痕）')
  dialog.value = false
  form.name = ''; form.email = ''; form.role = 'ops'
  await load()
}

async function toggle(u: any) {
  const disabling = u.status !== 'disabled'
  await ElMessageBox.confirm(
    disabling ? `停用后「${u.name}」将无法登录管理后台，确定停用？` : `恢复「${u.name}」的登录权限？`,
    disabling ? '停用用户' : '启用用户',
    { type: 'warning' }
  )
  await request.post(`/a/platform-users/${u.id}/${disabling ? 'disable' : 'enable'}`)
  ElMessage.success('已更新（留痕）')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>系统用户</h2>
        <p class="muted">平台侧账号与岗位（岗位即 6.2 权限矩阵：账单操作=财务/管理员、调价=运营、初审≠复审同岗）</p>
      </div>
      <el-button type="primary" @click="dialog = true">新建用户</el-button>
    </div>
    <el-card class="block">
      <el-table :data="users" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无系统用户" :image-size="60" /></template>
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="岗位" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ ROLE_TEXT[row.role] ?? row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status === 'disabled' ? 'disabled' : 'enabled')">
              {{ row.status === 'disabled' ? '停用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 'disabled' ? 'success' : 'danger'" plain @click="toggle(row)">
              {{ row.status === 'disabled' ? '启用' : '停用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建平台用户" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="24" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="work@meterflow.cn" />
        </el-form-item>
        <el-form-item label="岗位" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="(label, key) in ROLE_TEXT" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
</style>
