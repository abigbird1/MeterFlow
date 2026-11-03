/* ============================================================
   MeterFlow 原型 · 工具库（格式化 / 弹窗 / Toast / 步骤条 / 图表）
   ============================================================ */
'use strict';

/* ---------- 格式化 ---------- */
function fmtMoney(n, d){
  d = (d===undefined) ? 2 : d;
  const neg = n < 0;
  const v = Math.abs(n).toLocaleString('zh-CN',{minimumFractionDigits:d, maximumFractionDigits:d});
  return (neg?'-':'') + '¥' + v;
}
function fmt4(n){ return Number(n).toLocaleString('zh-CN',{minimumFractionDigits:4, maximumFractionDigits:4}); }
function fmtInt(n){ return Number(n).toLocaleString('zh-CN'); }
function esc(s){ return String(s).replace(/[&<>"]/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }
function val(id){ const e=document.getElementById(id); return e ? e.value.trim() : ''; }
function nowStr(){ return '2026-11-03 ' + new Date().toTimeString().slice(0,8); }

/* ---------- 徽标 ---------- */
function badge(text, color){ return `<span class="badge b-${color}">${esc(text)}</span>`; }
function statusBadge(status){
  const map = {
    'normal':['正常','green'],'active':['生效中','green'],'enabled':['启用','green'],'on':['上架','green'],
    'warning':['预警 · 产品停用','orange'],'suspended':['已全停 · 催缴中','red'],'disabled':['已停用','gray'],
    'stopped':['停用','red'],'off':['下架','gray'],
    'pending':['待支付','orange'],'paid':['已支付','green'],'closed':['已关闭','gray'],'canceled':['已取消','gray'],
    'exhausted':['已耗尽','gray'],'expired':['已过期','red'],
    'draft':['已出账','blue'],'confirmed':['已确认','blue'],'settled':['已结清','green'],
    'running':['进行中','teal'],
    'ok':['平','green'],'err':['差错','red'],
    'todo':['待处理','orange'],'correcting':['冲正中','blue'],'corrected':['已冲正','green'],'manual':['转人工','purple'],
    'review1':['待初审','orange'],'review2':['待复审','blue'],'done':['已生效','green'],'rejected':['已驳回','red'],
    'processing':['处理中','blue'],'solved':['已解决','green'],'escalated':['已升级','red'],
    'rotating':['轮换中','orange'],'granted':['已发放','green']
  };
  const m = map[status] || [status,'gray'];
  return badge(m[0], m[1]);
}

/* ---------- 通用动作委托 ---------- */
const ACTIONS = {};
document.addEventListener('click', e=>{
  const t = e.target.closest('[data-act]');
  if(!t) return;
  const fn = ACTIONS[t.dataset.act];
  if(fn){ e.preventDefault(); fn(t.dataset, t, e); }
});

/* ---------- Toast ---------- */
function showToast(msg, type){
  type = type || 'success';
  const root = document.getElementById('toast-root');
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  el.innerHTML = `<span>${type==='error'?'⛔':type==='warn'?'⚠️':'✅'}</span><span>${msg}</span>`;
  root.appendChild(el);
  setTimeout(()=>{ el.style.opacity='0'; el.style.transition='.3s'; setTimeout(()=>el.remove(),320); }, 2600);
}

/* ---------- 弹窗 ---------- */
function openModal(title, bodyHtml, footerHtml, size){
  closeModal();
  const root = document.getElementById('modal-root');
  root.innerHTML = `
    <div class="modal-overlay">
      <div class="modal ${size||''}">
        <div class="modal-h"><h3>${title}</h3><button class="modal-x" data-act="_closeModal">✕</button></div>
        <div class="modal-b" id="modal-body">${bodyHtml}</div>
        ${footerHtml ? `<div class="modal-f" id="modal-foot">${footerHtml}</div>` : ''}
      </div>
    </div>`;
  const overlay = root.querySelector('.modal-overlay');
  overlay.addEventListener('click', e=>{ if(e.target === overlay) closeModal(); });
}
function closeModal(){ document.getElementById('modal-root').innerHTML=''; }
ACTIONS['_closeModal'] = ()=>closeModal();
ACTIONS['_overlayClose'] = ()=>closeModal();
document.addEventListener('keydown', e=>{ if(e.key==='Escape') closeModal(); });

function confirmDialog(title, text, okLabel, onOk, danger){
  window.__confirmOk = onOk;
  openModal(title, `<p style="font-size:13.5px;line-height:1.7">${text}</p>`,
    `<button class="btn" data-act="_closeModal">取消</button>
     <button class="btn ${danger?'danger':'primary'}" data-act="_doConfirm">${okLabel||'确认'}</button>`);
}
ACTIONS['_doConfirm'] = ()=>{ const f=window.__confirmOk; closeModal(); if(f) f(); };

/* ---------- 倒计时（订单支付超时等） ---------- */
const COUNTDOWNS = [];
function registerCountdown(elId, endTime, onEnd){
  COUNTDOWNS.push({elId, endTime, onEnd, done:false});
}
function tickCountdowns(){
  const now = Date.now();
  COUNTDOWNS.forEach(c=>{
    if(c.done) return;
    const el = document.getElementById(c.elId);
    if(!el){ return; }
    let left = Math.max(0, Math.floor((c.endTime - now)/1000));
    const h = String(Math.floor(left/3600)).padStart(2,'0');
    const m = String(Math.floor(left%3600/60)).padStart(2,'0');
    const s = String(left%60).padStart(2,'0');
    el.textContent = (h!=='00' ? h+':' : '') + m + ':' + s;
    el.classList.toggle('danger', left < 300);
    if(left<=0){ c.done=true; if(c.onEnd) c.onEnd(); }
  });
}
setInterval(tickCountdowns, 1000);

/* ---------- 状态步骤条 ---------- */
function stepsHTML(labels, currentIdx){
  let html = '<div class="steps">';
  labels.forEach((lb,i)=>{
    const cls = i<currentIdx ? 'done' : i===currentIdx ? 'current' : '';
    html += `<div class="step ${cls}"><div class="dot2">${i<currentIdx?'✓':i+1}</div><div class="s-lbl">${lb}</div></div>`;
    if(i<labels.length-1) html += `<div class="step-line ${i<currentIdx?'done':''}"></div>`;
  });
  return html + '</div>';
}

/* ---------- 抵扣链 ---------- */
function chainHTML(chain){
  if(!chain || !chain.length) return '<div class="note-strip">无抵扣链数据</div>';
  return '<div class="chain">' + chain.map(s=>`
    <div class="chain-seg ${s.cls}">
      <div class="ord">${s.label.slice(0,1)}</div>
      <div><div class="b">${esc(s.label.replace(/^[①②③④⑤]\s*/,''))}</div>
      ${s.note?`<div class="cs-note">${esc(s.note)}</div>`:''}</div>
      <div class="cs-amt ${s.amount<0?'red':''}">${s.amount>0?'−':''}${fmtMoney(Math.abs(s.amount), Math.abs(s.amount)%1?4:2)}</div>
    </div>`).join('<div class="chain-arrow">▼ 未覆盖部分继续向下抵扣</div>') + '</div>';
}

/* ---------- 柱状图（纯 CSS） ---------- */
function barsHTML(data, unit){
  const max = Math.max(...data.map(d=>d.value), 1);
  return `<div class="bars">` + data.map(d=>`
    <div class="bar-col" title="${d.label}：${fmtMoney(d.value)}">
      <div class="bar-v">${d.value>=10?Math.round(d.value):d.value.toFixed(1)}</div>
      <div class="bar" style="height:${Math.max(3, d.value/max*100)}%"></div>
      <div class="bar-l">${d.label}</div>
    </div>`).join('') + `</div><div class="legend mt8"><span>单位：${unit||'元'} / 日</span></div>`;
}

/* ---------- CSV 导出 ---------- */
function exportCSV(filename, header, rows){
  const esc2 = v => `"${String(v==null?'':v).replace(/"/g,'""')}"`;
  const csv = '\uFEFF' + [header, ...rows].map(r=>r.map(esc2).join(',')).join('\r\n');
  const blob = new Blob([csv], {type:'text/csv;charset=utf-8'});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
  showToast('已导出 ' + filename);
}

/* ---------- 数据写入辅助 ---------- */
function pushAudit(action, target, scope, forced){
  const u = currentUser();
  const roleName = SESSION.view==='admin' ? DB.platformRoleNames[u.role] : {admin:'管理员',finance:'财务',member:'成员',viewer:'只读成员'}[u.role];
  DB.audit.unshift({at:nowStr(), actor:u.name, role:roleName, action, target, scope:scope||'—', result:'成功', forced:!!forced});
}
function pushNotify(type, title, text){
  DB.notifications.unshift({id:'n'+(DB.seq++), type, title, text, at:nowStr(), read:false});
}

/* ---------- 身份与权限 ---------- */
const SESSION = { view:'login', tenantUserId:'u1', platformUserId:'p1', onboard:false };
function currentUser(){
  if(SESSION.view==='admin') return DB.platformUsers.find(u=>u.id===SESSION.platformUserId);
  return DB.tenantUsers.find(u=>u.id===SESSION.tenantUserId);
}
const TENANT_PERMS = {
  admin:  ['wallet','packages','bills:view','bills:detail','bills:export','disputes','org','keys'],
  finance:['wallet','packages','bills:view','bills:detail','bills:export','disputes'],
  member: ['bills:view','bills:detail'],
  viewer: []
};
function tCan(perm){
  const u = currentUser();
  if(!u) return false;
  if(u.role==='admin') return true;
  return (TENANT_PERMS[u.role]||[]).includes(perm);
}
/* 部门级数据隔离：成员/只读成员仅本部门；管理员全租户 */
function visibleDepts(){
  const u = currentUser();
  if(u.role==='admin') return null;              // null = 不限制
  if(u.role==='viewer') return null;             // 只读成员看汇总
  return u.dept;
}
function visibleEvents(){
  const d = visibleDepts();
  if(d===null){
    if(currentUser().role==='viewer') return DB.events.filter(e=>true).map(e=>({...e, sum:'（汇总不可见明细）', cost:null, status:'hidden'}));
    return DB.events;
  }
  return DB.events.filter(e=>e.dept===deptName(d));
}
function deptName(id){ const d=DB.depts.find(x=>x.id===id); return d?d.name.replace('（总部）',''):id; }

/* 平台侧权限矩阵（需求文档 6.2） */
const ADMIN_PERMS = {
  sadmin:  null,  // null = 全部
  ops:     ['tenants:view','products','goods','grants','orders:view','dashboard'],
  cs:      ['tenants:view','disputes','dashboard','compensate:grant'],
  reviewer:['recon:view','recon:review1','dashboard'],
  fin:     ['tenants:view','billing','recon:view','recon:init','recon:review2','grants:review2','orders:view','whitelist','dashboard','compensate:refund'],
  audit:   ['audit','dashboard']
};
function aCan(perm){
  const u = currentUser();
  if(!u) return false;
  const list = ADMIN_PERMS[u.role];
  if(list===null) return true;
  return list.includes(perm);
}

/* 通用局部刷新 */
function rerender(){ render(); }
function refreshContent(){ render(); }
