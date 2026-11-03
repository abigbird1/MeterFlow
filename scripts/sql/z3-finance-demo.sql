-- ============================================================
-- MeterFlow finance 演示数据（对齐原型演示态；z3 在 schema 之后执行）
-- ============================================================
USE meterflow_finance;

-- 历史账单（2026-10 已确认·已调整；2026-09 已结清）
INSERT IGNORE INTO bill (id, bill_no, tenant_id, period, original_cents, adjust_cents, payable_cents,
                         status, is_adjusted, is_disputed, created_at) VALUES
 (10001, 'B2026-10', 1001, '2026-10', 128640, -4320, 124320, 'confirmed', 1, 0, '2026-10-31 23:59:00'),
 (10002, 'B2026-09', 1001, '2026-09', 110375, 0, 110375, 'settled', 0, 0, '2026-09-30 23:59:00');

INSERT IGNORE INTO bill_section (id, bill_id, product_id, version, range_start, range_end, amount_cents) VALUES
 (10011, 10001, 1001, 'v1', '2026-10-01', '2026-10-13', 52830),
 (10012, 10001, 1001, 'v2', '2026-10-14', '2026-10-31', 57460),
 (10013, 10001, 1002, 'v1', '2026-10-01', '2026-10-31', 9650),
 (10021, 10002, 1001, 'v1', '2026-09-01', '2026-09-30', 92135);

INSERT IGNORE INTO bill_dept_sub (id, bill_id, dept_name, amount_cents) VALUES
 (10031, 10001, '研发部', 64210),
 (10032, 10001, '算法部', 48630),
 (10033, 10001, '未分配', 15800);

-- 差错单（1 待处理 + 1 冲正中 + 1 已冲正 + 1 转人工）
INSERT IGNORE INTO recon_error (id, err_no, recon_date, err_type, tenant_id, description, impact_cents, status, adj_no) VALUES
 (20001, 'REC-20261102-07', '2026-11-02', '抵扣不符', 1001, '事件 EV-8812 资源包抵扣重复扣减 28.40 元', -2840, 'todo', NULL),
 (20002, 'REC-20261028-01', '2026-10-28', '重复计费', 1001, '10-21 输出 Token 重复计费 43.20 元（争议 DSP20261028-01）', -4320, 'corrected', 'ADJ20261030-01');

-- 调整单（1 已生效示例）
INSERT IGNORE INTO adjustment_order (id, adj_no, adj_type, related_type, related_no, target_bill, amount_cents,
                                     reason, initiator, stage, review1_by, review1_at, review2_by, review2_at, effect_note) VALUES
 (21001, 'ADJ20261030-01', 'adjust_red', 'error', 'REC-20261028-01', 'B2026-10', -4320,
  '重复计费退费（争议补偿）', '吴财（财务）', 'done', '周审（审核员）', '2026-10-30 16:02:00',
  '吴财（财务）', '2026-10-30 16:30:00', '已生效，B2026-10 应付金额已更新');

-- 争议工单（1 处理中 + 1 已超时升级待受理）
INSERT IGNORE INTO dispute (id, dispute_no, tenant_id, bill_no, amount_cents, reason, status,
                            sla_hours, sla_deadline, is_escalated, created_at) VALUES
 (22001, 'DSP20261103-01', 1001, 'B2026-10', 1860, '11-02 晚间批量调用明细与业务系统日志不符，申请核验', 'processing', 48, '2026-11-04 15:40:00', 0, '2026-11-02 15:40:00'),
 (22002, 'DSP20261101-02', 1001, 'B2026-10', 86000, '10 月账单中 Embedding 费用明显高于日常水平', 'pending', 24, '2026-11-02 08:00:00', 1, '2026-11-01 08:00:00');

INSERT IGNORE INTO dispute_reply (id, dispute_id, replier, content, created_at) VALUES
 (23001, 22001, '孙倩（客服）', '已受理，正在核对 11-02 事件流水与去重记录。', '2026-11-02 16:20:00');
