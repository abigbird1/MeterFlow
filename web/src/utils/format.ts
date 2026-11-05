/**
 * 统一格式化（meterflow.md #7.8）：
 * - 确定性金额 *_cents（分）→ 前端格式化为元
 * - 过程金额（4 位小数）后端以字符串出参，原样展示避免浮点误差
 */

/** 分 → ¥千分位（2 位小数） */
export function fmtCents(cents: any): string {
  if (cents == null || cents === '') return '—'
  const n = Number(cents)
  if (Number.isNaN(n)) return '—'
  const sign = n < 0 ? '-' : ''
  return sign + '¥' + (Math.abs(n) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 过程金额字符串（元，4 位小数）原样展示 */
export function fmtAmount(v: any): string {
  return v == null || v === '' ? '—' : '¥' + v
}

/** 后端 LocalDateTime 序列化（ISO）→ yyyy-MM-dd HH:mm:ss 展示 */
export function fmtTime(t: any): string {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

/** 通用状态中文映射（账单/事件/订单/Key 等共用） */
export const STATUS_TEXT: Record<string, string> = {
  // 账单
  draft: '待确认', confirmed: '已确认', settled: '已结清', voided: '已作废',
  // 事件
  priced: '已计价', priced_free: '免费额度抵扣', priced_pkg: '资源包抵扣', priced_gift: '赠送额度抵扣',
  rejected: '已拒收', errored: '计价失败',
  // 订单
  created: '待支付', paid: '已支付', canceled: '已取消', expired: '超时释放',
  // Key
  enabled: '启用', disabled: '禁用',
  // 争议
  open: '待受理', accepted: '已受理', replied: '已代查', compensated: '已补偿', closed: '已关闭',
  // 差错/调整单
  todo: '待初审', review2: '待复审', done: '已生效', rejected2: '已驳回',
  // 导出任务
  pending: '生成中', finished: '已完成', failed: '失败',
  // 实例/赠额
  active: '生效中', exhausted: '已用尽', expired2: '已过期',
}

/** 状态 → el-tag 类型 */
export function statusType(s: any): 'success' | 'warning' | 'danger' | 'info' {
  const key = String(s ?? '')
  if (['settled', 'priced', 'paid', 'enabled', 'done', 'finished', 'active', 'closed', 'compensated'].includes(key)) return 'success'
  if (['draft', 'created', 'todo', 'review2', 'pending', 'accepted', 'replied', 'priced_free', 'priced_pkg', 'priced_gift'].includes(key)) return 'warning'
  if (['rejected', 'errored', 'voided', 'failed', 'expired2'].includes(key)) return 'danger'
  return 'info'
}

/** 复制文本到剪贴板（明文 Key 一次性提示场景） */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  }
}
