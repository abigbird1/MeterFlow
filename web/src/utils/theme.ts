/** 主题切换（暗色/亮色）：持久化 localStorage，html.dark 驱动 Element Plus 与自定义变量层 */
export type Theme = 'light' | 'dark'

export function currentTheme(): Theme {
  return localStorage.getItem('mf-theme') === 'dark' ? 'dark' : 'light'
}

export function applyTheme(t: Theme) {
  localStorage.setItem('mf-theme', t)
  document.documentElement.classList.toggle('dark', t === 'dark')
  window.dispatchEvent(new CustomEvent('mf-theme-change', { detail: t }))
}

export function toggleTheme(): Theme {
  const next: Theme = currentTheme() === 'dark' ? 'light' : 'dark'
  applyTheme(next)
  return next
}

/** 应用启动时恢复持久化主题 */
export function initTheme() {
  document.documentElement.classList.toggle('dark', currentTheme() === 'dark')
}

/** echarts 主题感知配色（canvas 无法用 CSS 变量，渲染时取当前主题色） */
export function chartPalette() {
  const dark = currentTheme() === 'dark'
  return {
    axisLabel: dark ? '#7c8db0' : '#64748b',
    splitLine: dark ? '#1e293b' : '#e2e8f0',
    tooltipBg: dark ? '#10192e' : '#fff',
    tooltipBorder: dark ? '#253352' : '#e2e8f0',
    tooltipText: dark ? '#cbd5e1' : '#475569',
  }
}
