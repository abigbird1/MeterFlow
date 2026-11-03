/* ============================================================
   MeterFlow 原型 · 管理后台（平台端 11 个功能模块，六岗位权限）
   ============================================================ */
'use strict';

const AProdTab = {cur:'list'};
const ABillTab = {cur:'tasks'};
const AReconTab = {cur:'daily'};
const ADspFilter = {status:''};

function adminNote(){ return `<div class="note-strip mb16">当前演示身份：<b>${esc(currentUser().name)}</b>（${DB.platformRoleNames[currentUser().role]}）· 页面与操作按钮已按权限矩阵（需求文档 6.2）过滤；可通过顶栏「切换演示身份」体验其他岗位。</div>`; }

/* ============================================================
   数据看板
   ============================================================ */
function pageDash(){
  return `
  <div class="page-title">数据看板</div><div class="page-sub">平台运营与财务总览（模拟数据）</div>${adminNote()}
  <div class="grid g4 mb16">
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:var(--green-bg)">💰</div><div><div class="s-val">¥86,420</div><div class="s-lbl">本月充值收入</div></div></div></div></div>
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:var(--purple-bg)">📦</div><div><div class="s-val">¥31,258</div><div class="s-lbl">资源包销售额</div></div></div></div></div>
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:var(--blue-bg)">🏢</div><div><div class="s-val">23 / 500</div><div class="s-lbl">活跃租户</div></div></div></div></div>
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:var(--red-bg)">⛔</div><div><div class="s-val">2</div><div class="s-lbl">欠费/停服租户</div></div></div></div></div>
  </div>
  <div class="grid g2">
    <div class="card"><div class="card-h"><h3>近 7 日平台事件量（万条）</h3></div><div class="card-b">
      ${barsHTML(DB.dailyUsage.map(d=>({label:d.label, value:+(d.value*380/100).toFixed(1)})),'万条/日')}</div></div>
    <div class="card"><div class="card-h"><h3>平台健康 · 昨日日切与对账</h3></div><div class="card-b">
      <dl class="kv">
        <dt>日切任务</dt><dd>${badge('2026-11-03 00:05 成功','green')} · 覆盖事件 12,847</dd>
        <dt>每日对账</dt><dd>计量vs计费 ${badge('差 1 笔','red')} · 计费vs账户 ${badge('平','green')}</dd>
        <dt>差错积压</dt><dd>${DB.errors.filter(e=>e.status==='todo'||e.status==='correcting'||e.status==='manual').length} 笔待处理</dd>
        <dt>调整单</dt><dd>待初审 1 · 待复审 0 · 近 30 天生效 2</dd>
        <dt>争议工单</dt><dd>待受理 1 · 处理中 1 · 1 笔已超时升级</dd>
      </dl></div></div>
  </div>`;
}

/* ============================================================
   租户管理
   ============================================================ */
function pageTenants(){
  const rows = DB.tenants.map(t=>`
    <tr><td class="b">${esc(t.name)}</td><td class="small muted">${t.members} 人 / ${t.keys} Key</td>
    <td class="num ${t.balance<0?'red':''} b">${fmtMoney(t.balance)}</td>
    <td class="num">${fmtMoney(t.monthSpend)}</td>
    <td>${statusBadge(t.status)}${t.whitelist?' '+badge('白名单','purple'):''}</td>
    <td class="nowrap">
      ${aCan('tenants:view')?`<button class="btn sm" data-act="a_tenantDetail" data-id="${t.id}">详情</button>`:''}
      ${aCan('whitelist')?`<button class="btn sm ghost" data-act="a_whitelistDlg" data-id="${t.id}">白名单</button>`:''}
      ${(currentUser().role==='sadmin'||currentUser().role==='ops')?`<button class="btn sm ${t.status==='disabled'?'':'danger'}" data-act="a_tenantDisable" data-id="${t.id}">${t.status==='disabled'?'恢复':'停用'}</button>`:''}
    </td></tr>`).join('');
  return `<div class="page-title">租户管理</div><div class="page-sub">租户状态机：正常 → 预警（产品级停用）→ 已停服（全停+催缴）；平台可手动停用（全站拒收，留痕）</div>${adminNote()}
  <div class="card"><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>租户</th><th>规模</th><th class="tr">余额</th><th class="tr">本月消费</th><th>状态</th><th>操作</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>`;
}
ACTIONS['a_tenantDetail'] = d=>{
  const t = DB.tenants.find(x=>x.id===d.id);
  openModal('租户详情 · '+esc(t.name), `
    <dl class="kv mb16"><dt>状态</dt><dd>${statusBadge(t.status)}${t.whitelist?' '+badge('白名单','purple'):''}</dd>
    <dt>余额</dt><dd class="${t.balance<0?'red':''} b">${fmtMoney(t.balance)}（透支阈值 ${fmtMoney(t.overdraft)}）</dd>
    <dt>本月消费</dt><dd>${fmtMoney(t.monthSpend)}</dd>
    <dt>规模</dt><dd>${t.members} 名成员 / ${t.keys} 个 API Key</dd></dl>
    ${t.status==='warning'?`<div class="callout warn mb16">处于分级停服预警：已停用其本月消费最高产品，宽限期 3 天；逾期未充值将全停并进入催缴。</div>`:''}
    ${t.status==='suspended'?`<div class="callout danger mb16">已全停并进入催缴流程：全部产品调用拒收（METER_SUSPENDED）。</div>`:''}
    <div class="note-strip">代查、停用/恢复、白名单调整均自动记录审计日志。</div>`,
    `<button class="btn primary" data-act="_closeModal">关闭</button>`);
};
ACTIONS['a_tenantDisable'] = d=>{
  const t = DB.tenants.find(x=>x.id===d.id);
  const disable = t.status!=='disabled';
  confirmDialog(disable?'停用租户':'恢复租户',
    disable?`停用后 <b>${esc(t.name)}</b> 全部产品调用与事件上报将被拒收（全站拒收），操作留痕。确定停用？`:`恢复 <b>${esc(t.name)}</b> 的平台服务？`,
    disable?'确认停用':'确认恢复', ()=>{
      t.status = disable?'disabled':(t.balance<0?(t.balance<=t.overdraft?'suspended':'warning'):'normal');
      pushAudit(disable?'停用租户':'恢复租户', t.name, disable?'全站拒收':'恢复服务', true);
      showToast(disable?'租户已停用（已留痕）':'租户已恢复（已留痕）'); refreshContent();
    }, disable);
};
ACTIONS['a_whitelistDlg'] = d=>{
  const t = DB.tenants.find(x=>x.id===d.id);
  openModal('透支白名单 · '+esc(t.name), `
    <div class="field"><label>单独透支阈值（元）</label><input id="wl-val" type="number" value="${t.overdraft}"></div>
    <label class="check"><input type="checkbox" id="wl-on" ${t.whitelist?'checked':''}> 加入白名单（覆盖平台统一阈值 -100 元）</label>
    <div class="note-strip mt8">白名单调整属敏感操作，保存后自动记录审计日志。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="a_whitelistSave" data-id="${t.id}">保存</button>`);
};
ACTIONS['a_whitelistSave'] = d=>{
  const t = DB.tenants.find(x=>x.id===d.id);
  t.whitelist = document.getElementById('wl-on').checked;
  t.overdraft = +val('wl-val')||-100;
  pushAudit('透支白名单调整', t.name, (t.whitelist?'白名单生效 · ':'移出白名单 · ')+'阈值 '+fmtMoney(t.overdraft), true);
  closeModal(); showToast('白名单已更新（已留痕）'); refreshContent();
};

/* ============================================================
   产品目录与计价方案
   ============================================================ */
function pageProducts(){
  const tab = AProdTab.cur;
  return `<div class="page-title">产品目录与计价方案</div><div class="page-sub">产品 → 计量指标 → 计价项（单价/阶梯 + 内置免费额度）· 价格版本即时生效、账期内分段计费</div>${adminNote()}
  <div class="tabs">
    <button class="tab ${tab==='list'?'active':''}" data-act="a_prodTab" data-tab="list">产品与计价</button>
    <button class="tab ${tab==='ver'?'active':''}" data-act="a_prodTab" data-tab="ver">价格版本日志</button>
  </div>
  ${tab==='list'?prodList():prodVersions()}`;
}
ACTIONS['a_prodTab'] = d=>{ AProdTab.cur=d.tab; refreshContent(); };

function prodList(){
  const cards = DB.products.map(p=>{
    const ver = DB.currentVersion(p);
    const canAdj = aCan('products');
    return `<div class="card"><div class="card-h"><h3>${esc(p.name)}</h3><span class="right">${p.status==='on'?badge('上架','green'):badge('下架','gray')} ${badge('当前 '+ver,'indigo')}</span></div>
    <div class="card-b flush"><div class="table-wrap"><table class="table">
      <thead><tr><th>计量指标</th><th class="tr">当前单价</th><th>阶梯模式</th><th class="tr">内置免费额度</th><th></th></tr></thead>
      <tbody>${p.metrics.map(m=>{
        const t = m.tier;
        const tierTxt = t.mode==='flat' ? badge('档位单价','gray') : badge('累计分段 '+t.seg.length+' 档','blue');
        return `<tr><td class="b">${m.name}<span class="tiny muted"> / ${m.unit}</span></td>
        <td class="num tr">${fmt4(m.prices[ver])} 元/${m.unit}</td><td>${tierTxt}</td>
        <td class="num tr">${fmtInt(m.free)} ${m.freeUnit} <span class="tiny muted">(余 ${fmtInt(m.freeRemain)})</span></td>
        <td>${canAdj?`<button class="btn sm" data-act="a_adjustDlg" data-p="${p.id}" data-m="${m.key}">调价</button>`:''}</td></tr>`;
      }).join('')}</tbody></table></div></div></div>`;
  }).join('');
  return `<div class="grid g2">${cards}
  <div class="callout" style="grid-column:1/-1">调价即生成<b>新价格版本</b>并即时生效：生效时刻起的新用量适用新价，此前用量保持旧价，账单按版本分段呈现（R11）。${aCan('products')?'':'（当前岗位无调价权限）'}</div></div>`;
}
function prodVersions(){
  return DB.products.map(p=>`
    <div class="card mb16"><div class="card-h"><h3>${esc(p.name)}</h3><span class="right small muted">当前 ${DB.currentVersion(p)}</span></div>
    <div class="card-b"><ul class="tl">${p.versions.map((v,i)=>`
      <li class="${i===p.versions.length-1?'':'dim'}"><div><b>${v.v}</b> · ${esc(v.note)}</div><div class="tl-t">生效时间：${v.at}（即时生效）</div></li>`).join('')}</ul></div></div>`).join('');
}
ACTIONS['a_adjustDlg'] = d=>{
  const p = DB.products.find(x=>x.id===d.p);
  const m = p.metrics.find(x=>x.key===d.m);
  const curV = DB.currentVersion(p);
  openModal('调价 · '+esc(p.name)+' / '+m.name, `
    <dl class="kv mb16"><dt>当前版本</dt><dd>${curV} · 单价 ${fmt4(m.prices[curV])} 元/${m.unit}</dd>
    <dt>阶梯模式</dt><dd>${m.tier.mode==='flat'?'档位单价':'账期内累计分段'}</dd></dl>
    <div class="field"><label>新单价（元/${m.unit}）</label><input id="adj-price" type="number" step="0.0001" value="${m.prices[curV]}"></div>
    <div class="field"><label>调价说明</label><input id="adj-note" value="${m.name} 价格调整（运营调价演示）"></div>
    <div class="callout warn">生效时刻 = 保存时刻（<b>即时生效</b>）。账期内已计费用量保持旧价，新用量适用新价；日切快照不回溯。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="a_adjustSave" data-p="${p.id}" data-m="${m.key}">发布新版本</button>`);
};
ACTIONS['a_adjustSave'] = d=>{
  const p = DB.products.find(x=>x.id===d.p);
  const m = p.metrics.find(x=>x.key===d.m);
  const price = +val('adj-price');
  if(!(price>0)){ showToast('请输入有效单价','error'); return; }
  const nv = 'v'+(p.versions.length+1);
  const oldV = DB.currentVersion(p);
  m.prices[nv] = price;
  p.versions.push({v:nv, at:'2026-11-03 09:55:00', note:(val('adj-note')||`${m.name} ${fmt4(m.prices[oldV])} → ${fmt4(price)}`) + '（演示：即时生效）'});
  pushAudit('价格版本发布', `${p.name} ${nv}`, val('adj-note')+'，即时生效', true);
  pushNotify('info','价格版本发布', `${p.name} ${nv} 已即时生效：${val('adj-note')}`);
  closeModal(); showToast(`新版本 ${nv} 已发布并即时生效（账期内分段计费）`); refreshContent();
};

/* ============================================================
   资源包商品管理
   ============================================================ */
function pageGoods(){
  const rows = DB.goods.map(g=>`
    <tr><td><span class="tag ${g.type==='spec'?'':'hot'}">${g.type==='spec'?'指定包':'通用包'}</span> <span class="b">${esc(g.name)}</span><div class="tiny muted">${esc(g.spec)}</div></td>
    <td class="num b">${fmtMoney(g.price)}</td>
    <td style="min-width:140px"><div class="progress ${g.sold/g.stock>0.8?'hot':''}"><i style="width:${Math.round(g.sold/g.stock*100)}%"></i></div>
      <div class="tiny muted mt8">${fmtInt(g.sold)} / ${fmtInt(g.stock)} ${g.sold>=g.stock?'· 已售罄':''}</div></td>
    <td class="small">限购 ${g.perLimit} · 单笔 ${g.orderLimit}</td>
    <td class="small">${g.win}</td>
    <td>${statusBadge(g.status)}</td>
    <td class="nowrap">${aCan('goods')?`<button class="btn sm" data-act="a_goodsToggle" data-id="${g.id}">${g.status==='on'?'下架':'上架'}</button>`:'<span class="tiny muted">只读</span>'}</td></tr>`).join('');
  return `<div class="page-title">资源包商品管理</div><div class="page-sub">指定产品包 / 通用金额包 · 三重限制（总库存 + 每人限购 + 单笔上限）· 开售窗口</div>${adminNote()}
  <div class="card"><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>商品</th><th class="tr">售价</th><th>库存</th><th>限购</th><th>窗口</th><th>状态</th><th>操作</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>`;
}
ACTIONS['a_goodsToggle'] = d=>{
  const g = DB.goods.find(x=>x.id===d.id);
  g.status = g.status==='on'?'off':'on';
  pushAudit((g.status==='on'?'上架':'下架')+'资源包商品', g.name, '—', false);
  showToast('商品已'+(g.status==='on'?'上架':'下架')); refreshContent();
};

/* ============================================================
   订单管理
   ============================================================ */
function pageOrders(){
  const rows = DB.orders.map(o=>{
    const g = DB.goods.find(x=>x.id===o.goodsId);
    return `<tr><td class="mono small">${o.id}</td><td class="small">${esc(g?g.name:'—')}</td>
    <td class="num b">${fmtMoney(o.amount)}</td><td>${statusBadge(o.status)}</td>
    <td class="small">${o.status==='pending'?`<span class="count" id="od-${o.id}">--:--</span>`:(o.paidAt||'—')}</td>
    <td class="small muted">${o.createdAt}</td><td class="small">${o.status==='closed'?esc(o.closeNote):'—'}</td></tr>`;
  }).join('');
  return `<div class="page-title">抢购订单管理</div><div class="page-sub">下单锁库存 → 待支付 → 支付/超时释放；高并发场景核心链路监控</div>${adminNote()}
  <div class="card"><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>订单号</th><th>商品</th><th class="tr">金额</th><th>状态</th><th>支付/倒计时</th><th>下单时间</th><th>备注</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>`;
}

/* ============================================================
   账单管理（日切 / 出账 / 催缴）
   ============================================================ */
function pageBilling(){
  const tab = ABillTab.cur;
  return `<div class="page-title">账单管理</div><div class="page-sub">日切 → 月末出账 → 状态流转（已出账→已确认→已结清）→ 欠费催缴</div>${adminNote()}
  <div class="tabs">
    <button class="tab ${tab==='tasks'?'active':''}" data-act="a_billTab" data-tab="tasks">出账与日切</button>
    <button class="tab ${tab==='bills'?'active':''}" data-act="a_billTab" data-tab="bills">账单列表</button>
    <button class="tab ${tab==='debt'?'active':''}" data-act="a_billTab" data-tab="debt">欠费与催缴</button>
  </div>
  ${tab==='tasks'?billTasks():tab==='bills'?billList():billDebt()}`;
}
ACTIONS['a_billTab'] = d=>{ ABillTab.cur=d.tab; refreshContent(); };

function billTasks(){
  return `<div class="grid g2">
  <div class="card"><div class="card-h"><h3>每日日切任务</h3></div><div class="card-b">
    <dl class="kv mb16"><dt>最近执行</dt><dd>${badge(DB.monthRunning.lastCut+' 成功','green')}</dd>
    <dt>快照范围</dt><dd>前一日计量/计费快照（定案不回溯）</dd>
    <dt>联动</dt><dd>日切完成后自动触发每日对账</dd></dl>
    <button class="btn ${aCan('billing')?'primary':''}" ${aCan('billing')?'':'disabled'} data-act="a_runCut">执行日切（模拟）</button></div></div>
  <div class="card"><div class="card-h"><h3>月末出账任务</h3></div><div class="card-b">
    <dl class="kv mb16"><dt>下次出账</dt><dd>2026-11-30 23:59（自然月账期）</dd>
    <dt>本月进度</dt><dd>${fmtMoney(DB.monthRunning.spent)} / 事件 ${fmtInt(DB.monthRunning.events)}</dd></dl>
    <button class="btn ${aCan('billing')?'primary':''}" ${aCan('billing')?'':'disabled'} data-act="a_runBill">为本账期立即出账（演示）</button>
    <div class="note-strip mt8">演示将把当前账期生成一张「已出账」状态账单，可在租户端查看并走状态流转。</div></div></div></div>`;
}
ACTIONS['a_runCut'] = ()=>{
  DB.monthRunning.lastCut = '2026-11-03 10:00（模拟）';
  pushAudit('每日日切','2026-11-03 快照（模拟）','—',false);
  showToast('日切完成：快照已锁定并触发每日对账'); refreshContent();
};
ACTIONS['a_runBill'] = ()=>{
  if(DB.billedCurrent){ showToast('本账期账单已存在（B2026-11）','warn'); return; }
  const spent = DB.monthRunning.spent;
  DB.bills.unshift({id:'B2026-11', period:'2026 年 11 月', status:'draft', original:spent, adjust:0, tags:[], adjustRefs:[],
    sections:[{product:'LLM 推理 API', ver:'v2', range:'11-01 ~ 出账日', amount:+(spent*0.86).toFixed(2)},
              {product:'Embedding API', ver:'v1', range:'11-01 ~ 出账日', amount:+(spent*0.14).toFixed(2)}],
    deptSub:[{dept:'研发部', amount:+(spent*0.52).toFixed(2)},{dept:'算法部', amount:+(spent*0.31).toFixed(2)},{dept:'未分配', amount:+(spent*0.17).toFixed(2)}]});
  DB.billedCurrent = true;
  pushNotify('info','11 月账单已出账', `账单 B2026-11 已生成，应付 ${fmtMoney(spent)}。`);
  pushAudit('月末出账（演示）','B2026-11','金额 '+fmtMoney(spent),false);
  showToast('账单 B2026-11 已生成（已出账）'); refreshContent();
};
function billList(){
  const can = aCan('billing');
  const rows = DB.bills.map(b=>{
    const t = DB.tenants[0];
    return `<tr><td class="small muted">${esc(t.name)}</td><td class="b">${b.period}</td><td class="mono small">${b.id}</td>
    <td class="num">${fmtMoney(b.original)}</td><td class="num ${b.adjust<0?'green':''}">${b.adjust?fmtMoney(b.adjust):'—'}</td>
    <td class="num b">${fmtMoney(b.original+b.adjust)}</td>
    <td>${statusBadge(b.status)}${(b.tags||[]).map(x=>' '+badge(x,'purple')).join('')}</td>
    <td class="nowrap">${b.status==='draft'&&can?`<button class="btn sm primary" data-act="a_billConfirm" data-id="${b.id}">确认</button>`:''}
      ${b.status==='confirmed'&&can?`<button class="btn sm primary" data-act="a_billSettle" data-id="${b.id}">结清</button>`:''}
      ${b.status==='settled'?badge('—','gray'):''}</td></tr>`;
  }).join('');
  return `<div class="card"><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>租户</th><th>账期</th><th>账单号</th><th class="tr">原额</th><th class="tr">调整</th><th class="tr">应付</th><th>状态</th><th>流转</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>
  <div class="callout mt16">状态流转演示：已出账 →（确认）→ 已确认 →（结清/模拟收款）→ 已结清；争议/调整叠加标记。</div>`;
}
ACTIONS['a_billConfirm'] = d=>{
  const b = DB.bills.find(x=>x.id===d.id);
  confirmDialog('确认账单','确认后账单进入「已确认」状态，租户可见并可发起争议。','确认',()=>{
    b.status='confirmed'; pushAudit('账单确认', b.id, '—', false); showToast('账单已确认'); refreshContent();
  });
};
ACTIONS['a_billSettle'] = d=>{
  const b = DB.bills.find(x=>x.id===d.id);
  confirmDialog('结清账单','模拟收款到账，账单进入「已结清」。','模拟收款并结清',()=>{
    b.status='settled'; pushAudit('账单结清', b.id, '模拟收款 '+fmtMoney(b.original+b.adjust), false);
    pushNotify('success','账单已结清', `账单 ${b.id} 已结清。`);
    showToast('账单已结清'); refreshContent();
  });
};
function billDebt(){
  const can = aCan('billing');
  const rows = DB.tenants.filter(t=>t.balance<0).map(t=>{
    const stage = t.balance<=t.overdraft?'suspended':'warning';
    return `<tr><td class="b">${esc(t.name)}</td><td class="num red b">${fmtMoney(t.balance)}</td>
    <td class="num">${fmtMoney(t.overdraft)}</td><td>${statusBadge(stage)}</td>
    <td class="small">${stage==='suspended'?'全停 · 催缴中':stage==='warning'?'预警（停消费最高产品）· 宽限期 3 天':'—'}</td>
    <td>${can?`<button class="btn sm primary" data-act="a_urge" data-id="${t.id}">发送催缴</button>`:'<span class="tiny muted">无权限</span>'}</td></tr>`;
  }).join('');
  return `<div class="card"><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>租户</th><th class="tr">余额</th><th class="tr">透支阈值</th><th>阶段</th><th>说明</th><th>催缴</th></tr></thead>
  <tbody>${rows||'<tr><td colspan="6"><div class="empty">当前无欠费租户</div></td></tr>'}</tbody></table></div></div></div>
  <div class="callout mt16">分级停服（R22）：余额 &lt; 0 → 预警（停本月消费最高产品，不进催缴）；余额 ≤ 透支阈值或宽限期 3 天满 → 全停 + 催缴；充值回正立即全部恢复。</div>`;
}
ACTIONS['a_urge'] = d=>{
  const t = DB.tenants.find(x=>x.id===d.id);
  confirmDialog('发送催缴通知',`向 <b>${esc(t.name)}</b> 发送站内催缴通知（含欠费金额与恢复条件），并记录催缴留痕。`, '发送催缴', ()=>{
    pushNotify('warn','催缴通知', `您的租户已欠费 ${fmtMoney(t.balance)}，请尽快充值以恢复服务。`);
    pushAudit('发送催缴通知', t.name, '欠费 '+fmtMoney(t.balance), true);
    showToast('催缴通知已发送（已留痕）'); refreshContent();
  });
};

/* ============================================================
   对账中心
   ============================================================ */
function pageRecon(){
  const tab = AReconTab.cur;
  return `<div class="page-title">对账中心</div><div class="page-sub">每日对账（计量vs计费 · 计费vs账户）→ 差错识别 → 冲正（调整单，两级审批）/ 转人工</div>${adminNote()}
  <div class="tabs">
    <button class="tab ${tab==='daily'?'active':''}" data-act="a_reconTab" data-tab="daily">每日对账</button>
    <button class="tab ${tab==='err'?'active':''}" data-act="a_reconTab" data-tab="err">差错处理 <span class="cnt">${DB.errors.filter(e=>e.status!=='corrected'&&e.status!=='closed').length}</span></button>
    <button class="tab ${tab==='adj'?'active':''}" data-act="a_reconTab" data-tab="adj">调整单审批 <span class="cnt">${DB.adjustments.filter(a=>a.stage!=='done'&&a.stage!=='rejected').length}</span></button>
  </div>
  ${tab==='daily'?reconDaily():tab==='err'?reconErr():reconAdj()}`;
}
ACTIONS['a_reconTab'] = d=>{ AReconTab.cur=d.tab; refreshContent(); };

function reconDaily(){
  const rows = DB.reconDays.map(r=>`
    <tr><td class="b">${r.date}</td><td class="num">${fmtInt(r.events)}</td><td>${typeof r.diffAB==='string'?badge(r.diffAB, r.diffAB==='平'?'green':'red'):r.diffAB}</td>
    <td>${typeof r.diffBC==='string'?badge(r.diffBC, r.diffBC==='平'?'green':'red'):r.diffBC}</td>
    <td>${r.result==='ok'?badge('对账平','green'):badge('发现差错 '+((r.diffAB!=='平')+(r.diffBC!=='平'))+' 笔','red')}</td></tr>`).join('');
  return `<div class="card"><div class="card-h"><h3>近 7 日对账结果</h3>
    <button class="btn sm ${aCan('recon:init')?'primary':''}" ${aCan('recon:init')?'':'disabled'} data-act="a_runRecon" style="margin-left:auto">立即对账（模拟）</button></div>
  <div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>日期</th><th class="tr">事件数</th><th>计量 vs 计费</th><th>计费 vs 账户</th><th>结论</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>`;
}
ACTIONS['a_runRecon'] = ()=>{ pushAudit('每日对账（模拟）','2026-11-03','—',false); showToast('对账完成：未发现新差错'); refreshContent(); };

function reconErr(){
  const canInit = aCan('recon:init');
  const rows = DB.errors.map(e=>`
    <tr><td class="mono small">${e.id}</td><td class="small">${e.date}</td><td>${badge(e.type, e.type==='丢事件'?'purple':e.type==='重复计费'?'red':e.type==='抵扣不符'?'orange':'blue')}</td>
    <td class="small">${esc(e.tenant)}</td><td class="small">${esc(e.desc)}</td>
    <td class="num tr">${e.impact==null?'—':fmtMoney(e.impact)}</td><td>${statusBadge(e.status)}</td>
    <td class="mono small">${e.adj}</td>
    <td class="nowrap">${e.status==='todo'&&canInit?`<button class="btn sm primary" data-act="a_adjNew" data-id="${e.id}">发起冲正</button><button class="btn sm ghost" data-act="a_errManual" data-id="${e.id}">转人工</button>`:''}</td></tr>`).join('');
  return `<div class="card"><div class="card-h"><h3>差错单</h3><span class="right small muted">差错分类：丢事件 / 重复计费 / 抵扣不符 / 账户不平</span></div>
  <div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>编号</th><th>日期</th><th>类型</th><th>租户</th><th>描述</th><th class="tr">金额影响</th><th>状态</th><th>调整单</th><th>操作</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>
  <div class="callout mt16">冲正以<b>调整单</b>落地：红字冲销/蓝字补记、关联原单、不改原始单据；财务初审 + 复审两级审批（R28/R29）。</div>`;
}
ACTIONS['a_errManual'] = d=>{
  const e = DB.errors.find(x=>x.id===d.id);
  confirmDialog('转人工处理','该差错将挂起转人工处理队列，不再自动冲正。','转人工',()=>{
    e.status='manual'; pushAudit('差错转人工', e.id, e.type, true); showToast('已转人工（已留痕）'); refreshContent();
  });
};
ACTIONS['a_adjNew'] = d=>{
  const e = DB.errors.find(x=>x.id===d.id);
  openModal('发起冲正 · '+e.id, `
    <dl class="kv mb16"><dt>差错</dt><dd>${badge(e.type,'orange')} ${esc(e.desc)}</dd><dt>金额影响</dt><dd class="b">${e.impact==null?'—':fmtMoney(e.impact)}</dd></dl>
    <div class="field"><label>调整单类型</label><div class="radio-chips">
      <label><input type="radio" name="adjtype" value="红字冲销" ${e.impact<0?'checked':''}>红字冲销（退回/冲减）</label>
      <label><input type="radio" name="adjtype" value="蓝字补记" ${e.impact>0?'checked':''}>蓝字补记（补收/补记）</label></div></div>
    <div class="field"><label>调整金额（元，正数）</label><input id="adj-amt2" type="number" step="0.01" value="${e.impact?Math.abs(e.impact):''}"></div>
    <div class="field"><label>调整原因</label><input id="adj-reason2" value="${esc(e.desc)}"></div>
    <div class="callout warn">生成调整单后进入两级审批：<b>审核员初审 → 财务复审</b>（初审 ≠ 复审同岗），生效后差错单转「已冲正」。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="a_adjCreate" data-id="${e.id}">生成调整单</button>`);
};
ACTIONS['a_adjCreate'] = d=>{
  const e = DB.errors.find(x=>x.id===d.id);
  const type = document.querySelector('input[name="adjtype"]:checked').value;
  const amt = +val('adj-amt2')||0;
  const adj = {id:'ADJ20261103-'+String(DB.seq++).slice(-2), type, related:'差错 '+e.id, amount:(type==='红字冲销'?-1:1)*amt,
    reason:val('adj-reason2'), initiator:currentUser().name+'（'+DB.platformRoleNames[currentUser().role]+'）', initAt:nowStr(),
    stage:'review1', review1:null, review2:null, effect:'', targetBill:'—'};
  DB.adjustments.unshift(adj);
  e.status='correcting'; e.adj=adj.id;
  pushAudit('发起冲正（调整单）', adj.id, e.id+' · '+type+' '+fmtMoney(amt), true);
  closeModal(); showToast('调整单已生成，待审核员初审'); refreshContent();
};

function reconAdj(){
  const u = currentUser();
  const rows = DB.adjustments.map(a=>{
    const idx = a.stage==='done'?2 : a.stage==='review2'?1 : 0;
    const canR1 = aCan('recon:review1') && a.stage==='review1';
    const canR2 = aCan('recon:review2') && a.stage==='review2' && a.review1 && a.review1.byUser!==u.id;
    return `<tr><td class="mono small">${a.id}</td><td>${badge(a.type, a.type==='红字冲销'?'red':'green')}</td>
    <td class="small">${esc(a.related)}</td><td class="num b ${a.amount<0?'green':''}">${fmtMoney(a.amount)}</td>
    <td class="small">${esc(a.reason)}</td><td class="small">${esc(a.initiator)}</td>
    <td style="min-width:230px">${stepsHTML(['待初审','待复审','已生效'], idx)}</td>
    <td class="nowrap">
      ${canR1?`<button class="btn sm primary" data-act="a_adjReview" data-id="${a.id}" data-r="1" data-v="pass">初审通过</button><button class="btn sm ghost" data-act="a_adjReview" data-id="${a.id}" data-r="1" data-v="reject">驳回</button>`:''}
      ${canR2?`<button class="btn sm primary" data-act="a_adjReview" data-id="${a.id}" data-r="2" data-v="pass">复审通过</button><button class="btn sm ghost" data-act="a_adjReview" data-id="${a.id}" data-r="2" data-v="reject">驳回</button>`:''}
      ${a.stage==='review1'&&!canR1?`<span class="tiny muted">${aCan('recon:review1')?'':'待审核员初审'}</span>`:''}
      ${a.stage==='review2'&&!canR2?`<span class="tiny muted">${a.review1&&a.review1.byUser===u.id?'初审与复审不可同人':'待财务复审'}</span>`:''}
      ${a.stage==='done'?badge('已生效','green'):''}${a.stage==='rejected'?badge('已驳回','red'):''}
    </td></tr>`;
  }).join('');
  return `<div class="card"><div class="card-h"><h3>调整单审批工作台</h3><span class="right small muted">初审=审核员 · 复审=财务 · 初审≠复审同岗</span></div>
  <div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>编号</th><th>类型</th><th>关联</th><th class="tr">金额</th><th>原因</th><th>发起人</th><th>审批进度</th><th>操作</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>
  <div class="callout mt16">演示提示：切换为「周审（审核员）」可初审；切换为「吴财（财务）」可复审。生效后差错单转「已冲正」并留痕。</div>`;
}
ACTIONS['a_adjReview'] = d=>{
  const a = DB.adjustments.find(x=>x.id===d.id);
  const u = currentUser();
  const pass = d.v==='pass';
  if(d.r==='1'){
    a.stage = pass?'review2':'rejected';
    a.review1 = {by:u.name+'（'+DB.platformRoleNames[u.role]+'）', at:nowStr(), result:pass?'通过':'驳回', byUser:u.id};
    pushAudit('调整单初审', a.id, (pass?'通过':'驳回'), true);
    showToast(pass?'初审通过，待财务复审':'调整单已驳回','warn');
  }else{
    a.stage = pass?'done':'rejected';
    a.review2 = {by:u.name+'（'+DB.platformRoleNames[u.role]+'）', at:nowStr(), result:pass?'通过':'驳回', byUser:u.id};
    if(pass){
      a.effect = '已生效，'+(a.targetBill&&a.targetBill!=='—'?a.targetBill+' 应付金额已更新':'相关账务已更新');
      const err = DB.errors.find(e=>e.adj===a.id);
      if(err) err.status='corrected';
    }
    pushAudit('调整单复审', a.id, (pass?'通过（生效）':'驳回'), true);
    showToast(pass?'复审通过，调整单已生效':'调整单已驳回','warn');
  }
  refreshContent();
};

/* ============================================================
   争议工单处理
   ============================================================ */
function pageDisputesAdmin(){
  const rows = DB.disputes.map(x=>{
    const created = new Date(x.createdAt.replace(' ','T'));
    const leftH = Math.round(x.slaHours - (DEMO_NOW-created)/3600000);
    return `<tr><td class="mono small">${x.id}</td><td class="small b">${esc(x.tenant)}</td><td class="small">${x.bill}</td>
    <td class="num b">${fmtMoney(x.amount)}</td>
    <td>${x.status==='solved'?'—':(leftH>=0?badge('剩余 '+leftH+'h','blue'):`<span class="red b">已超时 ${-leftH}h</span> ${badge('升级','red')}`)}</td>
    <td>${statusBadge(x.status)}</td>
    <td class="nowrap">${aCan('disputes')?`<button class="btn sm" data-act="a_dspHandle" data-id="${x.id}">${x.status==='pending'?'受理':'处理'}</button>`:'<span class="tiny muted">客服专属</span>'}</td></tr>`;
  }).join('');
  return `<div class="page-title">争议工单处理</div><div class="page-sub">客服处理 · 可代查租户数据（强制审计留痕）· 补偿走审批制</div>${adminNote()}
  <div class="card"><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>工单号</th><th>租户</th><th>账单</th><th class="tr">金额</th><th>SLA</th><th>状态</th><th>操作</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>`;
}
ACTIONS['a_dspAccept'] = d=>{
  const x = DB.disputes.find(v=>v.id===d.id);
  x.status='processing';
  x.replies.push({at:nowStr(), by:currentUser().name+'（客服）', text:'工单已受理，开始核验相关流水。'});
  pushAudit('争议受理', x.id, x.tenant, false);
  showToast('已受理'); refreshContent();
};
ACTIONS['a_dspHandle'] = d=>{
  const x = DB.disputes.find(v=>v.id===d.id);
  if(x.status==='pending'){ ACTIONS['a_dspAccept']({id:x.id}); return; }
  openModal('处理争议 · '+x.id, `
    <div class="callout mb16"><b>争议内容：</b>${esc(x.reason)}<br><span class="small muted">关联账单 ${x.bill} · 争议金额 ${fmtMoney(x.amount)} · SLA ${x.slaHours}h${x.escalated?' · 已超时升级':''}</span></div>
    <div class="field"><label>回复租户</label><textarea id="dsp-reply" placeholder="填写核验结论或说明">已完成流水核验，结论将同步租户。</textarea></div>
    <div class="grid g2">
      <button class="btn" data-act="a_dspReply" data-id="${x.id}">📝 提交回复</button>
      <button class="btn" data-act="a_dspProxy" data-id="${x.id}">🔍 代查租户数据（留痕）</button>
    </div>
    <div class="hr"></div>
    <h4 class="mb8">发起补偿（审批制）</h4>
    <div class="field"><div class="radio-chips">
      <label><input type="radio" name="cmptype" value="refund" checked>退费 → 调整单（审核员初审 + 财务复审）</label>
      <label><input type="radio" name="cmptype" value="grant">送额度 → 赠送额度单（审核员初审 + 运营复审）</label></div></div>
    <div class="form-grid">
      <div class="field"><label>补偿金额（元）</label><input id="cmp-amt" type="number" step="0.01" value="${x.amount}"></div>
      <div class="field"><label>补偿说明</label><input id="cmp-note" value="争议核验后补偿"></div>
    </div>
    <div class="flex">
      <button class="btn primary" data-act="a_dspCompensate" data-id="${x.id}">发起补偿并结案</button>
      <button class="btn ghost" data-act="a_dspClose" data-id="${x.id}">无需补偿 · 直接结案</button>
    </div>`,
    `<button class="btn" data-act="_closeModal">关闭</button>`, 'lg');
};
ACTIONS['a_dspReply'] = d=>{
  const x = DB.disputes.find(v=>v.id===d.id);
  x.replies.push({at:nowStr(), by:currentUser().name+'（客服）', text:val('dsp-reply')||'（无内容）'});
  pushNotify('info','争议进展', `工单 ${x.id} 有新的处理回复。`);
  showToast('回复已提交并通知租户'); refreshContent();
};
ACTIONS['a_dspProxy'] = d=>{
  const x = DB.disputes.find(v=>v.id===d.id);
  pushAudit('代查租户数据', x.tenant, '争议 '+x.id+' · 2026-10 账单及消费明细', true);
  openModal('代查租户数据 · '+esc(x.tenant), `
    <div class="banner warn"><span class="b-ico">审计</span><div>本次代查已记录审计日志（操作人 / 目标租户 / 查询范围 / 时间）。</div></div>
    <h4 class="mb8">2026-10 账单概览</h4>
    <div class="table-wrap mb16"><table class="table"><thead><tr><th>账单</th><th class="tr">应付</th><th>状态</th></tr></thead>
    <tbody><tr><td class="mono small">B2026-10</td><td class="num tr">${fmtMoney(1243.20)}</td><td>${statusBadge('confirmed')}</td></tr></tbody></table></div>
    <h4 class="mb8">近期事件抽样（争议关联）</h4>
    <div class="table-wrap"><table class="table"><thead><tr><th>事件</th><th>时间</th><th class="tr">费用</th></tr></thead>
    <tbody>${DB.events.slice(0,4).map(e=>`<tr><td class="mono small">${e.id}</td><td class="small">${e.at}</td><td class="num tr">${fmt4(e.cost)}</td></tr>`).join('')}</tbody></table></div>`,
    `<button class="btn" data-act="a_dspHandle" data-id="${x.id}">← 返回处理</button>
     <button class="btn primary" data-act="_closeModal">关闭</button>`, 'lg');
};
ACTIONS['a_dspCompensate'] = d=>{
  const x = DB.disputes.find(v=>v.id===d.id);
  const type = document.querySelector('input[name="cmptype"]:checked').value;
  const amt = +val('cmp-amt')||0;
  if(type==='refund'){
    const adj = {id:'ADJ20261103-'+String(DB.seq++).slice(-2), type:'红字冲销', related:'争议 '+x.id, amount:-amt,
      reason:val('cmp-note')||'争议补偿退费', initiator:currentUser().name+'（客服发起）', initAt:nowStr(),
      stage:'review1', review1:null, review2:null, effect:'', targetBill:x.bill};
    DB.adjustments.unshift(adj);
    x.compensation = `退费调整单 ${adj.id}（${fmtMoney(-amt)}）审批中`;
  }else{
    const adj = {id:'GT20261103-'+String(DB.seq++).slice(-2), type:'赠送额度单', related:'争议 '+x.id, amount:-amt,
      reason:val('cmp-note')||'争议补偿送额度', initiator:currentUser().name+'（客服发起）', initAt:nowStr(),
      stage:'review1', review1:null, review2:null, effect:'', targetBill:'—', grantType:true};
    DB.adjustments.unshift(adj);
    x.compensation = `赠送额度单 ${adj.id}（${fmtMoney(-amt)}）审批中`;
  }
  x.replies.push({at:nowStr(), by:currentUser().name+'（客服）', text:'已发起补偿（审批制），工单结案。'+(val('dsp-reply')?' 回复：'+val('dsp-reply'):'')});
  x.status='solved';
  pushAudit('争议补偿发起', x.id, x.compensation, true);
  pushNotify('success','争议已解决', `工单 ${x.id} 已结案，${x.compensation}`);
  closeModal(); showToast('补偿单已发起（进入两级审批），工单已结案'); refreshContent();
};
ACTIONS['a_dspClose'] = d=>{
  const x = DB.disputes.find(v=>v.id===d.id);
  x.status='solved'; x.compensation='无（口径说明结案）';
  x.replies.push({at:nowStr(), by:currentUser().name+'（客服）', text:'经核验无差错，工单结案。'});
  pushNotify('info','争议已结案', `工单 ${x.id} 已结案。`);
  closeModal(); showToast('工单已结案'); refreshContent();
};

/* ============================================================
   免费额度与赠送管理
   ============================================================ */
function pageGrants(){
  const canGrant = aCan('grants');
  const rows = DB.grants.map(g=>`
    <tr><td class="mono small">${g.id}</td><td class="small b">${esc(g.tenant)}</td><td class="small">${g.product} · ${g.metric}</td>
    <td class="num">${fmtInt(g.amount)}</td><td class="small">${g.expire}</td><td class="small">${esc(g.by)}</td><td class="small muted">${g.at}</td></tr>`).join('');
  return `<div class="page-title">免费额度与赠送管理</div><div class="page-sub">内置额度随计价方案配置；赠送额度按「租户×产品」发放（独立权益、先于内置消耗、全程留痕）</div>${adminNote()}
  <div class="grid g2">
  <div class="card"><div class="card-h"><h3>发放赠送额度</h3></div><div class="card-b">
    <div class="form-grid">
      <div class="field"><label>租户</label><select id="gt-tenant" ${canGrant?'':'disabled'}>${DB.tenants.map(t=>`<option>${t.name}</option>`).join('')}</select></div>
      <div class="field"><label>产品 / 指标</label><select id="gt-metric" ${canGrant?'':'disabled'}>
        <option>LLM 推理 API · 输入 Token</option><option>LLM 推理 API · 输出 Token</option><option>Embedding API · 调用次数</option></select></div>
      <div class="field"><label>数量</label><input id="gt-amt" type="number" value="100000" ${canGrant?'':'disabled'}></div>
      <div class="field"><label>有效期至</label><input id="gt-exp" value="2026-11-30" ${canGrant?'':'disabled'}></div>
    </div>
    <button class="btn ${canGrant?'primary':''}" ${canGrant?'':'disabled'} data-act="a_grantGive">发放（留痕）</button>
    ${canGrant?'':'<span class="tiny muted" style="margin-left:8px">当前岗位无发放权限（运营专属）</span>'}
  </div></div>
  <div class="card"><div class="card-h"><h3>发放记录</h3></div><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>编号</th><th>租户</th><th>额度</th><th class="tr">数量</th><th>有效期</th><th>操作人</th><th>时间</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div></div>
  <div class="callout mt16">消耗顺序（R13）：赠送额度（先到期先扣）→ 内置月度额度 → 资源包 → 余额。补偿类赠送需走审批（争议工单发起）。</div>`;
}
ACTIONS['a_grantGive'] = ()=>{
  const tenant = val('gt-tenant'), metric = val('gt-metric'), amt = +val('gt-amt')||0, exp = val('gt-exp');
  if(amt<=0){ showToast('请输入有效数量','error'); return; }
  const g = {id:'GT20261103-'+String(DB.seq++).slice(-2), tenant, product:metric.split(' · ')[0], metric:metric.split(' · ')[1],
    amount:amt, expire:exp, by:currentUser().name+'（'+DB.platformRoleNames[currentUser().role]+'）', at:nowStr()};
  DB.grants.unshift(g);
  if(tenant==='星澜科技') DB.gifts.push({id:g.id, label:'运营赠送', metric:metric.includes('输入')?'in_tok':metric.includes('输出')?'out_tok':'calls',
    productName:g.product, total:amt, remain:amt, expire:exp, by:g.by, at:g.at});
  pushAudit('赠送额度发放', tenant, metric+' '+fmtInt(amt)+' · 至 '+exp, true);
  pushNotify('success','赠送额度到账', `${g.product} ${g.metric} ${fmtInt(amt)} 已到账，有效期至 ${exp}。`);
  showToast('赠送额度已发放（已留痕）'); refreshContent();
};

/* ============================================================
   系统用户与角色
   ============================================================ */
function pageUsers(){
  const can = aCan('users');
  const rows = DB.platformUsers.map(u=>`
    <tr><td><div class="flex"><div class="avatar">${u.name[0]}</div><div><div class="b">${esc(u.name)}</div><div class="tiny muted">${u.email}</div></div></div></td>
    <td>${badge(DB.platformRoleNames[u.role], u.role==='sadmin'?'red':'indigo')}</td>
    <td>${statusBadge(u.status)}</td>
    <td class="nowrap">${can?`<button class="btn sm" data-act="a_userRoleDlg" data-id="${u.id}">调整岗位</button>
      <button class="btn sm ghost" data-act="a_userToggle" data-id="${u.id}">${u.status==='active'?'禁用':'启用'}</button>`:'<span class="tiny muted">管理员专属</span>'}</td></tr>`).join('');
  return `<div class="page-title">系统用户与角色</div><div class="page-sub">平台侧六岗位：管理员 / 运营 / 客服 / 审核员 / 财务 / 审计员</div>${adminNote()}
  <div class="card"><div class="card-h"><h3>平台账号</h3>${can?`<button class="btn primary sm right" data-act="a_userNewDlg">+ 新增账号</button>`:''}</div>
  <div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>账号</th><th>岗位</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table></div></div></div>
  <div class="card mt16"><div class="card-h"><h3>权限矩阵速览</h3></div><div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>功能域</th><th>管理员</th><th>运营</th><th>客服</th><th>审核员</th><th>财务</th><th>审计员</th></tr></thead>
  <tbody>
    ${[['产品/计价/调价','✓','✓','','','',''],['资源包商品','✓','✓','','','',''],['赠送额度发放','✓','✓（发放）','','','复审',''],
       ['账单/出账/催缴','✓','','','','✓',''],['对账/差错','✓','','','','✓',''],['冲正调整单','','','','初审','复审',''],
       ['争议/代查','✓','','✓','','',''],['停用租户','✓','✓','','','',''],['审计日志','✓','','','','','只读']]
    .map(r=>`<tr>${r.map((c,i)=>`<td class="${i?'tc':''}">${c?badge(c, c==='✓'?'green':'indigo'):''}</td>`).join('')}</tr>`).join('')}
  </tbody></table></div></div></div>`;
}
ACTIONS['a_userNewDlg'] = ()=>{
  openModal('新增平台账号', `<div class="form-grid">
    <div class="field"><label>姓名 *</label><input id="pu-name"></div>
    <div class="field"><label>邮箱 *</label><input id="pu-email" placeholder="name@meterflow.cn"></div>
    <div class="field"><label>岗位</label><select id="pu-role">${Object.entries(DB.platformRoleNames).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></div></div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="a_userCreate">创建</button>`);
};
ACTIONS['a_userCreate'] = ()=>{
  const name = val('pu-name'), email = val('pu-email');
  if(!name||!email){ showToast('请填写姓名与邮箱','error'); return; }
  DB.platformUsers.push({id:'p'+(DB.seq++), name, email, role:val('pu-role'), status:'active'});
  pushAudit('新增平台账号', name, '岗位 '+DB.platformRoleNames[val('pu-role')], true);
  closeModal(); showToast('账号已创建'); refreshContent();
};
ACTIONS['a_userRoleDlg'] = d=>{
  const u = DB.platformUsers.find(x=>x.id===d.id);
  openModal('调整岗位 · '+esc(u.name), `<div class="field"><label>岗位</label><select id="pu-role2">
    ${Object.entries(DB.platformRoleNames).map(([k,v])=>`<option value="${k}" ${u.role===k?'selected':''}>${v}</option>`).join('')}</select></div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="a_userRoleSave" data-id="${u.id}">保存</button>`);
};
ACTIONS['a_userRoleSave'] = d=>{
  const u = DB.platformUsers.find(x=>x.id===d.id);
  u.role = val('pu-role2');
  pushAudit('调整平台岗位', u.name, DB.platformRoleNames[u.role], true);
  closeModal(); showToast('岗位已调整'); refreshContent();
};
ACTIONS['a_userToggle'] = d=>{
  const u = DB.platformUsers.find(x=>x.id===d.id);
  u.status = u.status==='active'?'disabled':'active';
  showToast('账号已'+(u.status==='active'?'启用':'禁用')); refreshContent();
};

/* ============================================================
   操作日志与审计
   ============================================================ */
function pageAudit(){
  const forced = DB.audit.filter(a=>a.forced);
  const rows = DB.audit.map(a=>`
    <tr><td class="small nowrap">${a.at}</td><td class="small b">${esc(a.actor)} <span class="tiny muted">${a.role}</span></td>
    <td>${esc(a.action)}${a.forced?' '+badge('敏感','hot'):''}</td><td class="small mono">${esc(a.target)}</td>
    <td class="small muted">${esc(a.scope)}</td><td>${a.result==='成功'?badge('成功','green'):badge(a.result,'orange')}</td></tr>`).join('');
  return `<div class="page-title">操作日志与审计</div><div class="page-sub">敏感操作全留痕 · 保留 1 年 · 审计员只读</div>${adminNote()}
  <div class="grid g3 mb16">
    <div class="card"><div class="card-b stat"><div class="s-ico" style="background:var(--red-bg)">🛡️</div><div><div class="s-val">${forced.length}</div><div class="s-lbl">强制留痕的敏感操作</div></div></div></div>
    <div class="card"><div class="card-b stat"><div class="s-ico" style="background:var(--blue-bg)">📋</div><div><div class="s-val">${DB.audit.length}</div><div class="s-lbl">日志总条数（演示）</div></div></div></div>
    <div class="card"><div class="card-b stat"><div class="s-ico" style="background:var(--purple-bg)">🔍</div><div><div class="s-val">${DB.audit.filter(a=>a.action.includes('代查')).length}</div><div class="s-lbl">代查操作（强制记录四要素）</div></div></div></div>
  </div>
  <div class="card"><div class="card-h"><h3>审计日志</h3><span class="right small muted">强制覆盖：代查 / 冲正 / 停服恢复 / 调价 / 额度与白名单调整</span></div>
  <div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>范围 / 说明</th><th>结果</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>`;
}

/* ============================================================
   管理端路由表
   ============================================================ */
const ADMIN_PAGES = {
  dashboard: {icon:'📊', label:'数据看板',     hash:'#/a/dashboard', title:'数据看板',     perm:'dashboard', render:pageDash},
  tenants:   {icon:'🏢', label:'租户管理',     hash:'#/a/tenants',   title:'租户管理',     perm:'tenants:view', render:pageTenants},
  products:  {icon:'🏷️', label:'产品与计价',   hash:'#/a/products',  title:'产品与计价',   perm:'products', render:pageProducts},
  goods:     {icon:'📦', label:'资源包商品',   hash:'#/a/goods',     title:'资源包商品',   perm:'goods', render:pageGoods},
  orders:    {icon:'🧾', label:'订单管理',     hash:'#/a/orders',    title:'订单管理',     perm:'orders:view', render:pageOrders, after:afterOrders},
  billing:   {icon:'💰', label:'账单管理',     hash:'#/a/billing',   title:'账单管理',     perm:'billing', render:pageBilling},
  recon:     {icon:'⚖️', label:'对账中心',     hash:'#/a/recon',     title:'对账中心',     perm:'recon:view', render:pageRecon},
  disputes:  {icon:'🎧', label:'争议工单',     hash:'#/a/disputes',  title:'争议工单',     perm:'disputes', render:pageDisputesAdmin},
  grants:    {icon:'🎁', label:'额度与赠送',   hash:'#/a/grants',    title:'额度与赠送',   perm:'grants', render:pageGrants},
  users:     {icon:'👤', label:'系统用户',     hash:'#/a/users',     title:'系统用户与角色',perm:'users', render:pageUsers},
  audit:     {icon:'🛡️', label:'审计日志',     hash:'#/a/audit',     title:'操作日志与审计',perm:'audit', render:pageAudit}
};
function afterOrders(){
  DB.orders.filter(o=>o.status==='pending').forEach(o=>{
    registerCountdown('od-'+o.id, o.expireAt, ()=>{
      if(o.status==='pending'){ closeOrder(o,'超时未支付，库存自动释放'); showToast('订单 '+o.id+' 已超时释放','warn'); refreshContent(); }
    });
  });
}
