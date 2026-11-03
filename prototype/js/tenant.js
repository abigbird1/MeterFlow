/* ============================================================
   MeterFlow 原型 · 租户控制台（11 个功能模块）
   ============================================================ */
'use strict';

/* API Key 数据（租户级多 Key，可绑部门 / 限产品集 / 轮换并行 24h） */
if(!DB.keys){
  DB.keys = [
    {id:'k1', name:'recommend-prod', note:'推荐系统生产网关', dept:'d1', products:['LLM 推理 API'], status:'enabled', createdAt:'2026-08-12', lastUsed:'2026-11-03 08:47', mask:'mfk-9a3f-****-****-d21c'},
    {id:'k2', name:'batch-eval', note:'批量评测任务', dept:'d3', products:['LLM 推理 API','Embedding API'], status:'enabled', createdAt:'2026-09-02', lastUsed:'2026-11-03 08:47', mask:'mfk-4c7b-****-****-09ee'},
    {id:'k3', name:'old-gateway', note:'旧网关 · 轮换中（旧 Key 24h 后失效）', dept:null, products:['全部产品'], status:'rotating', createdAt:'2026-06-20', lastUsed:'2026-11-02 22:01', mask:'mfk-1d55-****-****-7a30'}
  ];
}
DB.goods.forEach(g=>{ if(g.type==='spec' && g.metricKey===undefined) g.metricKey = g.name.includes('Embedding') ? 'calls' : 'out_tok'; });

const TPkgTab = {cur:'hall'};
const TBillTab = {cur:'bills'};
const TOrgTab = {cur:'dept'};
const TFilters = {product:'', dept:''};

/* ---------- 公共：顶部横幅（欠费分级停服 / 隔离提示 / 开通引导） ---------- */
function tenantBanners(){
  let h = '';
  const stage = tenantStage();
  const t = DB.tenant;
  if(stage==='suspended'){
    h += `<div class="banner danger"><span class="b-ico">⛔</span><div><b>已全停服 · 催缴中：</b>余额 ${fmtMoney(t.balance)} 低于透支阈值（${fmtMoney(t.overdraft)}），全部产品调用已被拒收（错误码 METER_SUSPENDED），平台已发起催缴。充值回正（≥ ¥0.00）后立即全部恢复。</div></div>`;
  }else if(stage==='warning'){
    h += `<div class="banner warn"><span class="b-ico">⚠️</span><div><b>分级停服预警：</b>余额 ${fmtMoney(t.balance)}（允许透支至 ${fmtMoney(t.overdraft)}）。已按规则停用本月消费最高产品「<b>${t.topProduct}</b>」，其余产品可用；宽限期 ${t.graceDays} 天内充值回正可全部恢复，逾期未充值将全停并进入催缴。<button class="btn-link" data-act="t_nav" data-to="#/t/wallet">立即充值 →</button></div></div>`;
  }
  const u = currentUser();
  if(u.role==='member') h += `<div class="banner info"><span class="b-ico">🔒</span><div><b>部门级数据隔离：</b>当前仅显示「${deptName(u.dept)}」的用量与账单数据。</div></div>`;
  if(u.role==='viewer') h += `<div class="banner info"><span class="b-ico">🔒</span><div><b>部门级数据隔离 · 只读成员：</b>仅可查看本部门用量汇总，无消费明细权限。</div></div>`;
  if(SESSION.onboard){
    h += `<div class="banner success"><span class="b-ico">🎉</span><div><b>注册即开通成功：</b>租户已自动开通，内置免费额度已发放，可立即创建 API Key 接入。<button class="btn-link" data-act="t_dismissOnboard">知道了</button></div></div>`;
  }
  return h;
}

/* ============================================================
   页面：用量与消费总览
   ============================================================ */
function pageOverview(){
  const u = currentUser();
  const stage = tenantStage();
  const isViewer = u.role==='viewer';
  const isMember = u.role==='member';
  const monthSpend = isMember ? DB.monthRunning.spent*0.42 : DB.monthRunning.spent;
  const evs = isViewer ? [] : visibleEvents().filter(e=>e.at.startsWith('2026-11'));
  const daily = DB.dailyUsage.map(d=> isMember ? {...d, value:+(d.value*0.42).toFixed(2)} : d);

  const quotaRows = [];
  DB.products.forEach(p=>p.metrics.forEach(m=>{
    if(m.free>0){
      const pct = Math.round(m.freeRemain/m.free*100);
      quotaRows.push(`<div class="mb8"><div class="flex small"><span class="b">${p.name} · ${m.name}</span><span class="right muted">剩余 ${fmtInt(m.freeRemain)} / ${fmtInt(m.free)} ${m.freeUnit}</span></div>
        <div class="progress ${pct<20?'hot':''}" style="margin-top:4px"><i style="width:${pct}%"></i></div></div>`);
    }
  }));
  const giftChips = DB.gifts.filter(g=>g.remain>0).map(g=>
    `<div class="chain-seg c-gift" style="margin-bottom:6px"><div class="ord">赠</div><div><div class="b">${esc(g.label)}</div><div class="cs-note">${esc(g.productName)} · 剩余 ${fmtInt(g.remain)} · ${g.expire} 到期</div></div></div>`).join('');

  const myPkgs = DB.packages.filter(k=>k.status==='active');
  const pkgHtml = myPkgs.map(k=>`<div class="flex mb8 small"><span class="tag ${k.type==='spec'?'':'hot'}">${k.type==='spec'?'指定包':'通用包'}</span>
    <span class="b nowrap">${esc(k.title)}</span><div class="progress ${k.remain/k.total<0.15?'hot':''}" style="flex:1"><i style="width:${Math.round(k.remain/k.total*100)}%"></i></div>
    <span class="nowrap muted">${k.type==='spec'? fmtInt(Math.round(k.remain/10000))+' 万'+k.unit : fmtMoney(k.remain)}</span></div>`).join('') || '<div class="empty">暂无生效中的资源包</div>';

  return `
  <div class="flex wrap mb16">
    <div><div class="page-title">用量与消费总览</div><div class="page-sub">实时逐笔计费 · 数据截至 ${DB.monthRunning.lastCut} 日切快照</div></div>
    <div class="right"><button class="btn primary" data-act="t_simDlg">⚡ 演示：模拟一笔用量上报</button></div>
  </div>
  ${tenantBanners()}
  <div class="grid g4 mb16">
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:${stage==='normal'?'var(--green-bg)':'var(--red-bg)'}">💳</div>
      <div><div class="s-val ${DB.tenant.balance<0?'red':''}">${fmtMoney(DB.tenant.balance)}</div><div class="s-lbl">当前余额${DB.tenant.balance<0?' · 透支中':''}</div></div></div></div></div>
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:var(--blue-bg)">📈</div>
      <div><div class="s-val">${fmtMoney(monthSpend)}</div><div class="s-lbl">本月消费${isMember?'（本部门）':''}</div></div></div></div></div>
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:var(--teal-bg)">⚡</div>
      <div><div class="s-val">${fmtInt(DB.monthRunning.events)}</div><div class="s-lbl">本月事件数（去重后）</div></div></div></div></div>
    <div class="card"><div class="card-b"><div class="stat"><div class="s-ico" style="background:var(--purple-bg)">📦</div>
      <div><div class="s-val">${myPkgs.length}</div><div class="s-lbl">生效中资源包</div></div></div></div></div>
  </div>
  <div class="grid g2">
    <div class="card"><div class="card-h"><h3>近 7 日消费趋势</h3></div><div class="card-b">${barsHTML(daily)}${isMember?'<div class="note-strip mt8">已按部门过滤（研发部）</div>':''}</div></div>
    <div class="card"><div class="card-h"><h3>免费额度</h3><span class="right small muted">内置额度自然月重置 · 先赠后内</span></div><div class="card-b">
      ${isViewer?'<div class="empty"><span class="e-ico">🔒</span>只读成员无额度明细权限</div>': quotaRows.join('')}
      ${giftChips?`<div class="hr"></div><div class="small b mb8">赠送额度（优先消耗）</div>${giftChips}`:''}
    </div></div>
  </div>
  <div class="card mt16"><div class="card-h"><h3>生效中的资源包</h3><button class="btn-link right" data-act="t_pkgTab" data-tab="mine">查看全部 →</button></div><div class="card-b">${pkgHtml}</div></div>`;
}

/* ---------- 模拟用量上报（计价 + 抵扣链演示） ---------- */
ACTIONS['t_simDlg'] = ()=>{
  openModal('模拟一笔用量上报', `
    <div class="callout info mb16">演示<b>实时逐笔计费</b>与<b>抵扣优先级链</b>（赠送额度 → 内置月度额度 → 资源包 → 余额）。若产品处于停服预警中，事件将被拒收。</div>
    <div class="field"><label>产品</label><select id="sim-prod">
      ${DB.products.map(p=>`<option value="${p.id}">${p.name}${p.name===DB.tenant.topProduct && tenantStage()==='warning'?'（预警停用中）':''}</option>`).join('')}
    </select></div>
    <div class="form-grid">
      <div class="field"><label>调用次数</label><input id="sim-calls" type="number" value="1200" min="1"></div>
      <div class="field"><label>输入 Token（千）</label><input id="sim-in" type="number" value="4800" min="0"></div>
      <div class="field"><label>输出 Token（千）</label><input id="sim-out" type="number" value="2600" min="0"></div>
      <div class="field"><label>说明</label><input value="字段仅对 LLM 推理 API 生效" disabled></div>
    </div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_simRun">上报并计费 →</button>`, 'lg');
};
ACTIONS['t_simRun'] = ()=>{
  const r = simulateEvent(val('sim-prod'), +val('sim-calls')||0, +val('sim-in')||0, +val('sim-out')||0);
  if(r.error){
    openModal('⛔ 事件被拒收', `
      <div class="banner danger"><span class="b-ico">⛔</span><div><b>错误码 ${r.error}</b><br>${esc(r.msg)}</div></div>
      <div class="callout">对应需求规则：<b>R8</b> 已停服产品的用量事件拒收并返回明确停服错误码，业务系统以此感知停服。</div>`,
      `<button class="btn primary" data-act="_closeModal">知道了</button>`);
    return;
  }
  const body = `
    <div class="callout success mb16">✅ 事件已受理（幂等返回 200）· 适用价格版本 <b>${r.ver}</b> · 计费总金额 <b>${fmt4(r.total)}</b> 元</div>
    <h4 class="mb8">逐笔计价明细（精度：逐笔 4 位小数）</h4>
    <div class="table-wrap"><table class="table"><thead><tr><th>计量指标</th><th>计费用量</th><th>适用版本</th><th>阶梯单价</th><th class="tr">费用</th></tr></thead><tbody>
      ${r.rows.map(x=>`<tr><td>${x.name}</td><td class="num">${x.freeCover?('（全部被额度覆盖 '+fmtInt(x.freeCover)+'）'):fmtInt(x.qty)+' '+x.unit}</td><td>${x.ver}</td><td class="num">${fmt4(x.price)} /${x.unit}</td><td class="num tr b">${fmt4(x.cost)}</td></tr>`).join('')}
    </tbody></table></div>
    <h4 class="mt16 mb8">抵扣优先级链（余量不足自动衔接下一抵扣源）</h4>
    ${chainHTML(r.chain)}
    <div class="hr"></div>
    <div class="kv"><dt>余额变化</dt><dd>${fmtMoney(DB.tenant.balance + (r.chain.filter(c=>c.cls==='c-balance').reduce((s,c)=>s+c.amount,0)))} → <b class="${r.balance<0?'red':''}">${fmtMoney(r.balance)}</b></dd>
    <dt>停服判定</dt><dd>${r.stageAfter==='normal'?'正常':r.stageAfter==='warning'?'预警：停用消费最高产品':'全停 + 催缴'}</dd></div>`;
  openModal('计费结果 · 抵扣链分解', body, `<button class="btn" data-act="t_simDlg">再模拟一笔</button><button class="btn primary" data-act="_closeModal">完成</button>`, 'lg');
  pushNotify('info','用量已计费', `模拟事件已按版本 ${r.ver} 计价并完成抵扣，总费用 ${fmt4(r.total)} 元。`);
};

/* ============================================================
   页面：余额与充值
   ============================================================ */
function pageWallet(){
  const t = DB.tenant, stage = tenantStage();
  const stageInfo = stage==='normal' ? badge('正常','green') : stage==='warning' ? badge('预警 · 分级停服','orange') : badge('已全停 · 催缴中','red');
  return `
  <div class="page-title">余额与充值</div><div class="page-sub">租户级共享余额账户 · 允许有限透支 · 模拟支付即时到账</div>
  ${tenantBanners()}
  <div class="grid g2">
    <div class="card"><div class="card-b">
      <div class="flex mb16"><div class="stat"><div class="s-ico" style="background:${t.balance<0?'var(--red-bg)':'var(--green-bg)'}">💳</div>
        <div><div class="s-val ${t.balance<0?'red':''}" style="font-size:28px">${fmtMoney(t.balance)}</div><div class="s-lbl">当前余额 ${stageInfo}</div></div></div>
        ${tCan('wallet')?`<button class="btn primary lg right" data-act="t_rechargeDlg">充值</button>`:''}
      </div>
      <dl class="kv">
        <dt>透支规则</dt><dd>允许有限透支，平台统一阈值 <b class="red">${fmtMoney(t.overdraft)}</b>，达到阈值触发全停</dd>
        <dt>白名单</dt><dd>${t.whitelist?'本租户已配置单独透支阈值':'未加入白名单（适用统一阈值）'}</dd>
        <dt>恢复条件</dt><dd>充值使余额回正（≥ ¥0.00）后，全部产品立即恢复</dd>
        <dt>充值方式</dt><dd>模拟支付（支付宝 / 微信 / 对公转账），即时到账，不支持提现与退款</dd>
      </dl>
    </div></div>
    <div class="card"><div class="card-h"><h3>充值记录</h3></div><div class="card-b flush"><div class="table-wrap">
      <table class="table"><thead><tr><th>流水号</th><th>时间</th><th class="tr">金额</th><th>方式</th><th>状态</th></tr></thead><tbody>
      ${DB.recharges.map(r=>`<tr><td class="mono small">${r.id}</td><td class="small">${r.at}</td><td class="num tr b">${fmtMoney(r.amount)}</td><td class="small">${r.method}</td><td>${badge(r.status,'green')}</td></tr>`).join('')}
      </tbody></table></div></div></div>
  </div>`;
}
ACTIONS['t_rechargeDlg'] = ()=>{
  openModal('余额充值（模拟支付）', `
    <div class="field"><label>充值金额</label>
      <div class="radio-chips" id="rc-amts">
        ${[100,200,500,1000].map(a=>`<label><input type="radio" name="rcamt" value="${a}" ${a===200?'checked':''}>¥${a}</label>`).join('')}
        <label><input type="radio" name="rcamt" value="custom">自定义</label>
      </div>
    </div>
    <div class="field" id="rc-custom-wrap" style="display:none"><label>自定义金额（元）</label><input id="rc-custom" type="number" min="1" placeholder="如 300"></div>
    <div class="field"><label>支付方式</label>
      <div class="radio-chips">
        <label><input type="radio" name="rcpay" value="支付宝（模拟）" checked>支付宝（模拟）</label>
        <label><input type="radio" name="rcpay" value="微信支付（模拟）">微信支付（模拟）</label>
        <label><input type="radio" name="rcpay" value="对公转账（模拟）">对公转账（模拟）</label>
      </div>
    </div>
    <div class="note-strip">当前余额 ${fmtMoney(DB.tenant.balance)}；充值即时到账，余额回正（≥¥0.00）后停服产品全部恢复。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_doRecharge">确认支付</button>`);
  document.querySelectorAll('input[name="rcamt"]').forEach(r=>r.addEventListener('change',()=>{
    document.getElementById('rc-custom-wrap').style.display = r.value==='custom'&&r.checked ? '' : 'none';
  }));
};
ACTIONS['t_doRecharge'] = ()=>{
  let amt = 0;
  const sel = document.querySelector('input[name="rcamt"]:checked');
  amt = sel.value==='custom' ? (+val('rc-custom')||0) : +sel.value;
  if(amt<=0){ showToast('请输入有效金额','error'); return; }
  const method = document.querySelector('input[name="rcpay"]:checked').value;
  const before = DB.tenant.balance;
  DB.tenant.balance = +(DB.tenant.balance + amt).toFixed(2);
  DB.recharges.unshift({id:'RC'+Date.now(), at:nowStr(), amount:amt, method, status:'成功', no:'PAY-'+(DB.seq++)});
  const recovered = before<0 && DB.tenant.balance>=0;
  pushAudit('模拟支付（充值）', '余额充值', fmtMoney(amt)+' · '+method, false);
  pushNotify('success','充值到账', `充值 ${fmtMoney(amt)} 已到账（${method}），当前余额 ${fmtMoney(DB.tenant.balance)}。`);
  closeModal();
  showToast(recovered ? `充值成功，余额已回正，停服产品全部恢复！` : `充值成功，到账 ${fmtMoney(amt)}`);
  refreshContent();
};

/* ============================================================
   页面：资源包中心（抢购）
   ============================================================ */
function pagePackages(){
  const tab = TPkgTab.cur;
  return `
  <div class="page-title">资源包中心</div><div class="page-sub">限量资源包 · 高并发抢购 · 下单锁库存，${'15'} 分钟未支付自动释放</div>
  ${tenantBanners()}
  <div class="tabs">
    <button class="tab ${tab==='hall'?'active':''}" data-act="t_pkgTab" data-tab="hall">抢购大厅</button>
    <button class="tab ${tab==='mine'?'active':''}" data-act="t_pkgTab" data-tab="mine">我的资源包 <span class="cnt">${DB.packages.length}</span></button>
  </div>
  ${tab==='hall' ? pkgHall() : pkgMine()}`;
}
ACTIONS['t_pkgTab'] = d=>{ TPkgTab.cur = d.tab; refreshContent(); };

function pkgHall(){
  const cards = DB.goods.map(g=>{
    const left = g.stock - g.sold;
    const bought = DB.orders.filter(o=>o.goodsId===g.id && (o.status==='paid'||o.status==='pending')).length;
    const before = new Date(g.win.split(' ~ ')[0]) > new Date('2026-11-03');
    const soldOut = left<=0;
    const limited = bought >= g.perLimit;
    let btn, reason=null;
    if(g.status!=='on'){ btn=`<button class="btn" disabled>已下架</button>`; }
    else if(before){ btn=`<button class="btn" disabled>未开售</button>`; reason=`开售时间：${g.win}`; }
    else if(soldOut){ btn=`<button class="btn" disabled>已售罄</button>`; }
    else if(limited){ btn=`<button class="btn" disabled>已达限购</button>`; reason=`每人限购 ${g.perLimit} 份，您已购买 ${bought} 份`; }
    else { btn=`<button class="btn primary" data-act="t_buy" data-id="${g.id}">立即抢购</button>`; }
    return `<div class="card"><div class="card-b">
      <div class="flex mb8"><span class="tag ${g.type==='spec'?'':'hot'}">${g.type==='spec'?'指定产品包':'通用金额包'}</span>${g.status!=='on'?badge('未开售','gray'):''}</div>
      <div class="b" style="font-size:15px">${esc(g.name)}</div>
      <div class="small muted mb8">${esc(g.spec)}</div>
      <div class="flex mb8"><span style="font-size:20px;font-weight:700;color:var(--ac)">${fmtMoney(g.price)}</span><span class="small muted">${g.type==='spec'?'':'/ 面值 ¥100'}</span>
        <span class="right small muted">有效期 ${g.days} 天</span></div>
      <div class="flex small mb8"><span class="muted nowrap">库存</span><div class="progress ${g.sold/g.stock>0.8?'hot':''}" style="flex:1"><i style="width:${Math.round(g.sold/g.stock*100)}%"></i></div>
        <span class="nowrap ${soldOut?'red':'muted'}">${soldOut?'已抢完':`剩 ${fmtInt(left)} / ${fmtInt(g.stock)}`}</span></div>
      <div class="small muted mb16">每人限购 ${g.perLimit} · 单笔限 ${g.orderLimit} 份 · 窗口 ${g.win} · 已购 ${bought} 份</div>
      <div class="flex">${btn}${reason?`<span class="small muted right">${reason}</span>`:''}</div>
    </div></div>`;
  }).join('');
  return `<div class="grid g2">${cards}</div>
  <div class="callout mt16">抢购口径（需求 R31/R32）：<b>三重限制</b> = 总库存 + 每人限购 + 单笔订单上限；<b>下单即锁库存</b>，15 分钟未支付自动释放回库存。</div>`;
}

function pkgMine(){
  const pendings = DB.orders.filter(o=>o.status==='pending');
  const pendHtml = pendings.map(o=>{
    const g = DB.goods.find(x=>x.id===o.goodsId);
    return `<div class="card mb16" style="border-color:#f59e0b"><div class="card-b flex wrap">
      <div><div class="b">${esc(g.name)} × ${o.qty}</div><div class="small muted">订单 ${o.id} · 待支付（库存已锁定）</div></div>
      <div class="right flex"><span class="count" id="cd-${o.id}">--:--</span>
        <button class="btn primary sm" data-act="t_payDlg" data-id="${o.id}">立即支付</button>
        <button class="btn sm" data-act="t_forceTimeout" data-id="${o.id}">模拟超时</button>
        <button class="btn ghost sm" data-act="t_cancelOrder" data-id="${o.id}">取消订单</button></div>
    </div></div>`;
  }).join('');
  const rows = DB.packages.map(k=>`
    <tr><td><span class="tag ${k.type==='spec'?'':'hot'}">${k.type==='spec'?'指定包':'通用包'}</span> <span class="b">${esc(k.title)}</span><div class="tiny muted">${esc(k.sub)}</div></td>
    <td style="min-width:150px"><div class="progress ${k.remain/k.total<0.15?'hot':''}"><i style="width:${Math.round(k.remain/k.total*100)}%"></i></div>
      <div class="tiny muted mt8">${k.type==='spec'? fmtInt(k.remain)+' / '+fmtInt(k.total)+' '+k.unit : fmtMoney(k.remain)+' / '+fmtMoney(k.total)}</div></td>
    <td>${k.expire}${k.status==='active' && new Date(k.expire)<=new Date('2026-11-10')?' '+badge('即将到期','orange'):''}</td>
    <td>${statusBadge(k.status)}</td>
    <td class="small muted">${esc(k.source)}</td>
    <td><button class="btn sm" data-act="t_pkgDetail" data-id="${k.id}">抵扣留痕</button></td></tr>`).join('');
  return `${pendHtml}
  <div class="card"><div class="card-h"><h3>资源包实例</h3><span class="right small muted">不退订 · 到期清零 · 全程留痕</span></div><div class="card-b flush"><div class="table-wrap">
    <table class="table"><thead><tr><th>资源包</th><th>余量</th><th>有效期</th><th>状态</th><th>来源</th><th></th></tr></thead><tbody>${rows}</tbody></table>
  </div></div></div>
  <script>/* countdown registration handled in after hook */</script>`;
}

ACTIONS['t_buy'] = d=>{
  const g = DB.goods.find(x=>x.id===d.id);
  openModal('确认订单', `
    <dl class="kv mb16">
      <dt>商品</dt><dd><b>${esc(g.name)}</b><div class="small muted">${esc(g.spec)}</div></dd>
      <dt>类型</dt><dd>${g.type==='spec'?'指定产品包（限绑定产品+指标）':'通用金额包（按实时单价折算）'}</dd>
      <dt>数量</dt><dd>1 份（单笔上限 ${g.orderLimit} 份）</dd>
      <dt>有效期</dt><dd>自支付成功起 ${g.days} 天</dd>
      <dt>应付</dt><dd style="font-size:18px" class="b">${fmtMoney(g.price)}</dd>
    </dl>
    <div class="note-strip">提交订单即锁定库存 1 份，15 分钟内未支付将自动释放。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_doBuy" data-id="${g.id}">提交订单</button>`);
};
ACTIONS['t_doBuy'] = d=>{
  const g = DB.goods.find(x=>x.id===d.id);
  g.sold += 1;
  const o = {id:'PO20261103-'+String(DB.seq++).slice(-3), goodsId:g.id, qty:1, amount:g.price, status:'pending',
    createdAt:nowStr(), expireAt:Date.now()+15*60*1000, closeNote:''};
  DB.orders.unshift(o);
  closeModal();
  TPkgTab.cur='mine';
  showToast('下单成功，库存已锁定，请在 15 分钟内完成支付');
  pushNotify('warn','订单待支付', `订单 ${o.id}（${g.name}）已锁定库存，请尽快支付，超时自动释放。`);
  nav('#/t/packages'); refreshContent();
};
function closeOrder(o, note){
  o.status='closed'; o.closeNote=note;
  const g = DB.goods.find(x=>x.id===o.goodsId); if(g) g.sold = Math.max(0,g.sold-1);
  pushNotify('info','订单已关闭', `订单 ${o.id} ${note}。`);
}
ACTIONS['t_cancelOrder'] = d=>{
  const o = DB.orders.find(x=>x.id===d.id);
  confirmDialog('取消订单','取消后锁定的库存将立即释放回库存池。确定取消？','取消订单',()=>{ closeOrder(o,'用户主动取消，库存已释放'); showToast('订单已取消，库存已释放'); refreshContent(); });
};
ACTIONS['t_forceTimeout'] = d=>{
  const o = DB.orders.find(x=>x.id===d.id);
  confirmDialog('模拟支付超时','演示「下单锁库存 + 超时释放」：立即触发 15 分钟超时，库存自动释放。','触发超时释放',()=>{ closeOrder(o,'超时未支付，库存自动释放'); showToast('订单超时关闭，库存已自动释放','warn'); refreshContent(); });
};
ACTIONS['t_payDlg'] = d=>{
  const o = DB.orders.find(x=>x.id===d.id);
  const g = DB.goods.find(x=>x.id===o.goodsId);
  openModal('模拟支付', `
    <dl class="kv mb16"><dt>订单</dt><dd>${o.id}</dd><dt>商品</dt><dd>${esc(g.name)}</dd><dt>应付</dt><dd class="b" style="font-size:18px">${fmtMoney(o.amount)}</dd></dl>
    <div class="field"><label>支付方式</label><div class="radio-chips">
      <label><input type="radio" name="pkpay" value="支付宝（模拟）" checked>支付宝（模拟）</label>
      <label><input type="radio" name="pkpay" value="微信支付（模拟）">微信支付（模拟）</label></div></div>
    <div class="note-strip">支付成功后发放资源包实例（有效期 ${g.days} 天），购买记录全程留痕。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_doPay" data-id="${o.id}">确认支付 ${fmtMoney(o.amount)}</button>`);
};
ACTIONS['t_doPay'] = d=>{
  const o = DB.orders.find(x=>x.id===d.id);
  const g = DB.goods.find(x=>x.id===o.goodsId);
  o.status='paid'; o.paidAt=nowStr();
  const isSpec = g.type==='spec';
  const total = isSpec ? (g.name.includes('1 亿')?100000000:5000000) : 100;
  const expDate = new Date(new Date('2026-11-03T00:00:00+08:00').getTime() + g.days*86400000);
  const expStr = expDate.toISOString().slice(0,10);
  DB.packages.unshift({id:'mp'+(DB.seq++), type:g.type, title:g.name+(isSpec?'':'（100 元）'), sub:g.spec, metric:g.metricKey||null,
    remain:total, total, unit:isSpec?(g.metricKey==='calls'?'次':'Token'):'元', expire:expStr, status:'active',
    source:'订单 '+o.id, deds:[]});
  pushAudit('模拟支付（资源包）', o.id, fmtMoney(o.amount), false);
  pushNotify('success','资源包已到账', `订单 ${o.id} 支付成功，「${g.name}」已发放，有效期 ${g.days} 天。`);
  closeModal(); showToast('支付成功，资源包已发放'); refreshContent();
};
ACTIONS['t_pkgDetail'] = d=>{
  const k = DB.packages.find(x=>x.id===d.id);
  openModal('资源包抵扣留痕 · '+esc(k.title), `
    <dl class="kv mb16"><dt>类型</dt><dd>${k.type==='spec'?'指定产品包（限绑定产品+指标）':'通用金额包'}</dd>
    <dt>余量</dt><dd>${k.type==='spec'?fmtInt(k.remain)+' / '+fmtInt(k.total)+' '+k.unit:fmtMoney(k.remain)+' / '+fmtMoney(k.total)}</dd>
    <dt>有效期至</dt><dd>${k.expire}（到期清零，不退订）</dd><dt>来源</dt><dd>${esc(k.source)}</dd></dl>
    <h4 class="mb8">抵扣 / 变动留痕</h4>
    ${k.deds.length? `<ul class="tl">${k.deds.map(x=>`<li><div>${esc(x.note)}</div><div class="tl-t">${x.at}</div></li>`).join('')}</ul>` : '<div class="empty">暂无抵扣记录</div>'}`,
    `<button class="btn primary" data-act="_closeModal">关闭</button>`);
};

/* ============================================================
   页面：账单与消费明细
   ============================================================ */
function pageBills(){
  if(!tCan('bills:view')) return '<div class="empty"><span class="e-ico">🔒</span>当前角色无权访问账单模块</div>';
  const tab = TBillTab.cur;
  return `
  <div class="page-title">账单与消费明细</div><div class="page-sub">自然月账期 · 每日日切快照 · 账单 = 原额 + 调整 = 应付</div>
  ${tenantBanners()}
  <div class="tabs">
    <button class="tab ${tab==='bills'?'active':''}" data-act="t_billTab" data-tab="bills">账单</button>
    ${tCan('bills:detail')?`<button class="tab ${tab==='events'?'active':''}" data-act="t_billTab" data-tab="events">消费明细</button>`:''}
  </div>
  ${tab==='bills' ? billsTab() : eventsTab()}`;
}
ACTIONS['t_billTab'] = d=>{ TBillTab.cur=d.tab; refreshContent(); };

function billsTab(){
  const running = `
  <div class="card mb16"><div class="card-b flex wrap">
    <div class="stat"><div class="s-ico" style="background:var(--teal-bg)">⏱️</div>
      <div><div class="s-val">${fmtMoney(DB.monthRunning.spent)}</div><div class="s-lbl">${DB.monthRunning.period} · 已消费（月末自动出账）</div></div></div>
    <div class="right small muted">本月事件 ${fmtInt(DB.monthRunning.events)} 条 · 最近日切 ${DB.monthRunning.lastCut} · 日切快照定案不回溯</div>
  </div></div>`;
  const rows = DB.bills.map(b=>`
    <tr class="row-click" data-act="t_billDetail" data-id="${b.id}">
      <td class="b">${b.period}</td><td class="mono small">${b.id}</td>
      <td class="num">${fmtMoney(b.original)}</td>
      <td class="num ${b.adjust<0?'green':''}">${b.adjust?fmtMoney(b.adjust):'—'}</td>
      <td class="num b">${fmtMoney(b.original+b.adjust)}</td>
      <td>${statusBadge(b.status)}${(b.tags||[]).map(t=>' '+badge(t,'purple')).join('')}</td>
      <td><button class="btn sm" data-act="t_billDetail" data-id="${b.id}">详情</button>
        ${tCan('disputes') && b.status!=='settled' ? `<button class="btn sm ghost" data-act="t_disputeNew" data-id="${b.id}">申请争议</button>`:''}</td>
    </tr>`).join('');
  return `${running}
  <div class="card"><div class="card-h"><h3>历史账单</h3><span class="right small muted">出账后调整一律通过调整单体现（R21）</span></div><div class="card-b flush"><div class="table-wrap">
    <table class="table"><thead><tr><th>账期</th><th>账单号</th><th class="tr">原额</th><th class="tr">调整</th><th class="tr">应付</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table>
  </div></div></div>`;
}
ACTIONS['t_billDetail'] = d=>{
  const b = DB.bills.find(x=>x.id===d.id);
  const curIdx = b.status==='settled'?2 : b.status==='confirmed'?1 : 0;
  openModal('账单详情 · '+b.period, `
    ${stepsHTML(['已出账','已确认','已结清'], curIdx)}
    <div class="flex wrap mb16 mt8" style="gap:18px">
      <div><div class="tiny muted">原额</div><div class="b" style="font-size:18px">${fmtMoney(b.original)}</div></div>
      <div class="divider-v"></div>
      <div><div class="tiny muted">调整（调整单）</div><div class="b ${b.adjust<0?'green':''}" style="font-size:18px">${b.adjust?fmtMoney(b.adjust):'—'}</div></div>
      <div class="divider-v"></div>
      <div><div class="tiny muted">应付</div><div class="b" style="font-size:18px;color:var(--ac)">${fmtMoney(b.original+b.adjust)}</div></div>
      <div class="right">${statusBadge(b.status)}${(b.tags||[]).map(t=>' '+badge(t,'purple')).join('')}</div>
    </div>
    <h4 class="mb8">按产品 × 价格版本分段（R11 即时生效分段计费）</h4>
    <div class="table-wrap mb16"><table class="table"><thead><tr><th>产品</th><th>版本</th><th>计费区间</th><th class="tr">金额</th></tr></thead>
      <tbody>${b.sections.map(s=>`<tr><td>${s.product}</td><td>${badge(s.ver,'indigo')}</td><td class="small">${s.range}</td><td class="num tr">${fmtMoney(s.amount)}</td></tr>`).join('')}</tbody></table></div>
    <h4 class="mb8">部门小计（部门级数据隔离口径）</h4>
    <div class="table-wrap mb16"><table class="table"><thead><tr><th>部门</th><th class="tr">金额</th></tr></thead>
      <tbody>${b.deptSub.map(s=>`<tr><td>${s.dept}</td><td class="num tr">${fmtMoney(s.amount)}</td></tr>`).join('')}</tbody></table></div>
    ${(b.adjustRefs&&b.adjustRefs.length)?`<h4 class="mb8">关联调整单</h4>${b.adjustRefs.map(a=>`<div class="chain-seg c-gift mb8"><div class="ord">整</div><div><div class="b mono small">${a}</div><div class="cs-note">调整单已生效，原始账单金额未修改（R21）</div></div></div>`).join('')}`:''}
    <div class="note-strip">账单状态机：已出账 → 已确认 → 已结清；争议/调整期间叠加「争议中 / 已调整」标记。</div>`,
    `<button class="btn" ${tCan('bills:export')?'':'disabled'} data-act="t_exportBill" data-id="${b.id}">导出 CSV</button>
     <button class="btn primary" data-act="_closeModal">关闭</button>`, 'lg');
};
ACTIONS['t_exportBill'] = d=>{
  const b = DB.bills.find(x=>x.id===d.id);
  exportCSV(b.id+'_账单.csv', ['产品','版本','计费区间','金额'], b.sections.map(s=>[s.product,s.ver,s.range,s.amount]));
};

function eventsTab(){
  const isAdmin = currentUser().role==='admin';
  const evs = visibleEvents().filter(e=>{
    if(TFilters.product && e.product!==TFilters.product) return false;
    if(TFilters.dept && e.dept!==TFilters.dept) return false;
    return true;
  });
  const rows = evs.map(e=>`
    <tr class="${e.chain?'row-click':''}" ${e.chain?`data-act="t_evDetail" data-id="${e.id}"`:''}>
      <td class="mono small nowrap">${e.id}</td><td class="small nowrap">${e.at}</td>
      <td class="nowrap">${e.product}</td><td><span class="tag">${e.dept}</span></td>
      <td class="small">${esc(e.sum)}</td><td>${badge(e.ver,'indigo')}</td>
      <td class="num tr ${e.status==='dup'?'muted':''}">${e.cost===null?'—':(e.cost?fmt4(e.cost):'—')}</td>
      <td>${e.status==='dup'?badge('重复 · 幂等返回','orange'):e.status==='hidden'?badge('—','gray'):badge('正常','green')}</td>
      <td>${e.chain?'<span class="btn-link">抵扣链 ▾</span>':''}</td>
    </tr>`).join('');
  return `
  <div class="card mb16"><div class="card-b flex wrap">
    <select class="field" style="width:180px;margin:0" id="f-prod"><option value="">全部产品</option>${DB.products.map(p=>`<option ${TFilters.product===p.name?'selected':''}>${p.name}</option>`).join('')}</select>
    ${isAdmin?`<select class="field" style="width:160px;margin:0" id="f-dept"><option value="">全部部门</option>${DB.depts.filter(d=>d.parent).map(d=>`<option ${TFilters.dept===d.name?'selected':''}>${d.name}</option>`).join('')}<option ${TFilters.dept==='未分配'?'selected':''}>未分配</option></select>`:''}
    <button class="btn sm" data-act="t_filter">筛选</button>
    ${tCan('bills:export')?`<button class="btn sm right" data-act="t_exportEvents">导出 CSV</button>`:''}
  </div></div>
  <div class="card"><div class="card-b flush"><div class="table-wrap">
  <table class="table"><thead><tr><th>事件 ID</th><th>时间</th><th>产品</th><th>部门</th><th>指标摘要</th><th>版本</th><th class="tr">费用</th><th>状态</th><th></th></tr></thead>
  <tbody>${rows||'<tr><td colspan="9"><div class="empty">无匹配记录</div></td></tr>'}</tbody></table></div></div></div>
  <div class="callout mt16">双保险判重（R3）：按「上报系统 + 事件 ID」判重，业务指纹兜底；重复事件<b>幂等返回首次结果</b>（R4），不重复计费。带「抵扣链 ▾」的行可查看逐笔抵扣分解。</div>`;
}
ACTIONS['t_filter'] = ()=>{ TFilters.product=val('f-prod'); TFilters.dept=val('f-dept'); refreshContent(); };
ACTIONS['t_exportEvents'] = ()=>{
  const evs = visibleEvents().filter(e=>TFilters.product===''||e.product===TFilters.product);
  exportCSV('消费明细.csv', ['事件ID','时间','产品','部门','指标摘要','版本','费用','状态'],
    evs.map(e=>[e.id,e.at,e.product,e.dept,e.sum,e.ver,e.cost==null?'':e.cost,e.status==='dup'?'重复-幂等返回':'正常']));
};
ACTIONS['t_evDetail'] = d=>{
  const e = DB.events.find(x=>x.id===d.id);
  openModal('事件计费与抵扣分解 · '+e.id, `
    <dl class="kv mb16"><dt>事件 ID</dt><dd class="mono">${e.id}</dd><dt>时间（到达时间计费）</dt><dd>${e.at}</dd>
    <dt>产品 / 版本</dt><dd>${e.product} · ${badge(e.ver,'indigo')}</dd><dt>费用合计</dt><dd class="b">${fmt4(e.cost)} 元</dd></dl>
    <h4 class="mb8">抵扣优先级链</h4>${chainHTML(e.chain)}`,
    `<button class="btn primary" data-act="_closeModal">关闭</button>`, 'lg');
};

/* ---------- 申请争议 ---------- */
ACTIONS['t_disputeNew'] = d=>{
  const billId = d.id;
  openModal('发起账单争议', `
    <div class="field"><label>关联账单</label><input value="${billId}" disabled></div>
    <div class="field"><label>争议金额（元）</label><input id="dp-amt" type="number" value="18.60" min="0.01"></div>
    <div class="field"><label>争议理由</label><textarea id="dp-reason" placeholder="请描述争议内容，将进入平台争议工单处理流程（分级 SLA）">11-02 晚间批量调用明细与业务系统日志不符，申请核验</textarea></div>
    <div class="note-strip">SLA：争议金额 &lt;1000 元响应时限 48h、≥1000 元 24h，超时自动升级。补偿通过审批制调整单/赠送额度单执行，不由客服直接赔付。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_doDispute" data-id="${billId}">提交申诉</button>`);
};
ACTIONS['t_doDispute'] = ()=>{
  const amt = +val('dp-amt')||0, reason = val('dp-reason');
  if(!reason){ showToast('请填写争议理由','error'); return; }
  const dsp = {id:'DSP20261103-'+String(DB.seq++).slice(-2), tenant:'星澜科技', bill:val('dp-bill')||'B2026-10', amount:amt,
    reason, status:'pending', createdAt:nowStr(), slaHours: amt>=1000?24:48, escalated:false, replies:[], compensation:null};
  dsp.bill = 'B2026-10';
  DB.disputes.unshift(dsp);
  pushAudit('发起账单争议', dsp.id, '关联 '+dsp.bill, false);
  closeModal(); showToast('申诉已提交，等待平台受理');
  nav('#/t/disputes'); refreshContent();
};

/* ============================================================
   页面：API Key 管理
   ============================================================ */
function pageKeys(){
  const rows = DB.keys.map(k=>`
    <tr><td><span class="b">${esc(k.name)}</span><div class="tiny muted">${esc(k.note)}</div></td>
    <td class="mono small">${k.mask}</td>
    <td>${k.dept?`<span class="tag">${deptName(k.dept)}</span>`:badge('未分配（仅管理员可见用量）','gray')}</td>
    <td class="small">${k.products.join('、')}</td>
    <td>${statusBadge(k.status)}</td><td class="small muted">${k.lastUsed}</td>
    <td class="nowrap">
      <button class="btn sm" data-act="t_keyToggle" data-id="${k.id}">${k.status==='enabled'?'禁用':'启用'}</button>
      <button class="btn sm" data-act="t_keyRotate" data-id="${k.id}">轮换</button></td></tr>`).join('');
  return `
  <div class="page-title">API Key 管理</div><div class="page-sub">租户级多 Key · 可选绑定部门（事件部门归属依据）· 可限定产品集 · 轮换新旧并行 24h</div>
  ${tenantBanners()}
  <div class="card"><div class="card-h"><h3>Key 列表</h3><button class="btn primary sm right" data-act="t_keyNewDlg">+ 创建 Key</button></div>
  <div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>名称 / 备注</th><th>Key</th><th>绑定部门</th><th>可用产品</th><th>状态</th><th>最近使用</th><th>操作</th></tr></thead>
  <tbody>${rows}</tbody></table></div></div></div>
  <div class="callout mt16">部门归属（R-部门隔离）：事件部门归属 = Key 的绑定部门；未绑定部门的 Key 产生的用量仅租户管理员可见。Key 变更属敏感操作，自动记录审计日志。</div>`;
}
ACTIONS['t_keyNewDlg'] = ()=>{
  openModal('创建 API Key', `
    <div class="form-grid">
      <div class="field"><label>Key 名称 *</label><input id="k-name" placeholder="如 recommend-prod"></div>
      <div class="field"><label>备注用途</label><input id="k-note" placeholder="如 推荐系统生产网关"></div>
      <div class="field"><label>绑定部门（可选）</label><select id="k-dept"><option value="">未分配（仅管理员可见用量）</option>${DB.depts.filter(d=>d.parent).map(d=>`<option value="${d.id}">${d.name}</option>`).join('')}</select></div>
    </div>
    <div class="field"><label>可用产品集</label><div class="check-group">${DB.products.map(p=>`<label class="check"><input type="checkbox" value="${p.name}" checked> ${p.name}</label>`).join('')}</div></div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_keyCreate">创建</button>`);
};
ACTIONS['t_keyCreate'] = ()=>{
  const name = val('k-name');
  if(!name){ showToast('请填写 Key 名称','error'); return; }
  const prods = [...document.querySelectorAll('#modal-body input[type=checkbox]:checked')].map(c=>c.value);
  const mask = 'mfk-'+Math.random().toString(16).slice(2,6)+'-****-****-'+Math.random().toString(16).slice(2,6);
  DB.keys.unshift({id:'k'+(DB.seq++), name, note:val('k-note'), dept:val('k-dept')||null, products:prods.length?prods:['全部产品'], status:'enabled', createdAt:'2026-11-03', lastUsed:'—', mask});
  pushAudit('创建 API Key', name, val('k-note'), true);
  closeModal(); showToast('Key 已创建（敏感操作已留痕）'); refreshContent();
};
ACTIONS['t_keyToggle'] = d=>{
  const k = DB.keys.find(x=>x.id===d.id);
  const to = k.status==='enabled'?'禁用':'启用';
  confirmDialog((to==='禁用'?'禁用':'启用')+' Key', `确定${to} <b>${esc(k.name)}</b>？${to==='禁用'?'禁用后使用该 Key 的调用与上报将被拒绝。':''}`, '确定', ()=>{
    k.status = k.status==='enabled'?'disabled':'enabled';
    pushAudit((to==='禁用'?'禁用':'启用')+' API Key', k.name, '—', true);
    showToast('已'+to+'（已留痕）'); refreshContent();
  }, to==='禁用');
};
ACTIONS['t_keyRotate'] = d=>{
  const k = DB.keys.find(x=>x.id===d.id);
  confirmDialog('轮换 API Key', `将为 <b>${esc(k.name)}</b> 生成新 Key，<b>新旧 Key 并行有效 24 小时</b>后旧 Key 自动失效（当前旧 Key 进入「轮换中」状态）。`, '生成新 Key', ()=>{
    k.status='rotating'; k.note=(k.note||'')+' · 轮换中（旧 Key 24h 后失效）';
    const mask = 'mfk-'+Math.random().toString(16).slice(2,6)+'-****-****-'+Math.random().toString(16).slice(2,6);
    DB.keys.unshift({id:'k'+(DB.seq++), name:k.name+'-new', note:'轮换生成（替换 '+k.name+'）', dept:k.dept, products:[...k.products], status:'enabled', createdAt:'2026-11-03', lastUsed:'—', mask});
    pushAudit('API Key 轮换', k.name, '新旧 Key 并行 24h', true);
    showToast('新 Key 已生成，旧 Key 24h 后失效'); refreshContent();
  });
};

/* ============================================================
   页面：争议申诉
   ============================================================ */
function pageDisputes(){
  const rows = DB.disputes.filter(x=>x.tenant==='星澜科技').map(x=>{
    const created = new Date(x.createdAt.replace(' ','T'));
    const leftH = Math.round(x.slaHours - (DEMO_NOW-created)/3600000);
    return `<tr class="row-click" data-act="t_dspDetail" data-id="${x.id}">
      <td class="mono small">${x.id}</td><td class="small">${x.bill}</td>
      <td class="num b">${fmtMoney(x.amount)}</td>
      <td>${statusBadge(x.status)}</td>
      <td>${x.status==='solved'?'—':(leftH>=0?`剩余 ${leftH}h`:`<span class="red b">已超时 · 已升级</span>`)}</td>
      <td class="small muted">${x.createdAt}</td><td><span class="btn-link">详情 ▾</span></td></tr>`;
  }).join('');
  return `
  <div class="page-title">账单争议申诉</div><div class="page-sub">分级 SLA（&lt;1000 元 48h / ≥1000 元 24h，超时升级）· 补偿走审批制，不由客服直接赔付</div>
  ${tenantBanners()}
  <div class="card"><div class="card-h"><h3>我的争议工单</h3></div><div class="card-b flush"><div class="table-wrap">
  <table class="table"><thead><tr><th>工单号</th><th>关联账单</th><th class="tr">争议金额</th><th>状态</th><th>SLA</th><th>提交时间</th><th></th></tr></thead>
  <tbody>${rows||'<tr><td colspan="7"><div class="empty">暂无争议记录</div></td></tr>'}</tbody></table></div></div></div>`;
}
ACTIONS['t_dspDetail'] = d=>{
  const x = DB.disputes.find(v=>v.id===d.id);
  const curIdx = x.status==='pending'?0 : x.status==='processing'?1 : 3;
  openModal('争议工单 · '+x.id, `
    ${stepsHTML(['已提交','已受理','处理中','已解决'], curIdx)}
    <dl class="kv mt16 mb16"><dt>关联账单</dt><dd>${x.bill}</dd><dt>争议金额</dt><dd class="b">${fmtMoney(x.amount)}</dd>
    <dt>SLA</dt><dd>${x.slaHours} 小时${x.escalated?' · <span class="red">已超时升级</span>':''}</dd>
    <dt>补偿结果</dt><dd>${x.compensation?esc(x.compensation):'—'}</dd></dl>
    <div class="callout mb16"><b>争议理由：</b>${esc(x.reason)}</div>
    <h4 class="mb8">处理记录</h4>
    <ul class="tl">${x.replies.map(r=>`<li><div><b>${esc(r.by)}</b>：${esc(r.text)}</div><div class="tl-t">${r.at}</div></li>`).join('') || '<li class="dim">等待平台受理…</li>'}</ul>`,
    `<button class="btn primary" data-act="_closeModal">关闭</button>`, 'lg');
};

/* ============================================================
   页面：组织与角色（部门树 / 成员 / 角色权限）
   ============================================================ */
function pageOrg(){
  const tab = TOrgTab.cur;
  return `
  <div class="page-title">组织 · 成员 · 角色</div><div class="page-sub">多级部门树 + 自定义角色（权限点勾选）· 部门级数据隔离</div>
  ${tenantBanners()}
  <div class="tabs">
    <button class="tab ${tab==='dept'?'active':''}" data-act="t_orgTab" data-tab="dept">部门管理</button>
    <button class="tab ${tab==='member'?'active':''}" data-act="t_orgTab" data-tab="member">成员管理</button>
    <button class="tab ${tab==='role'?'active':''}" data-act="t_orgTab" data-tab="role">角色权限</button>
  </div>
  ${tab==='dept'?orgDept():tab==='member'?orgMember():orgRole()}`;
}
ACTIONS['t_orgTab'] = d=>{ TOrgTab.cur=d.tab; refreshContent(); };

function deptTree(pid){
  const kids = DB.depts.filter(d=>d.parent===pid);
  if(!kids.length) return '';
  return '<ul>'+kids.map(d=>{
    const cnt = DB.tenantUsers.filter(u=>u.dept===d.id).length;
    const hasKids = DB.depts.some(x=>x.parent===d.id);
    return `<li><div class="tree-node"><span class="tree-toggle">${hasKids?'▾':'·'}</span>📁 <span class="d-name">${esc(d.name)}</span><span class="d-cnt">${cnt} 人</span>
      <div class="d-acts"><button class="btn sm ghost" data-act="t_deptAdd" data-id="${d.id}">+ 子部门</button></div></div>${deptTree(d.id)}</li>`;
  }).join('')+'</ul>';
}
function orgDept(){
  return `<div class="grid g2"><div class="card"><div class="card-h"><h3>部门树</h3><button class="btn sm right" data-act="t_deptAdd" data-id="d0">+ 子部门</button></div>
  <div class="card-b"><div class="tree">${deptTree('d0')}</div>
  <div class="note-strip mt16">部门用于成员分组与<b>数据隔离边界</b>：成员默认仅看本部门用量/账单；事件部门归属 = API Key 绑定部门。</div></div></div>
  <div class="card"><div class="card-h"><h3>隔离口径说明</h3></div><div class="card-b">
    <dl class="kv">
      <dt>管理员</dt><dd>可见全租户数据（含「未分配」Key 的用量）</dd>
      <dt>财务</dt><dd>可见全租户账单与明细（计费岗位需要）</dd>
      <dt>成员</dt><dd>仅本部门用量与账单明细</dd>
      <dt>只读成员</dt><dd>仅本部门用量汇总，无明细</dd>
      <dt>未分配</dt><dd>未绑定部门的 Key 产生的用量，仅管理员可见</dd>
    </dl></div></div></div>`;
}
ACTIONS['t_deptAdd'] = d=>{
  const parent = DB.depts.find(x=>x.id===d.id);
  openModal('新增子部门', `<div class="field"><label>上级部门</label><input value="${esc(parent.name)}" disabled></div>
    <div class="field"><label>部门名称 *</label><input id="d-name" placeholder="如 测试组"></div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_deptCreate" data-id="${parent.id}">创建</button>`);
};
ACTIONS['t_deptCreate'] = d=>{
  const name = val('d-name');
  if(!name){ showToast('请填写部门名称','error'); return; }
  DB.depts.push({id:'d'+(DB.seq++), name, parent:d.id});
  closeModal(); showToast('部门已创建'); refreshContent();
};

function orgMember(){
  const rows = DB.tenantUsers.map(u=>`
    <tr><td><div class="flex"><div class="avatar" style="background:linear-gradient(135deg,var(--ac),#0ea5e9)">${u.name[0]}</div>
      <div><div class="b">${esc(u.name)}</div><div class="tiny muted">${u.email}</div></div></div></td>
    <td>${u.dept?`<span class="tag">${deptName(u.dept)}</span>`:'—'}</td>
    <td>${badge(({admin:'管理员',finance:'财务',member:'成员',viewer:'只读成员'})[u.role]||u.role,'indigo')}</td>
    <td>${statusBadge(u.status)}</td>
    <td><button class="btn sm" data-act="t_memberRoleDlg" data-id="${u.id}">变更角色</button>
      <button class="btn sm ghost" data-act="t_memberToggle" data-id="${u.id}">${u.status==='active'?'禁用':'启用'}</button></td></tr>`).join('');
  return `<div class="card"><div class="card-h"><h3>成员</h3><button class="btn primary sm right" data-act="t_memberInviteDlg">+ 邀请成员</button></div>
  <div class="card-b flush"><div class="table-wrap"><table class="table">
  <thead><tr><th>成员</th><th>部门</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table></div></div></div>`;
}
ACTIONS['t_memberInviteDlg'] = ()=>{
  openModal('邀请成员', `
    <div class="form-grid">
      <div class="field"><label>邮箱 *</label><input id="u-email" placeholder="name@xinglan.com"></div>
      <div class="field"><label>姓名 *</label><input id="u-name" placeholder="姓名"></div>
      <div class="field"><label>部门</label><select id="u-dept">${DB.depts.filter(d=>d.parent).map(d=>`<option value="${d.id}">${d.name}</option>`).join('')}</select></div>
      <div class="field"><label>角色</label><select id="u-role">${[...DB.rolePresets.map(r=>({id:r.id,name:r.name})),...DB.customRoles.map(r=>({id:r.id,name:r.name+'（自定义）'}) )].map(r=>`<option value="${r.id}">${r.name}</option>`).join('')}</select></div>
    </div><div class="note-strip">成员加入后遵循部门级数据隔离与角色权限边界。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_memberInvite">发送邀请（模拟）</button>`);
};
ACTIONS['t_memberInvite'] = ()=>{
  const name = val('u-name'), email = val('u-email');
  if(!name||!email){ showToast('请填写姓名与邮箱','error'); return; }
  DB.tenantUsers.push({id:'u'+(DB.seq++), name, email, dept:val('u-dept'), role:val('u-role'), status:'active'});
  closeModal(); showToast('邀请已发送（模拟）'); refreshContent();
};
ACTIONS['t_memberRoleDlg'] = d=>{
  const u = DB.tenantUsers.find(x=>x.id===d.id);
  openModal('变更角色 · '+esc(u.name), `
    <div class="field"><label>角色</label><select id="u-role2">
      ${[...DB.rolePresets.map(r=>({id:r.id,name:r.name})),...DB.customRoles.map(r=>({id:r.id,name:r.name+'（自定义）'}) )].map(r=>`<option value="${r.id}" ${u.role===r.id?'selected':''}>${r.name}</option>`).join('')}
    </select></div>
    <div class="note-strip">角色决定功能权限；数据可见范围 = 角色 × 部门隔离规则。</div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_memberRole" data-id="${u.id}">保存</button>`);
};
ACTIONS['t_memberRole'] = d=>{
  DB.tenantUsers.find(x=>x.id===d.id).role = val('u-role2');
  closeModal(); showToast('角色已变更'); refreshContent();
};
ACTIONS['t_memberToggle'] = d=>{
  const u = DB.tenantUsers.find(x=>x.id===d.id);
  u.status = u.status==='active'?'disabled':'active';
  showToast('成员已'+(u.status==='active'?'启用':'禁用')); refreshContent();
};

function orgRole(){
  const presetCards = DB.rolePresets.map(r=>`
    <div class="card"><div class="card-b">
      <div class="flex mb8"><span class="b">${r.name}</span>${badge('预设','teal')}<button class="btn sm ghost right" data-act="t_roleCopy" data-name="${r.name}">复制为自定义</button></div>
      <div class="small muted">${r.desc}</div></div></div>`).join('');
  const customCards = DB.customRoles.map(r=>`
    <div class="card"><div class="card-b">
      <div class="flex mb8"><span class="b">${esc(r.name)}</span>${badge('自定义','purple')}</div>
      <div class="small muted">${esc(r.base)} · 权限点 ${r.perms.length} 个</div></div></div>`).join('');
  return `<div class="grid g2">${presetCards}${customCards}
    <div class="card" style="border-style:dashed"><div class="card-b tc" style="padding:34px">
      <button class="btn primary" data-act="t_roleNewDlg">+ 新建自定义角色</button>
      <div class="small muted mt8">按权限点勾选生成，可分配给成员</div></div></div></div>`;
}
const PERM_GROUPS = {
  '总览':['overview:view'],'余额':['wallet:view','wallet:recharge'],'账单':['bills:view','bills:detail','bills:export'],
  '资源包':['packages:view','packages:buy'],'Key':['keys:manage'],'争议':['disputes:view','disputes:create'],
  '组织':['org:manage'],'通知':['notify:view'],'个人':['profile:edit']
};
ACTIONS['t_roleCopy'] = d=>{
  roleModal('复制角色 · '+d.name, d.name+'-副本', []);
};
ACTIONS['t_roleNewDlg'] = ()=>roleModal('新建自定义角色','','');
function roleModal(title, name, perms){
  openModal(title, `
    <div class="field"><label>角色名称 *</label><input id="r-name" value="${esc(name)}" placeholder="如 运维观察员"></div>
    <div class="field"><label>权限点</label><div class="scroll-y">
    ${Object.entries(PERM_GROUPS).map(([g,list])=>`<div class="check-group mb8"><h4>${g}</h4>
      ${list.map(p=>`<label class="check"><input type="checkbox" value="${p}" ${perms.includes(p)?'checked':''}> ${p}</label>`).join('')}</div>`).join('')}
    </div></div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_roleSave">保存角色</button>`, 'lg');
}
ACTIONS['t_roleSave'] = ()=>{
  const name = val('r-name');
  if(!name){ showToast('请填写角色名称','error'); return; }
  const perms = [...document.querySelectorAll('#modal-body input[type=checkbox]:checked')].map(c=>c.value);
  DB.customRoles.push({id:'r'+(DB.seq++), name, base:'自定义', perms});
  closeModal(); showToast('自定义角色已创建'); refreshContent();
};

/* ============================================================
   页面：通知中心 / 个人信息
   ============================================================ */
function pageNotify(){
  const rows = DB.notifications.map(n=>`
    <div class="card-b flex" style="border-bottom:1px solid var(--line);${n.read?'':'background:var(--ac-bg)'}">
      <span style="font-size:17px">${n.type==='warn'?'⚠️':n.type==='success'?'✅':'ℹ️'}</span>
      <div class="flex1"><div class="b small">${esc(n.title)} ${n.read?'':badge('未读','red')}</div><div class="small muted">${esc(n.text)}</div></div>
      <span class="tiny muted nowrap">${n.at}</span></div>`).join('');
  return `<div class="page-title">通知中心</div><div class="page-sub">站内通知：欠费/停服、到期提醒、出账、争议进展（原型不含短信/邮件渠道）</div>
  ${tenantBanners()}
  <div class="card"><div class="card-h"><h3>全部通知</h3><button class="btn sm right" data-act="t_readAll">全部已读</button></div>${rows}</div>`;
}
ACTIONS['t_readAll'] = ()=>{ DB.notifications.forEach(n=>n.read=true); showToast('已全部标记已读'); refreshContent(); };

function pageProfile(){
  const u = currentUser();
  return `<div class="page-title">个人信息</div><div class="page-sub">基础资料与账号安全</div>${tenantBanners()}
  <div class="grid g2"><div class="card"><div class="card-h"><h3>基础资料</h3></div><div class="card-b">
    <div class="field"><label>姓名</label><input id="pf-name" value="${esc(u.name)}"></div>
    <div class="field"><label>邮箱</label><input value="${esc(u.email)}" disabled></div>
    <div class="field"><label>手机（模拟）</label><input value="138****8888" disabled></div>
    <button class="btn primary" data-act="t_profileSave">保存</button></div></div>
  <div class="card"><div class="card-h"><h3>账号安全</h3></div><div class="card-b">
    <dl class="kv mb16"><dt>当前身份</dt><dd>${badge(({admin:'管理员',finance:'财务',member:'成员',viewer:'只读成员'})[u.role]||u.role,'indigo')}</dd>
    <dt>数据边界</dt><dd>${u.role==='admin'?'全租户':u.role==='viewer'?'本部门汇总':'本部门'}</dd></dl>
    <button class="btn" data-act="t_pwdDlg">修改密码</button></div></div></div>`;
}
ACTIONS['t_profileSave'] = ()=>{ currentUser().name = val('pf-name')||currentUser().name; showToast('资料已保存'); refreshContent(); };
ACTIONS['t_pwdDlg'] = ()=>{
  openModal('修改密码', `<div class="field"><label>新密码</label><input id="pw1" type="password"></div>
    <div class="field"><label>确认新密码</label><input id="pw2" type="password"></div>`,
    `<button class="btn" data-act="_closeModal">取消</button><button class="btn primary" data-act="t_pwdSave">确认修改</button>`);
};
ACTIONS['t_pwdSave'] = ()=>{
  if(!val('pw1')||val('pw1')!==val('pw2')){ showToast('两次输入不一致','error'); return; }
  closeModal(); showToast('密码已更新'); pushAudit('修改密码','本人账号','—',true);
};

ACTIONS['t_nav'] = d=>{ nav(d.to); };
ACTIONS['t_dismissOnboard'] = ()=>{ SESSION.onboard=false; refreshContent(); };

/* ============================================================
   租户端路由表
   ============================================================ */
const TENANT_PAGES = {
  overview:     {icon:'📊', label:'用量总览',  hash:'#/t/overview',  title:'用量与消费总览', render:pageOverview},
  wallet:       {icon:'💳', label:'余额与充值',hash:'#/t/wallet',    title:'余额与充值',    perm:'wallet', render:pageWallet},
  packages:     {icon:'📦', label:'资源包中心',hash:'#/t/packages',  title:'资源包中心',    perm:'packages', render:pagePackages, after:afterPackages},
  bills:        {icon:'🧾', label:'账单与明细',hash:'#/t/bills',     title:'账单与明细',    perm:'bills:view', render:pageBills, after:afterBills},
  keys:         {icon:'🔑', label:'API Key',  hash:'#/t/keys',      title:'API Key 管理',  perm:'keys', render:pageKeys},
  disputes:     {icon:'🎧', label:'争议申诉',  hash:'#/t/disputes',  title:'争议申诉',      perm:'disputes', render:pageDisputes},
  org:          {icon:'👥', label:'组织与角色',hash:'#/t/org',       title:'组织与角色',    perm:'org', render:pageOrg},
  notifications:{icon:'🔔', label:'通知中心',  hash:'#/t/notifications', title:'通知中心',  render:pageNotify},
  profile:      {icon:'⚙️', label:'个人信息',  hash:'#/t/profile',   title:'个人信息',      render:pageProfile}
};
function afterPackages(){
  DB.orders.filter(o=>o.status==='pending').forEach(o=>{
    registerCountdown('cd-'+o.id, o.expireAt, ()=>{
      if(o.status==='pending'){ closeOrder(o,'超时未支付，库存自动释放'); showToast('订单 '+o.id+' 超时未支付，库存已自动释放','warn'); refreshContent(); }
    });
  });
}
function afterBills(){}
