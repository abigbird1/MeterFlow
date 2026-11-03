-- ============================================================
-- MeterFlow · platform 库（platform-service：租户/组织/Key/通知/审计/配置）
-- 规范：docs/rules/database-rules.md v1.0（InnoDB / utf8mb4 / 雪花ID / 只进不改）
-- ============================================================
CREATE DATABASE IF NOT EXISTS meterflow_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE meterflow_platform;

-- 1. 租户
CREATE TABLE IF NOT EXISTS tenant (
  id                  BIGINT       NOT NULL COMMENT '雪花ID',
  name                VARCHAR(128) NOT NULL COMMENT '租户名称',
  email               VARCHAR(128) NOT NULL COMMENT '注册邮箱（管理员账号）',
  password_hash       VARCHAR(128) NOT NULL COMMENT '密码散列',
  status              VARCHAR(32)  NOT NULL DEFAULT 'normal' COMMENT '状态：normal/warning/suspended/disabled',
  overdraft_cents     BIGINT       NULL COMMENT '白名单透支阈值（分）；NULL=适用平台统一阈值(-10000)',
  is_whitelist        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否透支白名单',
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB COMMENT='租户';

-- 2. 部门（多级树）
CREATE TABLE IF NOT EXISTS department (
  id           BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id    BIGINT       NOT NULL COMMENT '租户ID',
  parent_id    BIGINT       NULL COMMENT '上级部门ID；NULL=根',
  name         VARCHAR(64)  NOT NULL COMMENT '部门名称',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB COMMENT='部门树';

-- 3. 角色（预设 + 自定义，权限点勾选）
CREATE TABLE IF NOT EXISTS role (
  id           BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id    BIGINT       NULL COMMENT '租户ID；NULL=平台内置预设角色模板',
  name         VARCHAR(64)  NOT NULL COMMENT '角色名称',
  role_type    VARCHAR(16)  NOT NULL COMMENT '类型：preset/custom',
  status       VARCHAR(16)  NOT NULL DEFAULT 'active',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='租户角色';

-- 4. 角色权限点
CREATE TABLE IF NOT EXISTS role_permission (
  id          BIGINT      NOT NULL COMMENT '雪花ID',
  role_id     BIGINT      NOT NULL COMMENT '角色ID',
  perm_code   VARCHAR(64) NOT NULL COMMENT '权限点：功能域:操作（如 bills:detail）',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, perm_code)
) ENGINE=InnoDB COMMENT='角色权限点';

-- 5. 成员
CREATE TABLE IF NOT EXISTS member (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id     BIGINT       NOT NULL COMMENT '租户ID',
  name          VARCHAR(64)  NOT NULL COMMENT '姓名',
  email         VARCHAR(128) NOT NULL COMMENT '邮箱（登录名）',
  password_hash VARCHAR(128) NOT NULL COMMENT '密码散列',
  dept_id       BIGINT       NULL COMMENT '所属部门；NULL=未分配',
  role_id       BIGINT       NOT NULL COMMENT '角色ID',
  status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_email (tenant_id, email),
  KEY idx_dept (dept_id)
) ENGINE=InnoDB COMMENT='租户成员（邀请直接生效，B11）';

-- 6. 平台账号（六岗位）
CREATE TABLE IF NOT EXISTS platform_user (
  id            BIGINT       NOT NULL COMMENT '雪花ID',
  name          VARCHAR(64)  NOT NULL COMMENT '姓名',
  email         VARCHAR(128) NOT NULL COMMENT '邮箱（登录名）',
  password_hash VARCHAR(128) NOT NULL COMMENT '密码散列',
  role          VARCHAR(16)  NOT NULL COMMENT '岗位：sadmin/ops/cs/reviewer/fin/audit',
  status        VARCHAR(16)  NOT NULL DEFAULT 'active',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB COMMENT='平台账号（六岗位，需求6.2）';

-- 7. API Key（租户级多 Key，可选绑定部门，轮换并行 24h）
CREATE TABLE IF NOT EXISTS api_key (
  id              BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
  name            VARCHAR(64)  NOT NULL COMMENT 'Key 名称',
  note            VARCHAR(255) NULL COMMENT '备注用途',
  key_hash        CHAR(64)     NOT NULL COMMENT 'Key 散列（SHA-256 hex；明文仅创建时一次性返回，ET4 裁决）',
  key_mask        VARCHAR(48)  NOT NULL COMMENT '掩码展示（mfk-xxxx-****-xxxx）',
  dept_id         BIGINT       NULL COMMENT '绑定部门（事件部门归属依据）；NULL=未分配',
  products        JSON         NULL COMMENT '限定产品集；NULL=全部产品（整体读写，不检索）',
  status          VARCHAR(16)  NOT NULL DEFAULT 'enabled' COMMENT 'enabled/disabled/rotating',
  rotate_deadline DATETIME(3)  NULL COMMENT '轮换并行截止时刻（旧Key失效时刻）',
  last_used_at    DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_key_hash (key_hash),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='API Key（轮换并行24h，需求4.6）';

-- 8. 站内通知
CREATE TABLE IF NOT EXISTS notification (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  tenant_id   BIGINT       NOT NULL COMMENT '接收租户',
  type        VARCHAR(16)  NOT NULL COMMENT 'info/warn/success',
  title       VARCHAR(128) NOT NULL,
  content     VARCHAR(1024) NOT NULL,
  is_read     TINYINT(1)   NOT NULL DEFAULT 0,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB COMMENT='站内通知（仅站内渠道，默认约定#8）';

-- 9. 审计日志（ShardingSphere 逻辑表，按月分表 audit_log_yyyyMM，保留1年）
CREATE TABLE IF NOT EXISTS audit_log (
  id             BIGINT       NOT NULL COMMENT '雪花ID',
  actor_id       BIGINT       NULL COMMENT '操作人ID；系统任务为NULL',
  actor_name     VARCHAR(64)  NOT NULL COMMENT '操作人姓名',
  actor_role     VARCHAR(16)  NOT NULL COMMENT '岗位/角色',
  action         VARCHAR(64)  NOT NULL COMMENT '动作（代查/冲正/停服恢复/调价/额度白名单/写操作）',
  target         VARCHAR(255) NOT NULL COMMENT '对象',
  scope          VARCHAR(512) NULL COMMENT '范围/说明（代查四要素之一）',
  result         VARCHAR(16)  NOT NULL COMMENT '成功/驳回/...',
  is_sensitive   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '强制留痕的敏感操作',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '时间（分片键）',
  PRIMARY KEY (id),
  KEY idx_actor_time (actor_id, created_at),
  KEY idx_time (created_at)
) ENGINE=InnoDB COMMENT='审计日志（逻辑表；按月分表；保留1年，到期清理留痕 B9）';

-- 10. 平台配置（统一透支阈值/充值限额等；运营可全局调整并留痕）
CREATE TABLE IF NOT EXISTS platform_config (
  id          BIGINT       NOT NULL COMMENT '雪花ID',
  config_key  VARCHAR(64)  NOT NULL COMMENT '配置键：overdraft.unified.cents / recharge.max.cents ...',
  config_val  VARCHAR(255) NOT NULL COMMENT '配置值',
  remark      VARCHAR(255) NULL,
  updated_by  VARCHAR(64)  NULL COMMENT '最近修改人（敏感操作留痕）',
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_key (config_key)
) ENGINE=InnoDB COMMENT='平台配置（默认值：透支-10000分/充值上限10000000分）';
