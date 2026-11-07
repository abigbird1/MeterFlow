# MeterFlow · 多租户计量计费与权益中台

工程骨架（开发阶段第一阶段）。设计基线：docs/design/01~06（07 交付说明）；规范：meterflow.md（#1/#5/#6/#7）、docs/rules/database-rules.md。

## 目录

```
server/   后端 Maven 多模块（gateway + platform/billing/equity/finance + common）
web/      前端 Vue3 + TS + Vite + Element Plus（单应用双端 /t /a）
deploy/   docker-compose 中间件全家桶
scripts/sql/  四库建表脚本（44 表）
docs/     设计文档与规范
prototype/ 交互原型（只读参考）
```

## 快速启动

```bash
# 1) 中间件（MySQL 首次启动自动执行 scripts/sql 建表；全量约 10GB 内存，R-9）
cd deploy && docker compose up -d

# 2) 后端（本机 JDK 17 可编译运行；设计基线 Java 21 见 03 §1.5）
cd server && mvn -DskipTests install
mvn -pl meterflow-platform spring-boot:run    # 8081
mvn -pl meterflow-billing  spring-boot:run    # 8082
mvn -pl meterflow-equity   spring-boot:run    # 8083
mvn -pl meterflow-finance  spring-boot:run    # 8084
mvn -pl meterflow-gateway  spring-boot:run    # 8080（路由表见其 application.yml）

# 3) 前端（开发模式，可选）
cd web && npm install && npm run dev           # 5173，代理到网关 8080
```

## 一体化访问（推荐）

网关 `classpath:/static` 托管 `web/dist`（构建后拷贝至 gateway resources/static）：
**http://localhost:8080** 单端口直达完整应用（前端 + 全部 API），无跨域。
登录页支持**租户自助注册**（D1：注册即开通 + 管理员成员 + 内置免费额度）。

## 验证骨架

- `curl http://localhost:8081/ping` → `{"code":"00000",...}`
- 网关链路：`curl http://localhost:8080/t/...`（经路由表转发）
- 前端登录页演示账号一键进入（骨架阶段任意口令）

## 阶段状态

| 阶段 | 状态 |
|---|---|
| 设计 01~06 | ✅ 定稿（06 评审通过） |
| 开发一：工程骨架 | ✅ 可编译/可启动/可路由 |
| 开发二：四域后端 + e2e | ✅ e2e **58/58** 全绿（含核心规则负路径：R8 停服拒收/R22 分级停服/R13 赠额先行/R31 限购；覆盖注册 D1/账单详情/SLA 升级/全员出账/限流 429/长周期视图/出账通知；依赖 5 服务运行）；定时任务：日切对账每日 00:05、月末最后一天 23:30 全员出账（R17/R42，账单号含租户维度）、争议 SLA 升级每 5 分钟（R36/R26）、资源包到期提醒每日 00:30、计费完成事件出站 relay 3s |
| 性能基线（5 万事件实测） | 读：分页 119ms / 深翻页 230ms / 月聚合 282~400ms / 看板趋势 145ms（聚合接口已 SQL 化，规范 #5.9）；写：同步逐笔计价 26 TPS（500 TPS 目标依赖 03 §异步化演进）、幂等重放 32ms |
| Nacos 配置中心（新功能） | ✅ 告警阈值动态化：异常检测倍数/绝对下限、资源包余量提醒比例经 Nacos 下发（@RefreshScope 实时生效，控制台改配置行为翻转实测）；e2e T47 |
| Nacos 注册中心 | ✅ 五服务注册（standalone 8848），网关 11 条路由 lb://；控制台 http://localhost:8848/nacos（nacos/nacos） |
| Sentinel 限流兜底 | ✅ 网关路由级流控：ingest 500 TPS、t-equity 抢购 1000 QPS；触发返回 429 + GWY-0429 统一响应体（突发实测 429 生效、低速率无误伤）；Dashboard 可选接入 127.0.0.1:8858 |
| 长周期视图与出账通知 | ✅ 总览近 6 个月消费柱状图（缺月补零）；月末出账自动站内通知租户（账单号/应付金额）；e2e T44-T45 |
| 月度环比与告警阈值自定义 | ✅ 总览本月消费卡显示上月同期对比（↑红/↓绿）；预算告警阈值租户自定义（预警/用尽滑杆，校验 预警<用尽）；e2e T41-T42 |
| 资源包到期提醒（新功能） | ✅ 每日 00:30 扫描：7 天/3 天分级站内提醒（标记位幂等）+ 过期实例转态 expired（R33 生命周期收口）；资源包页「即将到期」tag；e2e T43a-c |
| 出站事件总线（新功能） | ✅ billing 计费完成事件（T_BILLING_COMPLETED）出站投 RocketMQ（relay 3s 周期，MAX_RETRY 毒消息终态）—— 表膨胀隐患解除，ES/催缴联动为后续接入点；e2e T46 |
| 消费异常检测（新功能） | ✅ 每日 00:15 对昨日消费对比前 7 日日均（>5 倍且 >10 元）自动站内提醒；feign 手动触发；e2e T39-T40a |
| 部门消费统计（新功能） | ✅ /t/depts/stats 本月按部门聚合（事件数/消费/占比），组织页展示（成员仅本部门）；e2e T40b |
| Key 维度用量统计（新功能） | ✅ usage_event 记录上报 Key（key_id），/t/keys/stats 按月聚合（事件数/消费），API Key 页内联展示（历史事件不计入）；e2e T38a-b |
| 月度预算告警（新功能） | ✅ 租户设置本期预算（仅管理员），消费达 80%/100% 自动站内告警（每小时扫描+标记位防重发）；总览页预算进度卡片；e2e T37a-c |
| API 文档 | ✅ springdoc 接入：四服务 /v3/api-docs + /swagger-ui.html（108 端点，基线 #7.10） |
| UI 打磨 | ✅ 侧边栏待办徽标（租户：未读通知；平台：待办账单+欠费/待审调整单/待受理争议，60s 轮询+切页刷新，静默失败）；响应式：<1100px 侧边栏自动折叠为图标栏（规范 #6.5） |
| 开发三：前端增强 | ✅ 租户端 9 页（总览图表/账单详情/Key 部门绑定/组织管理/包抵扣留痕等）+ 管理端 11 页（数据看板双图/账单双 tab 催缴/对账三 tab 审批工作台/争议代查补偿/产品版本明细/订单·额度·用户专页/审计四要素） |
