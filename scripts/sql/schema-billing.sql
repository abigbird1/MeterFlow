-- ============================================================
-- MeterFlow · billing 库（billing-service：计量/计价/抵扣/账户/实例台账/停服判定）
-- 热路径内聚库（03 拆分原则 S1）；ShardingSphere 逻辑表 5 张（标记 SHARDED）
-- ============================================================
CREATE DATABASE IF NOT EXISTS meterflow_billing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE meterflow_billing;

-- 1. 产品
CREATE TABLE IF NOT EXISTS product (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  name        VARCHAR(64)  NOT NULL COMMENT '产品名（LLM 推理 API ...）',
  description VARCHAR(255) NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'on' COMMENT 'on/off',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='产品目录';

-- 2. 计量指标（阶梯档位/内置免费额度随指标定义）
CREATE TABLE IF NOT EXISTS product_metric (
  id           BIGINT        NOT NULL COMMENT '雪花ID',
  product_id   BIGINT        NOT NULL COMMENT '产品ID',
  metric_key   VARCHAR(32)   NOT NULL COMMENT '指标键：calls/in_tok/out_tok/storage',
  name         VARCHAR(64)   NOT NULL COMMENT '指标名',
  unit         VARCHAR(16)   NOT NULL COMMENT '计价单位：次/千Token/GB·月',
  tier_mode    VARCHAR(16)   NOT NULL COMMENT '阶梯模式：seg 累计分段/flat 档位单价（R10）',
  tier_json    JSON          NOT NULL COMMENT '阶梯档位：[{from,to,price}]（整体读写不检索）',
  free_quota   DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '内置免费额度（原生单位，自然月重置 R13）',
  free_unit    VARCHAR(16)   NOT NULL COMMENT '额度单位：次/Token/GB',
  cursor_qty   DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '账期累计游标（分段计价用，R10）',
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_key (product_id, metric_key)
) ENGINE=InnoDB COMMENT='计量指标与计价项';

-- 3. 价格版本（即时生效·账期内分段 R11）
CREATE TABLE IF NOT EXISTS price_version (
  id           BIGINT       NOT NULL COMMENT '雪花ID',
  product_id   BIGINT       NOT NULL COMMENT '产品ID',
  version_no   INT          NOT NULL COMMENT '版本号 v1/v2...',
  effective_at DATETIME(3)  NOT NULL COMMENT '生效时刻（即时生效）',
  note         VARCHAR(255) NULL COMMENT '调价说明（敏感操作留痕 R11）',
  created_by   VARCHAR(64)  NULL COMMENT '发布人',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_ver (product_id, version_no),
  KEY idx_product_effective (product_id, effective_at)
) ENGINE=InnoDB COMMENT='价格版本';

-- 4. 版本单价明细（tier_json：版本化阶梯价，NULL 时回退 product_metric.tier_json——04 v1.4）
CREATE TABLE IF NOT EXISTS price_item (
  id          BIGINT        NOT NULL COMMENT '雪花ID',
  version_id  BIGINT        NOT NULL COMMENT '价格版本ID',
  metric_id   BIGINT        NOT NULL COMMENT '指标ID',
  price       DECIMAL(16,6) NOT NULL COMMENT '单价（元/计价单位，最小 0.0001，规范 4）',
  tier_json   JSON          NULL COMMENT '版本化阶梯价 [{from,to,price}]；NULL=沿用指标配置',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ver_metric (version_id, metric_id)
) ENGINE=InnoDB COMMENT='版本单价明细';

-- 5. 用量事件 —— SHARDED：逻辑表，按月物理分表 usage_event_yyyyMM，分片键 arrive_time
--    全局唯一边界：Redis 判重前置（TTL≥48h），库内唯一索引兜底（规范 5.3）
CREATE TABLE IF NOT EXISTS usage_event (
  id             BIGINT        NOT NULL COMMENT '雪花ID',
  ingest_system  VARCHAR(32)   NOT NULL COMMENT '上报系统标识',
  event_id       VARCHAR(64)   NOT NULL COMMENT '上报方事件ID（复合事件 R1）',
  tenant_id      BIGINT        NOT NULL COMMENT '租户ID（由 Key 推导）',
  dept_id        BIGINT        NULL COMMENT '部门ID（Key 绑定部门；NULL=未分配）',
  dept_name      VARCHAR(64)   NULL COMMENT '部门名快照（明细展示）',
  product_id     BIGINT        NOT NULL COMMENT '产品ID',
  metrics        JSON          NOT NULL COMMENT '指标集 {key:原生单位量}（整体读写不检索）',
  biz_no         VARCHAR(64)   NULL COMMENT '业务流水号（指纹兜底字段 R3）',
  arrive_time    DATETIME(3)   NOT NULL COMMENT '到达时刻（分片键·计费时间基准 R5）',
  price_version  VARCHAR(8)    NOT NULL COMMENT '适用价格版本（I9）',
  cost_amount    DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '计费总额（元·过程金额 4 位小数，规范 4）',
  status         VARCHAR(16)   NOT NULL DEFAULT 'normal' COMMENT 'normal/dup/manual（迟到转人工 R6）',
  origin_event_id BIGINT       NULL COMMENT '重复事件指向的首次事件ID（R4）',
  created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_event (ingest_system, event_id),
  UNIQUE KEY uk_tenant_product_bizno (tenant_id, product_id, biz_no),
  KEY idx_tenant_arrive (tenant_id, arrive_time)
) ENGINE=InnoDB COMMENT='用量事件（append-only，P2；保留 1 年）';

-- 6. 日切快照（定案不回溯 R18）
CREATE TABLE IF NOT EXISTS daily_snapshot (
  id            BIGINT        NOT NULL COMMENT '雪花ID',
  snapshot_date DATE         NOT NULL COMMENT '快照日期（UTC+8 零点锁定）',
  tenant_id     BIGINT        NOT NULL,
  event_count   BIGINT        NOT NULL DEFAULT 0 COMMENT '当日事件数（去重后）',
  usage_json    JSON          NULL COMMENT '各指标累计（整体读写）',
  cost_cents    BIGINT        NOT NULL DEFAULT 0 COMMENT '当日计费汇总（分，规范 4）',
  status        VARCHAR(16)   NOT NULL DEFAULT 'locked' COMMENT 'locked 定案',
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_date_tenant (snapshot_date, tenant_id)
) ENGINE=InnoDB COMMENT='日切快照';

-- 7. 内置免费额度（租户×产品×指标，自然月重置不结转 R13）
CREATE TABLE IF NOT EXISTS free_quota (
  id          BIGINT        NOT NULL COMMENT '雪花ID',
  tenant_id   BIGINT        NOT NULL,
  product_id  BIGINT        NOT NULL,
  metric_id   BIGINT        NOT NULL,
  period      CHAR(7)       NOT NULL COMMENT '账期 yyyy-MM',
  total_quota DECIMAL(20,4) NOT NULL COMMENT '总额度（原生单位）',
  remaining   DECIMAL(20,4) NOT NULL COMMENT '剩余（扣减在租户串行事务内）',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_metric_period (tenant_id, product_id, metric_id, period)
) ENGINE=InnoDB COMMENT='内置免费额度';

-- 8. 赠送额度（独立权益·先到期先扣 A2）
CREATE TABLE IF NOT EXISTS gift_grant (
  id          BIGINT        NOT NULL COMMENT '雪花ID',
  grant_no    VARCHAR(32)   NOT NULL COMMENT '发放编号（GT*）',
  tenant_id   BIGINT        NOT NULL,
  product_id  BIGINT        NOT NULL,
  metric_id   BIGINT        NOT NULL,
  name        VARCHAR(64)   NOT NULL COMMENT '活动/来源名',
  total       DECIMAL(20,4) NOT NULL COMMENT '总量（原生单位）',
  remaining   DECIMAL(20,4) NOT NULL COMMENT '剩余',
  expire_date DATE          NOT NULL COMMENT '有效期至（先到期先扣排序键）',
  source      VARCHAR(16)   NOT NULL COMMENT 'ops 运营发放/dispute 争议补偿（A5）',
  status      VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT 'active/exhausted/expired',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_grant_no (grant_no),
  KEY idx_tenant_metric_expire (tenant_id, metric_id, expire_date)
) ENGINE=InnoDB COMMENT='赠送额度（先于内置消耗，R13）';

-- 9. 资源包实例（余量归 billing：抵扣链强一致 S4；不退订到期清零 R33）
CREATE TABLE IF NOT EXISTS package_instance (
  id              BIGINT        NOT NULL COMMENT '雪花ID',
  tenant_id       BIGINT        NOT NULL,
  pkg_type        VARCHAR(16)   NOT NULL COMMENT 'spec 指定产品包/common 通用包（R30）',
  title           VARCHAR(128)  NOT NULL,
  product_id      BIGINT        NULL COMMENT 'spec：绑定产品；common 为 NULL',
  metric_key      VARCHAR(32)   NULL COMMENT 'spec：绑定指标；common 为 NULL',
  total           DECIMAL(20,4) NOT NULL COMMENT '总量（Token/次：原生单位；元：分，规范 4）',
  remaining       DECIMAL(20,4) NOT NULL COMMENT '余量',
  unit            VARCHAR(16)   NOT NULL COMMENT 'Token/次/元',
  expire_date     DATE          NOT NULL COMMENT '有效期至（到期清零 R33）',
  status          VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT 'active/exhausted/expired',
  source_order_no VARCHAR(32)   NOT NULL COMMENT '来源订单号（equity 库，MQ 事件发放 S4）',
  created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_status_expire (tenant_id, status, expire_date)
) ENGINE=InnoDB COMMENT='资源包实例（购买/抵扣/过期全程留痕 R34）';

-- 10. 余额账户（租户 1:1；行级锁串行 P7；有限透支 R16/R22）
CREATE TABLE IF NOT EXISTS balance_account (
  id              BIGINT      NOT NULL COMMENT '雪花ID',
  tenant_id       BIGINT      NOT NULL,
  balance_cents   BIGINT      NOT NULL DEFAULT 0 COMMENT '当前余额（分；允许负至透支阈值）',
  overdraft_cents BIGINT      NOT NULL DEFAULT -10000 COMMENT '透支阈值（分，默认-10000=-100元；白名单覆盖 R24）',
  status          VARCHAR(16) NOT NULL DEFAULT 'normal' COMMENT 'normal/warning/suspended（分级停服 R22）',
  version         BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本（行锁+版本双保险）',
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='余额账户（资金写路径唯一入口 P3）';

-- 11. 充值记录（模拟支付·即时到账 R16；单笔限额 R40）
CREATE TABLE IF NOT EXISTS recharge_record (
  id           BIGINT      NOT NULL COMMENT '雪花ID',
  tenant_id    BIGINT      NOT NULL,
  amount_cents BIGINT      NOT NULL COMMENT '充值金额（分；1~10000000，R40）',
  method       VARCHAR(32) NOT NULL COMMENT '支付宝/微信/对公（模拟）',
  status       VARCHAR(16) NOT NULL DEFAULT 'success',
  pay_no       VARCHAR(32) NOT NULL COMMENT '支付流水号',
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pay_no (pay_no),
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB COMMENT='充值记录（不支持提现退款）';

-- 12. 抵扣流水 —— SHARDED：逻辑表，按月分表 deduction_record_yyyyMM，分片键 created_at
CREATE TABLE IF NOT EXISTS deduction_record (
  id           BIGINT        NOT NULL COMMENT '雪花ID',
  tenant_id    BIGINT        NOT NULL,
  event_id     BIGINT        NOT NULL COMMENT '用量事件ID（一对一，D2）',
  product_id   BIGINT        NOT NULL,
  cost_amount  DECIMAL(20,6) NOT NULL COMMENT '事件总费用（元·4位小数，规范 4）',
  chain_json   JSON          NOT NULL COMMENT '抵扣链快照（①→④ 步骤，整体读写 D2）',
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '时间（分片键）',
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at),
  KEY idx_event (event_id)
) ENGINE=InnoDB COMMENT='抵扣主流水（append-only，保留 1 年）';

-- 13. 抵扣来源明细 —— SHARDED：与主流水同键同表尾；D2 两级结构
CREATE TABLE IF NOT EXISTS deduction_item (
  id           BIGINT        NOT NULL COMMENT '雪花ID',
  record_id    BIGINT        NOT NULL COMMENT '抵扣主流水ID',
  tenant_id    BIGINT        NOT NULL,
  source_type  VARCHAR(16)   NOT NULL COMMENT 'gift/builtin/pkg_spec/pkg_common/balance',
  source_id    BIGINT        NULL COMMENT '来源实例ID（额度/包）；balance 为 NULL',
  source_name  VARCHAR(128)  NOT NULL COMMENT '来源名快照（账单可解释性）',
  amount       DECIMAL(20,6) NOT NULL COMMENT '本源抵扣额（元·4位小数）',
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '时间（分片键）',
  PRIMARY KEY (id),
  KEY idx_source (source_type, source_id) COMMENT '抵扣来源明细（R34 留痕查询）',
  KEY idx_record (record_id)
) ENGINE=InnoDB COMMENT='抵扣来源明细（对账与解释粒度）';

-- 14. 余额变动流水 —— SHARDED：按月分表 account_transaction_yyyyMM（充值/抵扣/调整三入口 P3）
CREATE TABLE IF NOT EXISTS account_transaction (
  id                   BIGINT      NOT NULL COMMENT '雪花ID',
  tenant_id            BIGINT      NOT NULL,
  tx_type              VARCHAR(16) NOT NULL COMMENT 'recharge/deduct/adjust',
  ref_type             VARCHAR(32) NOT NULL COMMENT '关联单据类型（充值/事件/调整单）',
  ref_id               BIGINT      NOT NULL COMMENT '关联单据ID',
  amount_cents         BIGINT      NOT NULL COMMENT '变动额（分，正负号）',
  balance_after_cents  BIGINT      NOT NULL COMMENT '变动后余额（分）',
  created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '时间（分片键）',
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB COMMENT='余额变动流水（计费vs账户对账依据 R26）';

-- 15. 停服/恢复记录（分级停服 R22/R23，联动 M8 判定留痕）
CREATE TABLE IF NOT EXISTS suspension_log (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   BIGINT       NOT NULL,
  action      VARCHAR(16)  NOT NULL COMMENT 'warn/suspend/restore',
  detail      VARCHAR(512) NOT NULL COMMENT '触发原因（余额/阈值/宽限期）',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB COMMENT='停服/恢复记录';

-- 16. 本地消息表（billing 侧出站事件：计费完成/余额变动；最终一致 R-8）
CREATE TABLE IF NOT EXISTS local_message (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  topic         VARCHAR(64)  NOT NULL COMMENT 'MQ 主题（common/event 契约）',
  biz_key       VARCHAR(64)  NOT NULL COMMENT '业务幂等键',
  payload       JSON         NOT NULL COMMENT '事件体',
  status        VARCHAR(16)  NOT NULL DEFAULT 'init' COMMENT 'init/sent/confirmed',
  retry_count   INT          NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_topic_biz (topic, biz_key)
) ENGINE=InnoDB COMMENT='本地消息表（出站事件，消费方幂等）';

-- 17. 异步导出任务（05 阶段 AF2：账单/明细 CSV 异步导出 + 下载中心）
CREATE TABLE IF NOT EXISTS export_task (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   BIGINT       NOT NULL COMMENT '发起租户',
  task_type   VARCHAR(16)  NOT NULL COMMENT 'bill 账单/events 消费明细',
  params      JSON         NOT NULL COMMENT '导出参数（billNo/过滤条件，整体读写）',
  status      VARCHAR(16)  NOT NULL DEFAULT 'processing' COMMENT 'processing/done/failed',
  file_key    VARCHAR(255) NULL COMMENT '生成文件标识（服务本地卷 deploy/data/exports/）',
  file_rows   BIGINT       NULL COMMENT '导出行数',
  fail_reason VARCHAR(512) NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at DATETIME(3)  NULL,
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB COMMENT='异步导出任务（文件保留 7 天，XXL-Job 清理）';

-- 18. 阶梯累计游标（租户×产品×指标×账期维度；04 v1.3 评审修正——原指标表级游标无法支撑多租户，R10）
CREATE TABLE IF NOT EXISTS usage_cursor (
  id         BIGINT        NOT NULL COMMENT '雪花ID',
  tenant_id  BIGINT        NOT NULL,
  product_id BIGINT        NOT NULL,
  metric_id  BIGINT        NOT NULL,
  period     CHAR(7)       NOT NULL COMMENT '账期 yyyy-MM',
  qty        DECIMAL(20,4) NOT NULL DEFAULT 0 COMMENT '账期累计用量（原生单位，仅计费用量）',
  created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_metric_period (tenant_id, product_id, metric_id, period)
) ENGINE=InnoDB COMMENT='阶梯累计游标（分段计价 R10）';

-- 15. 租户月度预算与告警标记（消费达 80%/100% 站内告警，标记位防重复发送）
CREATE TABLE IF NOT EXISTS budget (
  id            BIGINT      NOT NULL COMMENT '雪花ID',
  tenant_id     BIGINT      NOT NULL,
  period        CHAR(7)     NOT NULL COMMENT '账期 yyyy-MM',
  budget_cents  BIGINT      NOT NULL COMMENT '预算（分）',
  notified80    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '80% 告警已发',
  notified100   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '100% 告警已发',
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_period (tenant_id, period)
) ENGINE=InnoDB COMMENT='租户月度预算与告警标记';
