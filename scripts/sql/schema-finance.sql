-- ============================================================
-- MeterFlow · finance 库（finance-service：账单/对账差错/催缴/争议审批）
-- 与热路径隔离（03 拆分原则 S3）；数据经 MQ 事件与只读接口汇聚
-- ============================================================
CREATE DATABASE IF NOT EXISTS meterflow_finance DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE meterflow_finance;

-- 1. 账单（租户级一张/账期 R17；原额+调整=应付 R19；账期 uk 防重跑 R42）
CREATE TABLE IF NOT EXISTS bill (
  id             BIGINT       NOT NULL COMMENT '雪花ID',
  bill_no        VARCHAR(32)  NOT NULL COMMENT '账单号（B2026-10，对外业务号）',
  tenant_id      BIGINT       NOT NULL,
  period         CHAR(7)      NOT NULL COMMENT '账期 yyyy-MM（自然月 R17）',
  original_cents BIGINT       NOT NULL DEFAULT 0 COMMENT '原额（分，Σ逐笔舍入汇总）',
  adjust_cents   BIGINT       NOT NULL DEFAULT 0 COMMENT '调整合计（分，Σ生效调整单）',
  payable_cents  BIGINT       NOT NULL DEFAULT 0 COMMENT '应付=原额+调整（I4）',
  status         VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft已出账/confirmed已确认/settled已结清',
  is_adjusted    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '已调整标记',
  is_disputed    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '争议中标记',
  confirmed_at   DATETIME(3)  NULL,
  settled_at     DATETIME(3)  NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_no (bill_no),
  UNIQUE KEY uk_tenant_period (tenant_id, period) COMMENT '同租户同账期唯一（重跑幂等 R42）'
) ENGINE=InnoDB COMMENT='账单（保留≥3年，不做物理删除）';

-- 2. 账单分段（产品×价格版本 R19）
CREATE TABLE IF NOT EXISTS bill_section (
  id           BIGINT       NOT NULL COMMENT '雪花ID',
  bill_id      BIGINT       NOT NULL COMMENT '账单ID',
  product_id   BIGINT       NOT NULL,
  version      VARCHAR(8)   NOT NULL COMMENT '价格版本',
  range_start  DATE         NOT NULL COMMENT '计费区间起',
  range_end    DATE         NOT NULL COMMENT '计费区间止',
  amount_cents BIGINT       NOT NULL COMMENT '分段金额（分）',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_bill (bill_id)
) ENGINE=InnoDB COMMENT='账单分段';

-- 3. 部门小计（部门级数据隔离口径 R19）
CREATE TABLE IF NOT EXISTS bill_dept_sub (
  id           BIGINT      NOT NULL COMMENT '雪花ID',
  bill_id      BIGINT      NOT NULL COMMENT '账单ID',
  dept_name    VARCHAR(64) NOT NULL COMMENT '部门名（含"未分配"）',
  amount_cents BIGINT      NOT NULL COMMENT '小计（分）',
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_bill (bill_id)
) ENGINE=InnoDB COMMENT='账单部门小计';

-- 4. 调整单（红冲/蓝补/赠送额度单；两级审批 R28/R29，初审≠复审 I6）
CREATE TABLE IF NOT EXISTS adjustment_order (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  adj_no        VARCHAR(32)  NOT NULL COMMENT '调整单号（ADJ*）',
  adj_type      VARCHAR(16)  NOT NULL COMMENT 'adjust_red 红字冲销/adjust_blue 蓝字补记/grant 赠送额度单',
  related_type  VARCHAR(16)  NOT NULL COMMENT '来源：error 差错/dispute 争议',
  related_no    VARCHAR(32)  NOT NULL COMMENT '来源单号',
  target_bill   VARCHAR(32)  NULL COMMENT '目标账单号；赠额度类为 NULL',
  amount_cents  BIGINT       NOT NULL COMMENT '调整金额（分，红冲为负）',
  reason        VARCHAR(512) NOT NULL COMMENT '调整原因',
  initiator     VARCHAR(64)  NOT NULL COMMENT '发起人（财务/客服发起）',
  stage         VARCHAR(16)  NOT NULL DEFAULT 'review1' COMMENT 'review1待初审/review2待复审/done生效/rejected驳回',
  review1_by    VARCHAR(64)  NULL COMMENT '初审人（审核员）',
  review1_at    DATETIME(3)  NULL,
  review2_by    VARCHAR(64)  NULL COMMENT '复审人（财务；服务层校验≠初审人 I6）',
  review2_at    DATETIME(3)  NULL,
  effect_note   VARCHAR(255) NULL COMMENT '生效影响（回写账单/账户）',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_adj_no (adj_no),
  KEY idx_stage (stage),
  KEY idx_related (related_type, related_no)
) ENGINE=InnoDB COMMENT='调整单（不改原始单据 R21；生效回写账单/余额）';

-- 5. 差错单（R26–R28）
CREATE TABLE IF NOT EXISTS recon_error (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  err_no        VARCHAR(32)  NOT NULL COMMENT '差错编号（REC*）',
  recon_date    DATE         NOT NULL COMMENT '发现日期',
  err_type      VARCHAR(16)  NOT NULL COMMENT '丢事件/重复计费/抵扣不符/账户不平（R27）',
  tenant_id     BIGINT       NOT NULL,
  description   VARCHAR(512) NOT NULL,
  impact_cents  BIGINT       NULL COMMENT '金额影响（分，可为 NULL）',
  status        VARCHAR(16)  NOT NULL DEFAULT 'todo' COMMENT 'todo/correcting冲正中/corrected已冲正/manual转人工/closed',
  adj_no        VARCHAR(32)  NULL COMMENT '关联调整单',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_err_no (err_no),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='差错单';

-- 6. 每日对账结果（两口径 R26）
CREATE TABLE IF NOT EXISTS daily_recon (
  id          BIGINT      NOT NULL COMMENT '雪花ID',
  recon_date  DATE        NOT NULL COMMENT '对账日期',
  event_count BIGINT      NOT NULL DEFAULT 0 COMMENT '当日事件数',
  diff_ab     INT         NOT NULL DEFAULT 0 COMMENT '计量vs计费 差异笔数',
  diff_bc     INT         NOT NULL DEFAULT 0 COMMENT '计费vs账户 差异笔数',
  result      VARCHAR(16) NOT NULL DEFAULT 'ok' COMMENT 'ok/err',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_recon_date (recon_date) COMMENT '每日一次（重跑幂等 R42）'
) ENGINE=InnoDB COMMENT='每日对账结果';

-- 7. 争议工单（R35–R38、R44：已出账/已确认可发起）
CREATE TABLE IF NOT EXISTS dispute (
  id             BIGINT       NOT NULL COMMENT '雪花ID',
  dispute_no     VARCHAR(32)  NOT NULL COMMENT '工单号（DSP*）',
  tenant_id      BIGINT       NOT NULL,
  bill_no        VARCHAR(32)  NOT NULL COMMENT '关联账单',
  amount_cents   BIGINT       NOT NULL COMMENT '争议金额（分）',
  reason         VARCHAR(1024) NOT NULL COMMENT '争议理由',
  status         VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending待受理/processing处理中/solved已解决/closed已关闭',
  sla_hours      INT          NOT NULL COMMENT 'SLA 时限（<1000元 48h / ≥1000元 24h，R36）',
  sla_deadline   DATETIME(3)  NOT NULL COMMENT 'SLA 截止时刻',
  is_escalated   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '超时升级标记',
  compensation   VARCHAR(255) NULL COMMENT '补偿结果（调整单/赠送额度单）',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dispute_no (dispute_no),
  KEY idx_status_sla (status, sla_deadline) COMMENT 'SLA 超时扫描路径'
) ENGINE=InnoDB COMMENT='争议工单';

-- 8. 工单处理回复
CREATE TABLE IF NOT EXISTS dispute_reply (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  dispute_id  BIGINT       NOT NULL COMMENT '工单ID',
  replier     VARCHAR(64)  NOT NULL COMMENT '回复人（客服）',
  content     VARCHAR(1024) NOT NULL COMMENT '回复内容',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_dispute (dispute_id)
) ENGINE=InnoDB COMMENT='工单处理回复';

-- 9. 催缴记录（R25：全停才催缴，站内通知+留痕）
CREATE TABLE IF NOT EXISTS urge_record (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id     BIGINT       NOT NULL,
  balance_cents BIGINT       NOT NULL COMMENT '催缴时余额快照（分）',
  detail        VARCHAR(255) NOT NULL COMMENT '催缴说明',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB COMMENT='催缴记录';

-- 10. 幂等任务表（XXL-Job 执行批次幂等 R42；各服务同构，本库为 finance 任务面）
CREATE TABLE IF NOT EXISTS task_instance (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  task_name   VARCHAR(64)  NOT NULL COMMENT '任务名：daily_cut/monthly_bill/daily_recon/expire_clean...',
  biz_key     VARCHAR(64)  NOT NULL COMMENT '业务幂等键（如日期/账期）',
  status      VARCHAR(16)  NOT NULL DEFAULT 'init' COMMENT 'init/running/success/failed',
  retry_count INT          NOT NULL DEFAULT 0,
  last_run_at DATETIME(3)  NULL,
  remark      VARCHAR(512) NULL COMMENT '失败原因/告警说明（R42）',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_biz (task_name, biz_key) COMMENT '任务幂等：同任务同键仅成功一次'
) ENGINE=InnoDB COMMENT='幂等任务实例';
