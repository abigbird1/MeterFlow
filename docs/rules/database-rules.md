# MeterFlow 数据库设计规范

> 适用范围：MeterFlow 全部数据库表设计与变更。依据：03.技术选型与整体架构 v2.2（每服务一库、ShardingSphere 分表）、requirements.md v1.1（3.9 精度、R1–R44）。本规范由 04 阶段起草，经确认后生效；修订需追加变更记录。

## 1. 全局约定

1. 存储引擎一律 InnoDB；字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。
2. **每服务独立库**：`meterflow_platform` / `meterflow_billing` / `meterflow_equity` / `meterflow_finance`；禁止跨库 JOIN，跨服务数据经业务事件或只读接口获取。
3. 脚本位置：`scripts/sql/schema-<service>.sql`；表结构变更按序号追加变更脚本（`scripts/sql/changes/V<序号>__<说明>.sql`），不修改历史脚本。

## 2. 命名规范

1. 表名、字段名统一小写下划线：`balance_account`、`free_quota`；模块前缀可省略（库即前缀）。
2. 布尔语义字段用 `is_` 前缀或 `status`（状态机）；时间字段 `*_at`（DATETIME(3)）；日期字段 `*_date`（DATE）；账期 `period`（CHAR(7)，`yyyy-MM`）。
3. 金额字段：确定性金额 `*_cents`（BIGINT，单位分）；过程金额 `*_amount`（DECIMAL(20,6)，单位元）；单价 `price`（DECIMAL(16,6)，单位元）。
4. 索引：唯一索引 `uk_`，普通索引 `idx_`；分表表名 `逻辑表_yyyyMM`（如 `usage_event_202611`）。

## 3. 主键与必备字段

1. 主键 `id BIGINT NOT NULL COMMENT '雪花ID'`，由 ShardingSphere 内置雪花算法生成；不使用自增。
2. 必备字段：`created_at DATETIME(3)`、`updated_at DATETIME(3) ON UPDATE`（账务/事件类表 `updated_at` 可省略，只进不改）。
3. 雪花 ID 不暴露业务量级；对外编号一律另设业务号列（如 `bill_no`、`order_no`、`dispute_no`），并建唯一索引。

## 4. 金额与精度（对应需求 3.9）

1. **确定性金额用 BIGINT 分**：余额账户、充值、订单、账单、调整单、日切汇总。
2. **过程金额用 DECIMAL(20,6) 元**：逐笔计价（usage_event.cost）、抵扣流水（deduction_record/item）；逐笔保留 4 位小数。
3. 过程金额入账（写账户/账单）时按 **HALF_UP 舍入到分**；同一事件多指标先合并总费用再舍入（避免逐项累进误差）。
4. 单价：DECIMAL(16,6) 元（最小粒度 0.0001 元）；Token 类指标单价按"元/千 Token"存储，用量按原生单位累计。

## 5. 索引规范

1. 业务唯一约束必须落库唯一索引（如 `uk(ingest_system, event_id)`、`uk(tenant_id, period)`）。
2. 查询路径建 `idx_`；单表索引 ≤ 6 个；禁止在低区分度列（如 status）单独建索引。
3. 分表表的唯一索引**仅分表内生效**：全局唯一由 Redis 判重前置（TTL≥48h）+ 业务兜底保证，设计文档必须说明该边界。

## 6. 分表与归档

1. ShardingSphere-JDBC 逻辑表 + 按月物理分表 `_yyyyMM`，分片键 = 业务时间列（`arrive_time` / `created_at`）。
2. 分表清单：`usage_event`、`deduction_record`、`deduction_item`、`account_transaction`、`audit_log`。
3. 保留期（评审 P-1 对齐 requirements 第 8 章）：用量明细（usage_event）与审计日志在线 1 年，到期整表归档后 DROP（XXL-Job 执行并留痕）；**抵扣流水/余额流水（deduction_record/item、account_transaction）在线 1 年，到期归档转储（不 DROP）保留 ≥3 年**；账单/调整单长期（≥3 年）不删除。

## 7. 删除策略

1. 账务与计费数据**只进不改、禁止物理 DELETE**（架构原则 P2）；修正一律通过调整单前向追加。
2. 组织/凭证类数据（成员、部门、API Key）用状态迁移（禁用/停用），不物理删除。
3. JSON 字段仅存放"整体读写、不做查询条件"的数据（指标集、阶梯档位、抵扣链快照）；需要检索/关联的字段必须独立成列。
4. **禁止创建物理外键（FOREIGN KEY）**：跨库一律业务号逻辑关联（bill_no/order_no/adj_no 等）；库内关联亦以索引 + 服务层状态机保证，完整性由 M7 对账体系兜底（评审 P-3 补充成文）。

## 8. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-11-03 | 04 阶段起草：企业通用规范 + 本项目定案（雪花 ID、金额策略、分表清单、只进不改） |
| v1.1 | 2026-11-03 | 06 阶段评审修订：保留期对齐 requirements（抵扣/余额流水归档转储 ≥3 年不 DROP，P-1）；显式补充"禁止物理外键"条款（P-3） |
