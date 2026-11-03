/* ============================================================
   MeterFlow 原型 · 应用壳：登录 / 双端切换 / 角色切换 / 路由
   ============================================================ */
'use strict';

function nav(hash){ location.hash = hash; }

function roleOf(acc){
  return SESSION.view==='tenant'
    ? ({admin:'管理员',finance:'财务',member:'成员',viewer:'只读成员'})[acc.role]
    : DB.platformRoleNames[acc.role];
}

/* ---------- 登录页 ---------- */
function renderLogin(){
  document.body.dataset.end = 'login';
  const tenantAccounts = [
    {id:'u1', name:'张伟', role:'管理员', desc:'全租户数据 · 全部功能'},
    {id:'u2', name:'李娜', role:'财务',   desc:'充值 / 账单 / 争议'},
    {id:'u3', name:'王芳', role:'成员 · 研发部', desc:'仅本部门数据'},
    {id:'u4', name:'赵磊', role:'只读成员', desc:'仅汇总，无明细'}
  ];
  const platformAccounts = [
    {id:'p1', name:'系统管理员', role:'管理员', desc:'全部权限'},
    {id:'p2', name:'钱运', role:'运营', desc:'产品 / 商品 / 额度'},
    {id:'p3', name:'孙倩', role:'客服', desc:'争议 / 代查'},
    {id:'p4', name:'周审', role:'审核员', desc:'调整单初审'},
    {id:'p5', name:'吴财', role:'财务', desc:'对账 / 复审 / 催缴'},
    {id:'p6', name:'郑明', role:'审计员', desc:'审计日志只读'}
  ];
  document.getElementById('root').innerHTML = `
  <div class="login-page"><div class="login-wrap">
    <div class="login-hero">
      <div class="logo-big"><span class="logo-mark">⚡</span>MeterFlow</div>
      <p class="sub">多租户计量计费与权益中台 · 可交互页面原型（纯前端 · 模拟数据）</p>
      <ul>
        <li>用量事件上报 → 双保险判重 → 实时逐笔计价</li>
        <li>抵扣优先级链：赠送额度 → 内置额度 → 资源包 → 余额</li>
        <li>自然月账单 + 每日日切 · 三方对账 · 调整单两级审批</li>
        <li>限量资源包抢购：总库存 + 限购 + 单笔上限，下单锁库存超时释放</li>
        <li>部门级数据隔离 · 六岗位权限矩阵 · 敏感操作全留痕</li>
      </ul>
      <div class="demo-tip">💡 建议体验路径：① 租户端「模拟一笔用量上报」看抵扣链 → ② 资源包抢购（支付/超时释放）→ ③ 余额充值恢复停服 → ④ 平台端对账中心走一次「初审→复审」→ ⑤ 切换角色看数据隔离与权限差异</div>
    </div>
    <div class="login-panel">
      <div class="login-card">
        <h3>租户控制台 · 演示身份</h3>
        <div class="small muted">星澜科技（余额 -¥37.50，透支预警演示态）</div>
        <div class="demo-accounts">${tenantAccounts.map(a=>`
          <button class="demo-chip" data-act="login" data-end="tenant" data-uid="${a.id}">
            <b>${a.name} · ${a.role}</b><span>${a.desc}</span></button>`).join('')}</div>
      </div>
      <div class="login-card">
        <h3>管理后台 · 演示身份（六岗位）</h3>
        <div class="small muted">菜单与操作按权限矩阵（需求文档 6.2）过滤</div>
        <div class="demo-accounts">${platformAccounts.map(a=>`
          <button class="demo-chip" data-act="login" data-end="admin" data-uid="${a.id}">
            <b>${a.name} · ${a.role}</b><span>${a.desc}</span></button>`).join('')}</div>
      </div>
      <div class="login-card">
        <div class="flex">
          <div class="flex1"><h3>注册新租户</h3><div class="small muted">演示「注册即开通 + 内置额度自动发放」</div></div>
        </div>
        <div class="flex mt8">
          <input id="reg-email" placeholder="企业邮箱" style="flex:1;border:1px solid var(--line2);border-radius:8px;padding:8px 11px">
          <button class="btn primary" data-act="regTenant">注册开通</button>
        </div>
      </div>
    </div>
  </div></div>`;
}
ACTIONS['login'] = d=>{
  if(d.end==='tenant'){ SESSION.view='tenant'; SESSION.tenantUserId=d.uid; nav('#/t/overview'); }
  else { SESSION.view='admin'; SESSION.platformUserId=d.uid; nav('#/a/dashboard'); }
  render();
  showToast('已进入' + (d.end==='tenant'?'租户控制台':'管理后台') + '，身份：' + currentUser().name);
};
ACTIONS['regTenant'] = ()=>{
  const email = val('reg-email');
  if(!email){ showToast('请输入企业邮箱（模拟）','warn'); return; }
  SESSION.view='tenant'; SESSION.tenantUserId='u1'; SESSION.onboard=true;
  nav('#/t/overview'); render();
  showToast('注册成功：租户已自动开通，内置免费额度已发放');
};

/* ---------- 应用壳 ---------- */
function menuFor(view){
  const pages = view==='tenant' ? TENANT_PAGES : ADMIN_PAGES;
  const can  = view==='tenant' ? tCan : aCan;
  const u = currentUser();
  const sec = view==='tenant' ? '星澜科技 · 租户控制台' : 'MeterFlow · 管理后台';
  const items = Object.values(pages).filter(p=>!p.perm || can(p.perm));
  return {sec, items};
}
function renderApp(){
  const view = SESSION.view;
  document.body.dataset.end = view;
  const {sec, items} = menuFor(view);
  const hash = location.hash || (view==='tenant'?'#/t/overview':'#/a/dashboard');
  let page = Object.values(view==='tenant'?TENANT_PAGES:ADMIN_PAGES).find(p=>p.hash===hash);
  if(!page) page = Object.values(view==='tenant'?TENANT_PAGES:ADMIN_PAGES)[0];
  if(page.perm && !(view==='tenant'?tCan:aCan)(page.perm)) page = Object.values(view==='tenant'?TENANT_PAGES:ADMIN_PAGES)[0];

  const u = currentUser();
  const roleLabel = view==='tenant'
    ? ({admin:'管理员',finance:'财务',member:'成员',viewer:'只读成员'})[u.role]
    : DB.platformRoleNames[u.role];
  const unread = DB.notifications.filter(n=>!n.read).length;

  const accounts = view==='tenant' ? DB.tenantUsers : DB.platformUsers;

  document.getElementById('root').innerHTML = `
  <div class="app">
    <aside class="sidebar">
      <div class="sb-logo"><span class="logo-mark">⚡</span><span>MeterFlow</span>
        <span class="sb-end">${view==='tenant'?'租户端':'平台端'}</span></div>
      <div class="nav-sec">${esc(sec)}</div>
      ${items.map(p=>`<button class="nav-item ${p===page?'active':''}" data-act="go" data-to="${p.hash}">
        <span class="ico">${p.icon}</span><span class="lbl">${p.label}</span></button>`).join('')}
      <div class="sb-foot">
        <button class="nav-item" data-act="switchEnd"><span class="ico">🔁</span><span class="lbl">切换到${view==='tenant'?'管理后台':'租户控制台'}</span></button>
        <div class="sb-user"><div class="avatar">${u.name[0]}</div>
          <div class="meta"><b>${esc(u.name)}</b><span>${roleLabel} · 演示身份</span></div></div>
        <button class="nav-item" data-act="logout"><span class="ico">🚪</span><span class="lbl">退出登录</span></button>
      </div>
    </aside>
    <div class="main">
      <div class="topbar">
        <span class="crumb">${page.title}<small>MeterFlow 原型 · 交互演示</small></span>
        <div class="top-actions">
          <button class="btn sm" data-act="guideDlg">📖 演示指引</button>
          <button class="btn sm" data-act="switchRoleDlg">🧑‍💼 切换演示身份（${roleLabel}）</button>
          ${view==='tenant'?`<button class="icon-btn" data-act="go" data-to="#/t/notifications">🔔${unread?`<span class="dot">${unread}</span>`:''}</button>`:''}
        </div>
      </div>
      <div class="content" id="page-content"></div>
    </div>
  </div>
  <div class="float-demo"><button class="btn primary sm" data-act="guideDlg">💡 演示指引</button></div>`;
  document.getElementById('page-content').innerHTML = page.render();
  if(page.after) page.after();
}
ACTIONS['go'] = d=>{ nav(d.to); };
ACTIONS['logout'] = ()=>{ SESSION.view='login'; nav(''); renderLogin(); };
ACTIONS['switchEnd'] = ()=>{
  SESSION.view = SESSION.view==='tenant'?'admin':'tenant';
  nav(SESSION.view==='tenant'?'#/t/overview':'#/a/dashboard');
  render();
  showToast('已切换到' + (SESSION.view==='tenant'?'租户控制台':'管理后台') + '（身份：' + currentUser().name + '）');
};
ACTIONS['switchRoleDlg'] = ()=>{
  const view = SESSION.view;
  const accounts = view==='tenant' ? DB.tenantUsers : DB.platformUsers;
  const cur = currentUser();
  openModal('切换演示身份', `
    <div class="note-strip mb16">${view==='tenant'?'切换后立即以所选身份查看租户控制台（注意数据隔离差异与菜单权限差异）':'切换后立即以所选岗位查看管理后台（注意菜单与操作按钮的权限差异）'}</div>
    <div class="demo-accounts" style="grid-template-columns:1fr 1fr">
      ${accounts.map(a=>`<button class="demo-chip ${a.id===cur.id?'':''}" data-act="switchRole" data-uid="${a.id}">
        <b>${a.name} · ${roleOf(a)}</b><span>${a.email}</span></button>`).join('')}
    </div>`,
    `<button class="btn" data-act="_closeModal">取消</button>`);
};
ACTIONS['switchRole'] = d=>{
  if(SESSION.view==='tenant') SESSION.tenantUserId = d.uid; else SESSION.platformUserId = d.uid;
  closeModal(); render();
  showToast('已切换身份：' + currentUser().name);
};

/* ---------- 演示指引 ---------- */
ACTIONS['guideDlg'] = ()=>{
  openModal('📖 演示指引 · 关键交互走查', `
    <div class="callout info mb16">本原型覆盖需求文档（requirements.md v1.0）中两端的 22 个功能模块与全部关键业务规则。以下路径建议按序体验：</div>
    <h4 class="mb8">租户控制台</h4>
    <ul class="tl" style="margin-bottom:14px">
      <li><b>模拟一笔用量上报</b>（总览页右上角）：查看逐笔计价明细与<b>抵扣优先级链</b>（赠送→内置→资源包→余额）；对停服中的产品上报会被拒收（METER_SUSPENDED）。</li>
      <li><b>资源包中心</b>：抢购下单（库存锁定）→「模拟超时」看库存自动释放，或支付发放实例；「我的资源包」查看余量、到期提醒与抵扣留痕。</li>
      <li><b>余额与充值</b>：当前余额 -¥37.50 处于分级停服预警（LLM 推理 API 已停用）；充值 ¥200 回正后观察横幅与停服恢复。</li>
      <li><b>账单与明细</b>：账单详情看「原额+调整=应付」、按版本分段、部门小计；消费明细展开抵扣链、导出 CSV；对账单发起争议。</li>
      <li><b>API Key / 组织与角色</b>：创建 Key 绑定部门；切换成员/只读成员身份看<b>部门级数据隔离</b>。</li>
    </ul>
    <h4 class="mb8">管理后台（六岗位权限差异）</h4>
    <ul class="tl">
      <li><b>产品与计价</b>：运营身份调价 → 生成新版本即时生效（版本日志可见）。</li>
      <li><b>对账中心</b>：财务发起冲正生成调整单 → 切「周审（审核员）」初审 → 切「吴财（财务）」复审生效（初审≠复审同岗）→ 差错单转「已冲正」。</li>
      <li><b>争议工单</b>：客服受理 → 代查（审计留痕）→ 发起补偿（退费调整单 / 赠送额度单）。</li>
      <li><b>账单管理</b>：执行日切、为本账期出账、账单确认/结清、欠费催缴。</li>
      <li><b>审计日志</b>：所有敏感操作（代查/冲正/停服/调价/额度/白名单）均带「敏感」标记。</li>
    </ul>`,
    `<button class="btn primary" data-act="_closeModal">开始体验</button>`, 'lg');
};

/* ---------- 路由入口 ---------- */
function render(){
  if(SESSION.view==='login' || (!location.hash.startsWith('#/t') && !location.hash.startsWith('#/a'))){
    renderLogin(); return;
  }
  renderApp();
}
window.addEventListener('hashchange', ()=>{
  if(SESSION.view==='login') return;
  render();
});
render();
