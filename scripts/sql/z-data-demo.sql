-- ============================================================
-- MeterFlow 演示数据（骨架阶段；文件名以 z- 开头保证在 schema 之后执行）
-- 演示口令统一为 demo（SHA-256 存储，ET4 同口径）
-- ============================================================
USE meterflow_platform;

-- 租户：星澜科技（演示态：余额预警见 billing 域接入后生效）
INSERT IGNORE INTO tenant (id, name, email, password_hash, status, is_whitelist)
VALUES (1001, '星澜科技', 'zhangwei@xinglan.com', SHA2('demo', 256), 'normal', 0);

-- 部门树：研发部 / 算法部 / 研发部-平台组
INSERT IGNORE INTO department (id, tenant_id, parent_id, name) VALUES
 (2001, 1001, NULL, '研发部'),
 (2002, 1001, NULL, '算法部'),
 (2003, 1001, 2001, '平台组');

-- 预设角色（tenant_id NULL=平台模板）
INSERT IGNORE INTO role (id, tenant_id, name, role_type, status) VALUES
 (3001, NULL, '管理员',    'preset', 'active'),
 (3002, NULL, '财务',      'preset', 'active'),
 (3003, NULL, '成员',      'preset', 'active'),
 (3004, NULL, '只读成员',  'preset', 'active');

-- 角色权限点（6.1 权限矩阵）
INSERT IGNORE INTO role_permission (id, role_id, perm_code) VALUES
 (90001, 3001, 'overview:view'), (90002, 3001, 'wallet:view'), (90003, 3001, 'wallet:recharge'),
 (90004, 3001, 'bills:view'), (90005, 3001, 'bills:detail'), (90006, 3001, 'bills:export'),
 (90007, 3001, 'packages:view'), (90008, 3001, 'packages:buy'), (90009, 3001, 'disputes:view'),
 (90010, 3001, 'disputes:create'), (90011, 3001, 'keys:manage'), (90012, 3001, 'org:manage'),
 (90013, 3001, 'notify:view'), (90014, 3001, 'profile:edit'),
 (91001, 3002, 'overview:view'), (91002, 3002, 'wallet:view'), (91003, 3002, 'wallet:recharge'),
 (91004, 3002, 'bills:view'), (91005, 3002, 'bills:detail'), (91006, 3002, 'bills:export'),
 (91007, 3002, 'packages:view'), (91008, 3002, 'packages:buy'), (91009, 3002, 'disputes:view'),
 (91010, 3002, 'disputes:create'), (91011, 3002, 'profile:edit'),
 (92001, 3003, 'overview:view'), (92002, 3003, 'bills:view'), (92003, 3003, 'bills:detail'),
 (92004, 3003, 'notify:view'), (92005, 3003, 'profile:edit'),
 (93001, 3004, 'overview:view'), (93002, 3004, 'notify:view'), (93003, 3004, 'profile:edit');

-- 成员（口令 demo）
INSERT IGNORE INTO member (id, tenant_id, name, email, password_hash, dept_id, role_id, status) VALUES
 (4001, 1001, '张伟', 'zhangwei@xinglan.com', SHA2('demo', 256), NULL, 3001, 'active'),
 (4002, 1001, '李娜', 'lina@xinglan.com',     SHA2('demo', 256), NULL, 3002, 'active'),
 (4003, 1001, '王芳', 'wangfang@xinglan.com', SHA2('demo', 256), 2001, 3003, 'active'),
 (4004, 1001, '赵磊', 'zhaolei@xinglan.com',  SHA2('demo', 256), 2001, 3004, 'active'),
 (4005, 1001, '陈晨', 'chenchen@xinglan.com', SHA2('demo', 256), 2002, 3003, 'active');

-- 平台账号（六岗位，口令 demo）
INSERT IGNORE INTO platform_user (id, name, email, password_hash, role, status) VALUES
 (5001, '系统管理员', 'root@meterflow.cn',     SHA2('demo', 256), 'sadmin',   'active'),
 (5002, '钱运',       'qianyun@meterflow.cn',  SHA2('demo', 256), 'ops',      'active'),
 (5003, '孙倩',       'sunqian@meterflow.cn',  SHA2('demo', 256), 'cs',       'active'),
 (5004, '周审',       'zhoushen@meterflow.cn', SHA2('demo', 256), 'reviewer', 'active'),
 (5005, '吴财',       'wucai@meterflow.cn',    SHA2('demo', 256), 'fin',      'active'),
 (5006, '郑明',       'zhengming@meterflow.cn',SHA2('demo', 256), 'audit',    'active');

-- 站内通知（对齐原型演示态）
INSERT IGNORE INTO notification (id, tenant_id, type, title, content, is_read) VALUES
 (6001, 1001, 'warn',    '余额预警 · 分级停服', '余额 -37.50 元，已停用本月消费最高产品「LLM 推理 API」，宽限期 3 天，请及时充值。', 0),
 (6002, 1001, 'info',    '争议已受理', '工单 DSP20261103-01 已由客服孙倩受理，处理中。', 1),
 (6003, 1001, 'success', '赠送额度到账', '万圣节活动赠送：LLM 推理 API 输出 Token 5 万，有效期至 2026-11-05。', 1);

-- 平台配置（统一透支阈值 / 充值单笔上限，R24/R40）
INSERT IGNORE INTO platform_config (id, config_key, config_val, remark) VALUES
 (7001, 'overdraft.unified.cents', '-10000',  '平台统一透支阈值（分，-100 元）'),
 (7002, 'recharge.max.cents',      '10000000','充值单笔上限（分，10 万元）');
