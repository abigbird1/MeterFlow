-- ============================================================
-- MeterFlow billing 演示数据（对齐 04 §10 示例产品目录与原型演示态）
-- 执行顺序：schema-billing.sql 之后（z2 > z1）
-- ============================================================
USE meterflow_billing;

-- 产品与计量指标（04 §10 示例）
INSERT IGNORE INTO product (id, name, description, status) VALUES
 (1001, 'LLM 推理 API', '通用大模型推理服务', 'on'),
 (1002, 'Embedding API', '向量化服务', 'on'),
 (1003, '对象存储', '对象存储容量', 'on');

INSERT IGNORE INTO product_metric (id, product_id, metric_key, name, unit, tier_mode, tier_json, free_quota, free_unit) VALUES
 (1101, 1001, 'calls',   '调用次数',   '次',      'seg',  '[{"from":0,"to":100000,"price":0.01},{"from":100000,"to":null,"price":0.008}]', 1000,     '次'),
 (1102, 1001, 'in_tok',  '输入 Token', '千Token', 'seg',  '[{"from":0,"to":1000000,"price":0.002},{"from":1000000,"to":5000000,"price":0.0018},{"from":5000000,"to":null,"price":0.0016}]', 1000000, 'Token'),
 (1103, 1001, 'out_tok', '输出 Token', '千Token', 'seg',  '[{"from":0,"to":1000000,"price":0.006},{"from":1000000,"to":null,"price":0.0055}]', 100000,  'Token'),
 (1201, 1002, 'calls',   '调用次数',   '次',      'flat', '[{"from":0,"to":null,"price":0.001}]', 10000, '次'),
 (1301, 1003, 'storage', '存储量',     'GB·月',   'flat', '[{"from":0,"to":null,"price":0.12}]', 10,    'GB');

-- 价格版本（v1 初始 / v2 即时生效示例）
INSERT IGNORE INTO price_version (id, product_id, version_no, effective_at, note, created_by) VALUES
 (2001, 1001, 1, '2026-06-01 00:00:00', '初始定价', '系统'),
 (2002, 1001, 2, '2026-10-14 10:00:00', '输入 Token 0.0020 → 0.0018（即时生效，账期内分段计费 R11）', '钱运（运营）'),
 (2101, 1002, 1, '2026-06-01 00:00:00', '初始定价', '系统'),
 (2201, 1003, 1, '2026-06-01 00:00:00', '初始定价', '系统');

INSERT IGNORE INTO price_item (id, version_id, metric_id, price, tier_json) VALUES
 (3001, 2001, 1101, 0.0100, NULL), (3002, 2001, 1102, 0.0020, NULL), (3003, 2001, 1103, 0.0060, NULL),
 (3011, 2002, 1101, 0.0100, NULL), (3012, 2002, 1102, 0.0018, NULL), (3013, 2002, 1103, 0.0060, NULL),
 (3101, 2101, 1201, 0.0010, NULL),
 (3201, 2201, 1301, 0.1200, NULL);

-- 租户 1001 十一月内置免费额度
INSERT IGNORE INTO free_quota (id, tenant_id, product_id, metric_id, period, total_quota, remaining) VALUES
 (4001, 1001, 1001, 1101, '2026-11', 1000,    640),
 (4002, 1001, 1001, 1102, '2026-11', 1000000, 412000),
 (4003, 1001, 1001, 1103, '2026-11', 100000,  0),
 (4004, 1001, 1002, 1201, '2026-11', 10000,   8000);

-- 赠送额度（万圣节活动，先到期先扣 A2）
INSERT IGNORE INTO gift_grant (id, grant_no, tenant_id, product_id, metric_id, name, total, remaining, expire_date, source, status) VALUES
 (5001, 'GT20261029-01', 1001, 1001, 1103, '万圣节活动赠送', 50000, 12000, '2026-11-05', 'ops', 'active');

-- 资源包实例（指定包 6200 万输出 Token + 通用包 62.40 元）
INSERT IGNORE INTO package_instance (id, tenant_id, pkg_type, title, product_id, metric_key, total, remaining, unit, expire_date, status, source_order_no) VALUES
 (6001, 1001, 'spec',   'LLM 输出 Token 包',       1001, 'out_tok', 100000000, 62000000, 'Token', '2027-01-10', 'active', 'PO20261012-001'),
 (6002, 1001, 'common', '通用金额包（100 元）',     NULL, NULL,      100,       62.40,    '元',    '2026-11-08', 'active', 'PO20260928-006');

-- 余额账户（演示预警态：-37.50 元，透支阈值 -100 元）
INSERT IGNORE INTO balance_account (id, tenant_id, balance_cents, overdraft_cents, status) VALUES
 (7001, 1001, -3750, -10000, 'warning');

-- 停服记录（分级停服预警：本月消费最高产品）
INSERT IGNORE INTO suspension_log (id, tenant_id, action, detail) VALUES
 (8001, 1001, 'warn', '余额转负，停用本月消费最高产品：topProduct=LLM 推理 API');
