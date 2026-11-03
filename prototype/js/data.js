/* ============================================================
   MeterFlow 原型 · 模拟数据与计费模拟引擎（纯前端演示）
   演示基准时间固定为 2026-11-03 10:00 (UTC+8)
   ============================================================ */
'use strict';

const DEMO_NOW = new Date('2026-11-03T10:00:00+08:00');

function buildDB(){
  const DB = {};

  DB.tenant = {
    name:'星澜科技', plan:'标准版',
    balance:-37.50,                 // 当前余额（元），负数=透支中
    overdraft:-100,                 // 平台统一透支阈值；白名单租户可单独设置
    whitelist:false,
    topProduct:'LLM 推理 API',      // 本月消费最高产品（分级停服预警对象）
    graceDays:3,
    billExport:true
  };
  DB.platformTenantName = '星澜科技';

  /* ---------- 组织 ---------- */
  DB.depts = [
    {id:'d0', name:'星澜科技（总部）', parent:null},
    {id:'d1', name:'研发部', parent:'d0'},
    {id:'d2', name:'算法部', parent:'d0'},
    {id:'d3', name:'平台组', parent:'d1'}
  ];
  DB.tenantUsers = [
    {id:'u1', name:'张伟', email:'zhangwei@xinglan.com', dept:'d0', role:'admin',   status:'active'},
    {id:'u2', name:'李娜', email:'lina@xinglan.com',     dept:'d0', role:'finance',status:'active'},
    {id:'u3', name:'王芳', email:'wangfang@xinglan.com', dept:'d1', role:'member', status:'active'},
    {id:'u4', name:'赵磊', email:'zhaolei@xinglan.com',  dept:'d1', role:'viewer', status:'active'},
    {id:'u5', name:'陈晨', email:'chenchen@xinglan.com', dept:'d2', role:'member', status:'active'}
  ];
  DB.customRoles = [
    {id:'r9', name:'运维观察员', base:'复制自「只读成员」', perms:['overview:view','notify:view','profile:edit']}
  ];
  DB.rolePresets = [
    {id:'admin',   name:'管理员',   desc:'租户内全部权限，可见全租户数据（含未分配 Key 用量）'},
    {id:'finance', name:'财务',     desc:'余额充值、账单明细、资源包购买、争议申诉'},
    {id:'member',  name:'成员',     desc:'本部门用量与账单明细'},
    {id:'viewer',  name:'只读成员', desc:'仅本部门用量汇总，无明细'}
  ];

  /* ---------- 平台侧账号（六岗位） ---------- */
  DB.platformUsers = [
    {id:'p1', name:'系统管理员', email:'root@meterflow.cn',  role:'sadmin',   status:'active'},
    {id:'p2', name:'钱运',       email:'qianyun@meterflow.cn',role:'ops',      status:'active'},
    {id:'p3', name:'孙倩',       email:'sunqian@meterflow.cn',role:'cs',       status:'active'},
    {id:'p4', name:'周审',       email:'zhoushen@meterflow.cn',role:'reviewer',status:'active'},
    {id:'p5', name:'吴财',       email:'wucai@meterflow.cn',  role:'fin',      status:'active'},
    {id:'p6', name:'郑明',       email:'zhengming@meterflow.cn',role:'audit',  status:'active'}
  ];
  DB.platformRoleNames = {sadmin:'管理员', ops:'运营', cs:'客服', reviewer:'审核员', fin:'财务', audit:'审计员'};

  /* ---------- 产品目录与计价方案 ---------- */
  DB.products = [
    {
      id:'pp1', name:'LLM 推理 API', status:'on', desc:'通用大模型推理服务',
      metrics:[
        {key:'calls',  name:'调用次数',   unit:'次',      cursor:360,
         prices:{v1:0.01, v2:0.01}, tier:{mode:'seg', seg:[{from:0,to:100000,price:0.01},{from:100000,to:null,price:0.008}]},
         free:1000, freeRemain:640, freeUnit:'次'},
        {key:'in_tok', name:'输入 Token', unit:'千Token', cursor:412000,
         prices:{v1:0.002, v2:0.0018}, tier:{mode:'seg', seg:[{from:0,to:1000000,price:0.002},{from:1000000,to:5000000,price:0.0018},{from:5000000,to:null,price:0.0016}]},
         free:1000000, freeRemain:412000, freeUnit:'Token'},
        {key:'out_tok',name:'输出 Token', unit:'千Token', cursor:356000,
         prices:{v1:0.006, v2:0.006}, tier:{mode:'seg', seg:[{from:0,to:1000000,price:0.006},{from:1000000,to:null,price:0.0055}]},
         free:100000, freeRemain:0, freeUnit:'Token'}
      ],
      versions:[
        {v:'v1', at:'2026-06-01 00:00', note:'初始定价'},
        {v:'v2', at:'2026-10-14 10:00', note:'输入 Token 0.0020 → 0.0018（即时生效，账期内分段计费）'}
      ]
    },
    {
      id:'pp2', name:'Embedding API', status:'on', desc:'向量化服务',
      metrics:[
        {key:'calls', name:'调用次数', unit:'次', cursor:21400,
         prices:{v1:0.001, v2:0.001}, tier:{mode:'flat'}, free:10000, freeRemain:8000, freeUnit:'次'}
      ],
      versions:[{v:'v1', at:'2026-06-01 00:00', note:'初始定价'}]
    },
    {
      id:'pp3', name:'对象存储', status:'on', desc:'对象存储容量',
      metrics:[
        {key:'storage', name:'存储量', unit:'GB·月', cursor:68,
         prices:{v1:0.12, v2:0.12}, tier:{mode:'flat'}, free:10, freeRemain:0, freeUnit:'GB'}
      ],
      versions:[{v:'v1', at:'2026-06-01 00:00', note:'初始定价'}]
    }
  ];
  DB.currentVersion = function(p){
    let cur = p.versions[0].v;
    p.versions.forEach(v => { if(new Date(v.at.replace(' ','T')) <= DEMO_NOW) cur = v.v; });
    return cur;
  };

  /* 赠送额度（独立权益，先于内置额度消耗） */
  DB.gifts = [
    {id:'GT20261029-01', label:'万圣节活动赠送', metric:'out_tok', productName:'LLM 推理 API',
     total:50000, remain:12000, expire:'2026-11-05', by:'钱运（运营）', at:'2026-10-29 11:30'}
  ];

  /* ---------- 我的资源包实例 ---------- */
  DB.packages = [
    {id:'mp1', type:'spec', title:'LLM 输出 Token 包', sub:'1 亿输出 Token · 限 LLM 推理 API', metric:'out_tok',
     remain:62000000, total:100000000, unit:'Token', expire:'2027-01-10', status:'active',
     source:'订单 PO20261012-001', deds:[{at:'2026-10-30 23:41', note:'月末批量任务抵扣 38.8 万 Token'}]},
    {id:'mp2', type:'common', title:'通用金额包（100 元）', sub:'100 元通用额度 · 可抵扣任意产品', metric:null,
     remain:62.40, total:100, unit:'元', expire:'2026-11-08', status:'active',
     source:'订单 PO20260928-006', deds:[{at:'2026-10-29 14:02', note:'抵扣 Embedding 调用费 ¥0.086'}]},
    {id:'mp3', type:'spec', title:'LLM 输出 Token 包（1 千万）', sub:'到期未用完自动清零（留痕）', metric:'out_tok',
     remain:0, total:10000000, unit:'Token', expire:'2026-10-31', status:'expired',
     source:'订单 PO20260718-002', deds:[]}
  ];

  /* ---------- 资源包商品 ---------- */
  DB.goods = [
    {id:'g1', type:'spec', name:'LLM 输出 Token 包', spec:'1 亿输出 Token · 限 LLM 推理 API',
     price:480, days:90, stock:500, sold:437, perLimit:2, orderLimit:1, win:'2026-11-01 ~ 2026-11-11', status:'on'},
    {id:'g2', type:'common', name:'通用金额包', spec:'100 元通用额度 · 抵扣时按实时单价折算',
     price:88, days:180, stock:1000, sold:612, perLimit:5, orderLimit:2, win:'2026-11-01 ~ 2026-11-30', status:'on'},
    {id:'g3', type:'spec', name:'Embedding 调用包', spec:'500 万次调用 · 限 Embedding API',
     price:3800, days:365, stock:200, sold:200, perLimit:1, orderLimit:1, win:'2026-10-20 ~ 2026-11-05', status:'on'},
    {id:'g4', type:'common', name:'年度通用大礼包', spec:'1000 元通用额度 · 年度大促',
     price:799, days:365, stock:300, sold:0, perLimit:1, orderLimit:1, win:'2026-11-11 ~ 2026-11-17', status:'on'}
  ];

  /* ---------- 抢购订单 ---------- */
  DB.orders = [
    {id:'PO20261103-002', goodsId:'g1', qty:1, amount:480, status:'pending',
     createdAt:'2026-11-03 09:58', expireAt:Date.now() + 13*60*1000 + 20*1000, closeNote:''},
    {id:'PO20261012-001', goodsId:'g1', qty:1, amount:480, status:'paid',
     createdAt:'2026-10-12 20:11', paidAt:'2026-10-12 20:13', closeNote:''},
    {id:'PO20261005-003', goodsId:'g2', qty:1, amount:88, status:'closed',
     createdAt:'2026-10-05 21:00', closeNote:'超时未支付，库存自动释放'}
  ];

  /* ---------- 账单 ---------- */
  DB.bills = [
    {id:'B2026-10', period:'2026 年 10 月', status:'confirmed', original:1286.40, adjust:-43.20,
     tags:['已调整'], adjustRefs:['ADJ20261030-01'],
     sections:[
       {product:'LLM 推理 API', ver:'v1', range:'10-01 ~ 10-13', amount:528.30},
       {product:'LLM 推理 API', ver:'v2', range:'10-14 ~ 10-31', amount:574.60},
       {product:'Embedding API', ver:'v1', range:'10-01 ~ 10-31', amount:96.50},
       {product:'对象存储', ver:'v1', range:'10-01 ~ 10-31', amount:87.00}],
     deptSub:[{dept:'研发部', amount:642.10},{dept:'算法部', amount:486.30},{dept:'未分配', amount:158.00}]},
    {id:'B2026-09', period:'2026 年 9 月', status:'settled', original:1103.75, adjust:0, tags:[], adjustRefs:[],
     sections:[
       {product:'LLM 推理 API', ver:'v1', range:'09-01 ~ 09-30', amount:921.35},
       {product:'Embedding API', ver:'v1', range:'09-01 ~ 09-30', amount:102.40},
       {product:'对象存储', ver:'v1', range:'09-01 ~ 09-30', amount:80.00}],
     deptSub:[{dept:'研发部', amount:561.20},{dept:'算法部', amount:417.55},{dept:'未分配', amount:125.00}]}
  ];
  DB.monthRunning = {period:'2026 年 11 月（进行中）', spent:186.02, events:12847, lastCut:'2026-11-03 00:05'};
  DB.billedCurrent = false;   // 是否已提前为本账期出账（演示）

  /* ---------- 用量事件（消费明细） ---------- */
  const CHAIN_FULL = [
    {label:'① 赠送免费额度', note:'万圣节赠送 · 输出 Token 12,000', amount:0.0720, cls:'c-gift'},
    {label:'② 内置月度额度', note:'调用 640 次 + 输入 Token 412,000', amount:7.1416, cls:'c-builtin'},
    {label:'③ 资源包（指定包优先）', note:'LLM 输出 Token 包 · 抵扣 2,588,000 Token', amount:15.5280, cls:'c-pkg'},
    {label:'④ 余额', note:'剩余费用 · 余额进入透支', amount:13.6160, cls:'c-balance'}
  ];
  DB.events = [
    {id:'EV-8801', at:'2026-10-28 09:12', product:'LLM 推理 API', dept:'研发部', sum:'调用 3 次 · 输入 7.2 千 · 输出 3.4 千', ver:'v2', cost:0.0561, status:'normal'},
    {id:'EV-8802', at:'2026-10-28 14:40', product:'Embedding API', dept:'算法部', sum:'调用 120 次', ver:'v1', cost:0.1200, status:'normal'},
    {id:'EV-8803', at:'2026-10-29 10:05', product:'LLM 推理 API', dept:'研发部', sum:'调用 2 次 · 输入 5.1 千 · 输出 2.2 千', ver:'v2', cost:0.0351, status:'normal'},
    {id:'EV-8804', at:'2026-10-30 16:22', product:'对象存储', dept:'研发部', sum:'存储 +2 GB·日', ver:'v1', cost:0.2400, status:'normal'},
    {id:'EV-8805', at:'2026-10-31 23:41', product:'LLM 推理 API', dept:'未分配', sum:'月末批量：调用 41 次 · 输入 98.6 千 · 输出 45.2 千', ver:'v2', cost:0.5233, status:'normal'},
    {id:'EV-8806', at:'2026-11-01 08:30', product:'LLM 推理 API', dept:'研发部', sum:'调用 2 次 · 输入 4.4 千 · 输出 1.9 千', ver:'v2', cost:0.0419, status:'normal'},
    {id:'EV-8807', at:'2026-11-01 13:07', product:'Embedding API', dept:'算法部', sum:'调用 86 次', ver:'v1', cost:0.0860, status:'normal'},
    {id:'EV-8808', at:'2026-11-02 09:55', product:'LLM 推理 API', dept:'算法部', sum:'调用 1 次 · 输入 3.2 千 · 输出 1.5 千', ver:'v2', cost:0.0287, status:'normal'},
    {id:'EV-8809', at:'2026-11-02 21:14', product:'LLM 推理 API', dept:'研发部', sum:'调用 1 次 · 输入 2.4 千 · 输出 1.1 千', ver:'v2', cost:0.0214, status:'normal'},
    {id:'EV-8810', at:'2026-11-02 21:14', product:'LLM 推理 API', dept:'研发部', sum:'（重复上报，与 EV-8809 同事件 ID）', ver:'v2', cost:0, status:'dup'},
    {id:'EV-8811', at:'2026-11-03 08:47', product:'LLM 推理 API', dept:'研发部', sum:'批量评测：调用 1,200 次 · 输入 4,800 千 · 输出 2,600 千', ver:'v2', cost:36.3576, status:'normal', chain:CHAIN_FULL}
  ];
  DB.dailyUsage = [
    {label:'10-28', value:42.18},{label:'10-29', value:38.90},{label:'10-30', value:51.24},
    {label:'10-31', value:88.02},{label:'11-01', value:44.37},{label:'11-02', value:36.11},{label:'11-03', value:36.42}
  ];

  /* ---------- 争议工单 ---------- */
  DB.disputes = [
    {id:'DSP20261103-01', tenant:'星澜科技', bill:'B2026-10', amount:18.60,
     reason:'11-02 晚间批量调用明细与业务系统日志不符，申请核验',
     status:'processing', createdAt:'2026-11-02 15:40', slaHours:48, escalated:false,
     replies:[{at:'2026-11-02 16:20', by:'孙倩（客服）', text:'已受理，正在核对 11-02 事件流水与去重记录。'}],
     compensation:null},
    {id:'DSP20261101-02', tenant:'云图互动', bill:'B2026-10', amount:860.00,
     reason:'10 月账单中 Embedding 费用明显高于日常水平',
     status:'pending', createdAt:'2026-11-01 08:00', slaHours:24, escalated:true,
     replies:[], compensation:null},
    {id:'DSP20261028-01', tenant:'星澜科技', bill:'B2026-10', amount:43.20,
     reason:'10-21 输出 Token 疑似重复计费',
     status:'solved', createdAt:'2026-10-28 11:20', slaHours:48, escalated:false,
     replies:[{at:'2026-10-28 13:05', by:'孙倩（客服）', text:'已代查事件流水，确认重复计费 43.20 元。'},
              {at:'2026-10-30 16:30', by:'孙倩（客服）', text:'退费调整单 ADJ20261030-01 已生效，款项已退至余额。'}],
     compensation:'退费调整单 ADJ20261030-01（-¥43.20）'},
    {id:'DSP20260915-03', tenant:'星辰数媒', bill:'B2026-09', amount:120.00,
     reason:'9 月账单存储计费口径异议', status:'solved', createdAt:'2026-09-15 10:00', slaHours:48, escalated:false,
     replies:[{at:'2026-09-16 10:00', by:'孙倩（客服）', text:'口径说明已同步，无差错。'}], compensation:'无（口径说明结案）'}
  ];

  /* ---------- 调整单（冲正） ---------- */
  DB.adjustments = [
    {id:'ADJ20261103-01', type:'红字冲销', related:'差错 REC-20261102-07', amount:-28.40,
     reason:'事件 EV-8812 资源包抵扣重复扣减', initiator:'吴财（财务）', initAt:'2026-11-03 09:20',
     stage:'review1', review1:null, review2:null, effect:'', targetBill:'B2026-11（账期进度）'},
    {id:'ADJ20261030-01', type:'红字冲销', related:'争议 DSP20261028-01', amount:-43.20,
     reason:'重复计费退费（争议补偿）', initiator:'吴财（财务）', initAt:'2026-10-30 15:00',
     stage:'done',
     review1:{by:'周审（审核员）', at:'2026-10-30 16:02', result:'通过'},
     review2:{by:'吴财（财务）', at:'2026-10-30 16:30', result:'通过'},
     effect:'已生效，B2026-10 应付金额已更新', targetBill:'B2026-10'},
    {id:'ADJ20261101-02', type:'蓝字补记', related:'差错 REC-20261031-03', amount:15.80,
     reason:'账户差额补记', initiator:'吴财（财务）', initAt:'2026-11-01 09:00',
     stage:'done',
     review1:{by:'周审（审核员）', at:'2026-11-01 09:40', result:'通过'},
     review2:{by:'吴财（财务）', at:'2026-11-01 10:05', result:'通过'},
     effect:'已生效，星辰数媒余额已补记', targetBill:'—'}
  ];

  /* ---------- 对账 ---------- */
  DB.reconDays = [
    {date:'2026-11-03', events:12847, diffAB:'平', diffBC:'平', result:'ok'},
    {date:'2026-11-02', events:13902, diffAB:'差 1 笔', diffBC:'平', result:'err'},
    {date:'2026-11-01', events:12044, diffAB:'平', diffBC:'平', result:'ok'},
    {date:'2026-10-31', events:18021, diffAB:'平', diffBC:'差 1 笔', result:'err'},
    {date:'2026-10-30', events:13558, diffAB:'平', diffBC:'平', result:'ok'},
    {date:'2026-10-29', events:12990, diffAB:'平', diffBC:'平', result:'ok'},
    {date:'2026-10-28', events:12671, diffAB:'平', diffBC:'平', result:'ok'}
  ];
  DB.errors = [
    {id:'REC-20261103-02', date:'2026-11-03', type:'丢事件', tenant:'云图互动',
     desc:'对账窗口内计量侧 3 条事件未生成计费记录（迟到 52 小时，超阈值转人工）', impact:null, status:'manual', adj:'—'},
    {id:'REC-20261102-07', date:'2026-11-02', type:'抵扣不符', tenant:'星澜科技',
     desc:'事件 EV-8812 资源包抵扣重复扣减 28.40 元', impact:-28.40, status:'correcting', adj:'ADJ20261103-01'},
    {id:'REC-20261031-03', date:'2026-10-31', type:'账户不平', tenant:'星辰数媒',
     desc:'余额账户与抵扣流水差额 15.80 元', impact:15.80, status:'corrected', adj:'ADJ20261101-02'},
    {id:'REC-20261028-01', date:'2026-10-28', type:'重复计费', tenant:'星澜科技',
     desc:'10-21 输出 Token 重复计费 43.20 元（争议 DSP20261028-01）', impact:-43.20, status:'corrected', adj:'ADJ20261030-01'}
  ];

  /* ---------- 平台租户列表（管理后台用） ---------- */
  DB.tenants = [
    {id:'t1', name:'星澜科技', members:5, keys:3, balance:-37.50, status:'warning', monthSpend:186.02, overdraft:-100, whitelist:false},
    {id:'t2', name:'星辰数媒', members:12, keys:6, balance:8420.66, status:'normal', monthSpend:1204.88, overdraft:-100, whitelist:false},
    {id:'t3', name:'云图互动', members:8, keys:4, balance:-42.00, status:'suspended', monthSpend:2311.40, overdraft:-100, whitelist:false},
    {id:'t4', name:'极目智驾', members:26, keys:11, balance:15230.00, status:'normal', monthSpend:9860.22, overdraft:-500, whitelist:true}
  ];

  /* ---------- 赠送额度发放记录 ---------- */
  DB.grants = [
    {id:'GT20261029-01', tenant:'星澜科技', product:'LLM 推理 API', metric:'输出 Token', amount:50000, expire:'2026-11-05', by:'钱运（运营）', at:'2026-10-29 11:30'},
    {id:'GT20261015-02', tenant:'极目智驾', product:'LLM 推理 API', metric:'输入 Token', amount:5000000, expire:'2026-12-31', by:'钱运（运营）', at:'2026-10-15 14:00'}
  ];

  /* ---------- 站内通知（租户） ---------- */
  DB.notifications = [
    {id:'n1', type:'warn', title:'余额预警 · 分级停服', text:'余额 -37.50 元，已停用本月消费最高产品「LLM 推理 API」，宽限期 3 天，请及时充值。', at:'2026-11-03 08:48', read:false},
    {id:'n2', type:'warn', title:'资源包到期提醒', text:'「通用金额包（100 元）」将于 2026-11-08 到期，剩余 ¥62.40 到期清零，请尽快使用。', at:'2026-11-01 09:00', read:false},
    {id:'n3', type:'info', title:'争议已受理', text:'工单 DSP20261103-01 已由客服孙倩受理，处理中。', at:'2026-11-02 16:20', read:false},
    {id:'n4', type:'info', title:'10 月账单已出账', text:'账单 B2026-10 已生成，应付 ¥1,243.20（含调整 -¥43.20），请查收。', at:'2026-10-31 23:59', read:true},
    {id:'n5', type:'success', title:'赠送额度到账', text:'万圣节活动赠送：LLM 推理 API 输出 Token 5 万，有效期至 2026-11-05。', at:'2026-10-29 11:30', read:true}
  ];

  /* ---------- 审计日志 ---------- */
  DB.audit = [
    {at:'2026-11-03 08:05', actor:'系统（日切任务）', role:'—', action:'每日日切', target:'2026-11-02 快照', scope:'—', result:'成功', forced:false},
    {at:'2026-11-03 09:20', actor:'吴财', role:'财务', action:'发起冲正（调整单）', target:'ADJ20261103-01', scope:'差错 REC-20261102-07', result:'成功', forced:true},
    {at:'2026-11-02 16:20', actor:'孙倩', role:'客服', action:'争议受理', target:'DSP20261103-01', scope:'星澜科技', result:'成功', forced:false},
    {at:'2026-11-02 09:40', actor:'孙倩', role:'客服', action:'代查租户数据', target:'星澜科技', scope:'2026-10 账单及消费明细', result:'成功', forced:true},
    {at:'2026-11-01 09:12', actor:'吴财', role:'财务', action:'发送催缴通知', target:'云图互动', scope:'欠费 ¥42.00 · 全停', result:'成功', forced:true},
    {at:'2026-10-31 23:59', actor:'系统（出账任务）', role:'—', action:'月末出账', target:'B2026-10 等 486 户', scope:'—', result:'成功', forced:false},
    {at:'2026-10-30 16:30', actor:'吴财', role:'财务', action:'调整单复审', target:'ADJ20261030-01', scope:'星澜科技', result:'通过', forced:true},
    {at:'2026-10-30 16:02', actor:'周审', role:'审核员', action:'调整单初审', target:'ADJ20261030-01', scope:'星澜科技', result:'通过', forced:true},
    {at:'2026-10-29 11:30', actor:'钱运', role:'运营', action:'赠送额度发放', target:'星澜科技', scope:'输出 Token 5 万 · 至 2026-11-05', result:'成功', forced:true},
    {at:'2026-10-14 10:00', actor:'钱运', role:'运营', action:'价格版本发布', target:'LLM 推理 API v2', scope:'输入 Token 0.0020 → 0.0018，即时生效', result:'成功', forced:true},
    {at:'2026-10-12 20:13', actor:'李娜', role:'财务', action:'模拟支付（资源包）', target:'PO20261012-001', scope:'¥480.00', result:'成功', forced:false},
    {at:'2026-10-12 18:40', actor:'张伟', role:'管理员', action:'API Key 轮换', target:'key-recommend-prod', scope:'新旧 Key 并行 24h', result:'成功', forced:true}
  ];

  /* ---------- 充值记录 ---------- */
  DB.recharges = [
    {id:'RC20261020-01', at:'2026-10-20 10:31', amount:500, method:'对公转账（模拟）', status:'成功', no:'PAY-88121'},
    {id:'RC20260930-02', at:'2026-09-30 17:05', amount:1000, method:'支付宝（模拟）', status:'成功', no:'PAY-87233'}
  ];

  DB.seq = 100;
  return DB;
}

let DB = buildDB();

/* ============================================================
   计费模拟引擎（演示 R8-R16：实时逐笔计价 + 抵扣优先级链）
   ============================================================ */

/* Token 类指标：单价按「千Token」计价，用量按「Token」累计，换算除数 */
function unitDiv(m){ return m.key && m.key.indexOf('_tok') >= 0 ? 1000 : 1; }

/* 阶梯计价：账期内累计分段 / 档位单价（cursor 与分段边界均为原生单位） */
function tierCost(metric, qty){
  const t = metric.tier, div = unitDiv(metric);
  if(t.mode === 'flat') return qty * metric.prices.v2 / div;
  let start = metric.cursor, cost = 0, rest = qty;
  for(const seg of t.seg){
    if(rest <= 0) break;
    const segTo = seg.to === null ? Infinity : seg.to;
    if(start >= segTo) continue;
    const segFrom = Math.max(seg.from, start);
    const usable = Math.min(rest, segTo - segFrom);
    if(usable > 0){ cost += usable * seg.price / div; rest -= usable; }
  }
  return cost;
}
function currentSegPrice(metric){
  const t = metric.tier;
  if(t.mode === 'flat') return metric.prices.v2;
  for(const seg of t.seg){
    const segTo = seg.to === null ? Infinity : seg.to;
    if(metric.cursor >= seg.from && metric.cursor < segTo) return seg.price;
  }
  return t.seg[t.seg.length-1].price;
}

/* 租户停服阶段：normal / warning（预警，停消费最高产品） / suspended（全停） */
function tenantStage(){
  if(DB.tenant.balance <= DB.tenant.overdraft) return 'suspended';
  if(DB.tenant.balance < 0) return 'warning';
  return 'normal';
}

/* 模拟一笔用量上报：返回计价明细 + 抵扣链，并更新全局数据 */
function simulateEvent(productId, calls, inTokK, outTokK){
  const p = DB.products.find(x=>x.id===productId);
  if(!p) return {error:'产品不存在'};
  const stage = tenantStage();
  if(stage === 'suspended') return {error:'METER_SUSPENDED', msg:'租户已全停（余额低于透支阈值），全部产品用量事件被拒收。'};
  if(stage === 'warning' && p.name === DB.tenant.topProduct)
    return {error:'METER_SUSPENDED', msg:`产品「${p.name}」处于分级停服预警停用中（本月消费最高产品），事件被拒收。充值回正后恢复。`};

  const ver = DB.currentVersion(p);
  const rows = [];       // 计价明细
  let total = 0;
  const chain = [];

  for(const m of p.metrics){
    let qty = 0, note = '';
    if(m.key==='calls'){ qty = calls; }
    if(m.key==='in_tok'){ qty = Math.round(inTokK*1000); }
    if(m.key==='out_tok'){ qty = Math.round(outTokK*1000); }
    if(qty<=0) continue;
    let billable = qty;
    /* ① 赠送额度（同指标、未过期） */
    const gift = DB.gifts.find(g=>g.metric===m.key && g.remain>0 && new Date(g.expire)>=new Date('2026-11-03'));
    let giftUsed = 0;
    if(gift && m.key!=='calls' && m.key!=='storage'){
      giftUsed = Math.min(gift.remain, billable);
      gift.remain -= giftUsed; billable -= giftUsed;
    }
    /* ② 内置月度额度 */
    let builtinUsed = 0;
    if(m.freeRemain > 0){
      builtinUsed = Math.min(m.freeRemain, billable);
      m.freeRemain -= builtinUsed; billable -= builtinUsed;
    }
    const unitPrice = currentSegPrice(m);
    const div = unitDiv(m);
    const cost = tierCost(m, billable);
    m.cursor += billable;   // 阶梯游标只累计计费用量
    total += cost;
    if(billable>0) rows.push({name:m.name, qty:billable, unit:m.unit, ver, price:unitPrice, cost});
    else rows.push({name:m.name, qty:0, unit:m.unit, ver, price:unitPrice, cost:0, freeCover:qty});
    if(giftUsed>0) chain.push({label:'① 赠送免费额度', note:`${gift.label} · ${m.name} ${fmtInt(giftUsed)} ${m.freeUnit}`, amount:+(giftUsed*unitPrice/div).toFixed(4), cls:'c-gift'});
    if(builtinUsed>0) chain.push({label:'② 内置月度额度', note:`${m.name} ${fmtInt(builtinUsed)} ${m.freeUnit}（本月剩余额度内）`, amount:+(builtinUsed*unitPrice/div).toFixed(4), cls:'c-builtin'});
  }

  /* ③ 资源包（指定包优先 → 通用包） ④ 余额 */
  let rest = +total.toFixed(4);
  const m0 = p.metrics[0];
  const specPkg = DB.packages.find(k=>k.type==='spec' && k.status==='active' && k.metric===m0.key && k.remain>0);
  if(specPkg && rest>0){
    const pricePerUnit = currentSegPrice(m0) / unitDiv(m0);
    const canCover = Math.min(specPkg.remain, Math.floor(rest/pricePerUnit));
    if(canCover>0){
      const amt = +(canCover*pricePerUnit).toFixed(4);
      specPkg.remain -= canCover;
      if(specPkg.remain<=0) specPkg.status='exhausted';
      chain.push({label:'③ 资源包（指定包优先）', note:`${specPkg.title} · 抵扣 ${fmtInt(canCover)} ${specPkg.unit}`, amount:amt, cls:'c-pkg'});
      rest = +(rest-amt).toFixed(4);
    }
  }
  const commonPkg = DB.packages.find(k=>k.type==='common' && k.status==='active' && k.remain>0);
  if(commonPkg && rest>0){
    const use = Math.min(commonPkg.remain, rest);
    commonPkg.remain = +(commonPkg.remain-use).toFixed(4);
    if(commonPkg.remain<=0) commonPkg.status='exhausted';
    chain.push({label:'③ 资源包（通用包兜底）', note:`${commonPkg.title} · 金额抵扣`, amount:+use.toFixed(4), cls:'c-pkg'});
    rest = +(rest-use).toFixed(4);
  }
  if(rest>0){
    DB.tenant.balance = +(DB.tenant.balance - rest).toFixed(2);
    chain.push({label:'④ 余额', note: DB.tenant.balance<0 ? `余额扣至 ${fmtMoney(DB.tenant.balance)}（透支）` : '余额扣减', amount:+rest.toFixed(4), cls:'c-balance'});
  } else {
    chain.push({label:'④ 余额', note:'费用已全部被额度/资源包覆盖，余额未变动', amount:0, cls:'c-none'});
  }

  const ev = {
    id:'EV-'+(DB.seq++), at:'2026-11-03 10:00（模拟）', product:p.name, dept:'研发部',
    sum:`模拟上报：调用 ${fmtInt(calls)} 次 · 输入 ${fmtInt(inTokK)} 千 · 输出 ${fmtInt(outTokK)} 千`,
    ver, cost:+total.toFixed(4), status:'normal', chain
  };
  DB.events.unshift(ev);
  DB.monthRunning.spent = +(DB.monthRunning.spent + total).toFixed(2);
  DB.monthRunning.events += 1;
  const t1 = DB.tenants[0]; t1.balance = DB.tenant.balance; t1.monthSpend = DB.monthRunning.spent;
  if(chain.length) ev.chain = chain;

  return {ok:true, ver, rows, total:+total.toFixed(4), chain, balance:DB.tenant.balance, stageAfter:tenantStage()};
}

function fmtInt(n){ return n.toLocaleString('zh-CN'); }
