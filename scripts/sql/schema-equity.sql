-- ============================================================
-- MeterFlow · equity 库（equity-service：商品/订单/抢购；独立扩容扛 1,000 QPS）
-- 库存原子预扣在 Redis（AD-T4），本库为权威台账；实例发放经 MQ 事件由 billing 落地（S4）
-- ============================================================
CREATE DATABASE IF NOT EXISTS meterflow_equity DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE meterflow_equity;

-- 1. 资源包商品（指定产品包/通用包 R30；三重限制 R31）
CREATE TABLE IF NOT EXISTS goods (
  id                BIGINT       NOT NULL COMMENT '雪花ID',
  goods_type        VARCHAR(16)  NOT NULL COMMENT 'spec 指定产品包/common 通用包',
  name              VARCHAR(128) NOT NULL COMMENT '商品名',
  spec              VARCHAR(255) NOT NULL COMMENT '规格说明（内容/面额）',
  product_id        BIGINT       NULL COMMENT 'spec：绑定产品；common 为 NULL',
  metric_key        VARCHAR(32)  NULL COMMENT 'spec：绑定指标',
  face_amount_cents BIGINT       NULL COMMENT 'common：面额（分）',
  price_cents       BIGINT       NOT NULL COMMENT '售价（分）',
  valid_days        INT          NOT NULL COMMENT '有效期（天，自支付日起）',
  total_stock       INT          NOT NULL COMMENT '总库存（I5）',
  sold              INT          NOT NULL DEFAULT 0 COMMENT '已售（含锁定；权威值，Redis 为热路径预扣）',
  per_limit         INT          NOT NULL COMMENT '每人限购',
  order_limit       INT          NOT NULL COMMENT '单笔订单上限（默认 1，B 裁决）',
  window_start      DATE         NOT NULL COMMENT '开售日',
  window_end        DATE         NOT NULL COMMENT '结束日',
  status            VARCHAR(16)  NOT NULL DEFAULT 'on' COMMENT 'on/off',
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='资源包商品';

-- 2. 抢购订单（下单锁库存 R32；15 分钟超时释放 R43，支付以订单状态为准防超付）
CREATE TABLE IF NOT EXISTS goods_order (
  id             BIGINT       NOT NULL COMMENT '雪花ID',
  order_no       VARCHAR(32)  NOT NULL COMMENT '订单号（PO*，对外业务号）',
  tenant_id      BIGINT       NOT NULL,
  goods_id       BIGINT       NOT NULL,
  qty            INT          NOT NULL DEFAULT 1 COMMENT '数量（≤单笔上限）',
  amount_cents   BIGINT       NOT NULL COMMENT '应付（分）',
  status         VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/paid/closed/canceled',
  pay_expire_at  DATETIME(3)  NOT NULL COMMENT '支付截止时刻（下单+15min，超时释放键）',
  paid_at        DATETIME(3)  NULL,
  close_note     VARCHAR(255) NULL COMMENT '关闭原因（超时释放/主动取消）',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status_expire (status, pay_expire_at) COMMENT '超时释放扫描路径',
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB COMMENT='抢购订单';

-- 3. 支付流水（模拟支付；支付前校验订单状态，R43）
CREATE TABLE IF NOT EXISTS pay_record (
  id           BIGINT      NOT NULL COMMENT '雪花ID',
  order_no     VARCHAR(32) NOT NULL COMMENT '订单号',
  tenant_id    BIGINT      NOT NULL,
  amount_cents BIGINT      NOT NULL COMMENT '支付金额（分）',
  method       VARCHAR(32) NOT NULL COMMENT '支付宝/微信（模拟）',
  status       VARCHAR(16) NOT NULL DEFAULT 'success',
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_order (order_no)
) ENGINE=InnoDB COMMENT='支付流水（模拟）';

-- 4. 库存变动明细（锁库存/释放/售出 全程留痕 R34；与 Redis 预扣对账校平）
CREATE TABLE IF NOT EXISTS stock_log (
  id          BIGINT      NOT NULL COMMENT '雪花ID',
  goods_id    BIGINT      NOT NULL,
  order_no    VARCHAR(32) NOT NULL COMMENT '关联订单',
  change_qty  INT         NOT NULL COMMENT '变动量：+锁定释放回补为正、占用为负（口径：可用库存）',
  reason      VARCHAR(16) NOT NULL COMMENT 'lock/release/sale',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_goods_time (goods_id, created_at)
) ENGINE=InnoDB COMMENT='库存变动明细（I5：已售+锁定≤总库存）';

-- 5. 本地消息表（equity 侧出站事件：订单已支付 → billing 发放实例，S4）
CREATE TABLE IF NOT EXISTS outbox_message (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  topic         VARCHAR(64)  NOT NULL COMMENT 'MQ 主题（T_ORDER_PAID 等）',
  biz_key       VARCHAR(64)  NOT NULL COMMENT '业务幂等键（order_no）',
  payload       JSON         NOT NULL COMMENT '事件体',
  status        VARCHAR(16)  NOT NULL DEFAULT 'init' COMMENT 'init/sent/confirmed',
  retry_count   INT          NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_topic_biz (topic, biz_key)
) ENGINE=InnoDB COMMENT='本地消息表（与订单同事务写入）';
