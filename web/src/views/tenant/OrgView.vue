<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { STATUS_TEXT, statusType, fmtCents } from '@/utils/format'

/** 组织·成员·角色（05 §5.9）：多级部门树 + 成员管理 + 预设/自定义角色（部门级数据隔离基础） */
const depts = ref<any[]>([])
const members = ref<any[]>([])
const roles = ref<any[]>([])
const loading = ref(false)

// 邀请成员
const inviteVisible = ref(false)
const inviteRef = ref()
const invite = reactive({ email: '', name: '', deptId: null as number | null, roleId: null as number | null })
const inviteRules = {
  email: [
    { required: true, message: '请填写邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

// 新增部门
const deptVisible = ref(false)
const deptForm = reactive({ parentId: null as number | null, name: '' })

// 角色权限抽屉
const permVisible = ref(false)
const permRole = ref<any>(null)
const perms = ref<any[]>([])

// 新建角色
const roleVisible = ref(false)
const roleForm = reactive({ name: '', perms: '' })

/** 部门本月消费（billing 聚合；管理员看全租户，成员仅本部门） */
const deptStats = ref<any[]>([])
const totalCost = computed(() => deptStatRows.value.reduce((s, r) => s + r.costCents, 0))
const deptStatRows = computed(() => {
  const m = new Map<string, any>()
  for (const r of deptStats.value) {
    const k = r.deptName || '未分配'
    const cur = m.get(k) ?? { deptName: k, events: 0, costCents: 0 }
    cur.events += r.events; cur.costCents += r.costCents
    m.set(k, cur)
  }
  return [...m.values()].sort((a, b) => b.costCents - a.costCents)
})

const deptFlat = computed(() => {
  const out: any[] = []
  const walk = (list: any[], prefix: string) => {
    for (const d of list ?? []) {
      out.push({ id: d.id, label: prefix + d.name })
      walk(d.children ?? [], prefix + d.name + ' / ')
    }
  }
  walk(depts.value, '')
  return out
})

function deptName(id: any) { return deptFlat.value.find(d => d.id === id)?.label ?? '未分配' }
function roleName(id: any) { return roles.value.find(r => r.id === id)?.name ?? `角色 #${id}` }

async function load() {
  loading.value = true
  try {
    const d: any = await request.get('/t/org/departments')
    const m: any = await request.get('/t/org/members')
    const r: any = await request.get('/t/org/roles')
    depts.value = d ?? []
    members.value = m?.list ?? m ?? []
    roles.value = r ?? []
    request.get('/t/depts/stats', { silent: true }).then((st: any) => { deptStats.value = st ?? [] })
  } finally {
    loading.value = false
  }
}

async function openDeptDialog(parentId: number | null) {
  deptForm.parentId = parentId
  deptForm.name = ''
  deptVisible.value = true
}

async function submitDept() {
  if (!deptForm.name.trim()) { ElMessage.warning('请填写部门名称'); return }
  await request.post(`/t/org/departments/${deptForm.parentId ?? 0}`, { name: deptForm.name.trim() })
  ElMessage.success('部门已创建')
  deptVisible.value = false
  await load()
}

async function openInvite() {
  invite.email = ''; invite.name = ''; invite.deptId = null; invite.roleId = null
  inviteVisible.value = true
}

async function submitInvite() {
  await inviteRef.value?.validate().catch(() => Promise.reject())
  await request.post('/t/org/members', { ...invite })
  ElMessage.success('邀请成功（初始口令已发送邮箱，演示环境为 demo）')
  inviteVisible.value = false
  await load()
}

async function changeRole(m: any, roleId: number) {
  await request.post(`/t/org/members/${m.id}/role`, { roleId })
  ElMessage.success(`已将「${m.name}」调整为 ${roleName(roleId)}`)
  await load()
}

async function toggleStatus(m: any) {
  const disabling = m.status === 'active'
  await ElMessageBox.confirm(
    disabling ? `停用后「${m.name}」将无法登录租户控制台，确定停用？` : `恢复「${m.name}」的登录权限？`,
    disabling ? '停用成员' : '启用成员',
    { type: 'warning', confirmButtonText: disabling ? '确认停用' : '确认启用' }
  )
  await request.post(`/t/org/members/${m.id}/status`, { status: disabling ? 'disabled' : 'active' })
  ElMessage.success('已更新')
  await load()
}

async function openPerms(r: any) {
  permRole.value = r
  perms.value = (await request.get(`/t/org/roles/${r.id}/permissions`)) ?? []
  permVisible.value = true
}

async function submitRole() {
  if (!roleForm.name.trim()) { ElMessage.warning('请填写角色名称'); return }
  if (!roleForm.perms.trim()) { ElMessage.warning('请填写权限点，多个用逗号分隔'); return }
  await request.post('/t/org/roles', {
    name: roleForm.name.trim(),
    perms: roleForm.perms.split(/[,，]/).map(s => s.trim()).filter(Boolean),
  })
  ElMessage.success('自定义角色已创建')
  roleVisible.value = false
  roleForm.name = ''; roleForm.perms = ''
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>组织与角色</h2>
        <p class="muted">多级部门树 + 预设/自定义角色 + 部门级数据隔离（成员仅见本部门用量明细）</p>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button @click="openDeptDialog(null)">+ 根部门</el-button>
        <el-button @click="roleVisible = true">+ 自定义角色</el-button>
        <el-button type="primary" @click="openInvite">邀请成员</el-button>
      </div>
    </div>

    <el-card class="block">
      <template #header><b>成员管理</b></template>
      <el-table :data="members" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无成员" :image-size="60" /></template>
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="部门" min-width="140"><template #default="{ row }">{{ deptName(row.deptId) }}</template></el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select :model-value="row.roleId" size="small" @change="(v: number) => changeRole(row, v)">
              <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ STATUS_TEXT[row.status] ?? row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 'active' ? 'danger' : 'success'" plain @click="toggleStatus(row)">
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="grid2">
      <el-card>
        <template #header><b>部门树</b>（子部门事件计入本部门，明细按部门隔离）</template>
        <el-tree :data="depts" node-key="id" default-expand-all :expand-on-click-node="false">
          <template #default="{ data }">
            <div class="tree-node">
              <span>📁 {{ data.name }}</span>
              <el-button size="small" link type="primary" @click="openDeptDialog(data.id)">+ 子部门</el-button>
            </div>
          </template>
        </el-tree>
      </el-card>

      <el-card>
        <template #header><b>角色</b>（点击查看权限点）</template>
        <div v-for="r in roles" :key="r.id" class="role-row" @click="openPerms(r)">
          <span>🏷️ {{ r.name }}</span>
          <span class="muted">{{ r.roleType === 'preset' ? '预设' : '自定义' }}</span>
        </div>
        <el-empty v-if="!roles.length" description="暂无角色" :image-size="50" />
      </el-card>
    </div>

    <el-card class="block">
      <template #header><b>部门本月消费</b>（按上报事件聚合；管理员看全租户，成员仅本部门）</template>
      <el-table :data="deptStatRows" size="small">
        <template #empty><el-empty description="本月暂无部门消费数据" :image-size="50" /></template>
        <el-table-column prop="deptName" label="部门" min-width="140" />
        <el-table-column prop="events" label="事件数" width="100" align="right" />
        <el-table-column label="消费金额" width="130" align="right">
          <template #default="{ row }"><b>{{ fmtCents(row.costCents) }}</b></template>
        </el-table-column>
        <el-table-column label="占比" min-width="200">
          <template #default="{ row }">
            <el-progress :percentage="totalCost ? Math.round(row.costCents / totalCost * 100) : 0" :stroke-width="8" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="deptVisible" :title="deptForm.parentId ? '新增子部门' : '新增根部门'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="上级部门">
          <el-input :model-value="deptForm.parentId ? deptFlat.find(d => d.id === deptForm.parentId)?.label : '（无 · 根部门）'" disabled />
        </el-form-item>
        <el-form-item label="部门名称">
          <el-input v-model="deptForm.name" placeholder="如：算法部" maxlength="24" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDept">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inviteVisible" title="邀请成员" width="480px">
      <el-form ref="inviteRef" :model="invite" :rules="inviteRules" label-width="80px">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="invite.email" placeholder="成员登录邮箱" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="invite.name" maxlength="24" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="invite.deptId" placeholder="未分配（仅管理员可见其明细）" clearable style="width: 100%">
            <el-option v-for="d in deptFlat" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="invite.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInvite">邀请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="新建自定义角色" width="480px">
      <el-form label-width="80px">
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.name" placeholder="如：只读财务" maxlength="24" />
        </el-form-item>
        <el-form-item label="权限点">
          <el-input v-model="roleForm.perms" type="textarea" :rows="3" placeholder="逗号分隔，如：bills:detail, usage:list" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRole">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="permVisible" :title="`角色权限 · ${permRole?.name ?? ''}`" size="380px">
      <el-tag v-for="p in perms" :key="p.id" style="margin: 0 8px 8px 0">{{ p.permCode }}</el-tag>
      <el-empty v-if="!perms.length" description="该角色暂无权限点" :image-size="60" />
    </el-drawer>
  </div>
</template>

<style scoped>
@import './page.css';
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.tree-node { display: flex; justify-content: space-between; align-items: center; width: 100%; padding-right: 8px; font-size: 13px; }
.role-row { display: flex; justify-content: space-between; padding: 8px 4px; font-size: 13px; border-bottom: 1px dashed var(--mf-line); cursor: pointer; }
.role-row:hover { background: var(--mf-hover); }
</style>
