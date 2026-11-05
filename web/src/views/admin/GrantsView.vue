<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { fmtTime, fmtAmount } from '@/utils/format'

/** 额度与赠送（05 §6.2）：平台向租户发放赠送额度（R13 先赠后免、抵扣优先级链首位） */
const grants = ref<any[]>([])
const loading = ref(false)

const dialog = ref(false)
const form = reactive({ tenantId: 1001, productMetric: '', amount: 1000000, expireDate: '' })
const formRef = ref()
const rules = {
  productMetric: [{ required: true, message: '请填写指标 Key（如 out_tok）', trigger: 'blur' }],
  amount: [{ required: true, message: '请填写赠送量', trigger: 'blur' }],
  expireDate: [{ required: true, message: '请选择到期日', trigger: 'change' }],
}

async function load() {
  loading.value = true
  try { grants.value = (await request.get('/a/grants')) ?? [] } finally { loading.value = false }
}

async function submit() {
  await formRef.value?.validate().catch(() => Promise.reject())
  await request.post('/a/grants', { ...form })
  ElMessage.success('赠送额度单已发放（抵扣优先级：赠送 → 免费 → 资源包 → 余额，R13）')
  dialog.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>额度与赠送</h2>
        <p class="muted">赠送额度先于免费额度抵扣（R13），到期自动失效</p>
      </div>
      <el-button type="primary" @click="dialog = true">发放赠送额度</el-button>
    </div>
    <el-card class="block">
      <el-table :data="grants" size="small" v-loading="loading">
        <template #empty><el-empty description="暂无赠送记录" :image-size="60" /></template>
        <el-table-column prop="grantNo" label="赠送单号" min-width="160" />
        <el-table-column label="租户" width="90"><template #default="{ row }">#{{ row.tenantId }}</template></el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="productMetric" label="指标" width="110" />
        <el-table-column label="总量 / 余量" width="150">
          <template #default="{ row }">{{ fmtAmount(row.total) }} / {{ fmtAmount(row.remaining) }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="到期日" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'active' ? 'success' : 'info'">{{ row.status === 'active' ? '生效中' : row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150"><template #default="{ row }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="发放赠送额度" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="租户 ID">
          <el-input-number v-model="form.tenantId" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="指标 Key" prop="productMetric">
          <el-input v-model="form.productMetric" placeholder="如 out_tok" />
        </el-form-item>
        <el-form-item label="赠送量" prop="amount">
          <el-input-number v-model="form.amount" :min="1" :step="10000" />
        </el-form-item>
        <el-form-item label="到期日" prop="expireDate">
          <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
@import '../tenant/page.css';
</style>
