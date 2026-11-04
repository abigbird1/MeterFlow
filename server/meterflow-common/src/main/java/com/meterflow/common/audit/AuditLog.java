package com.meterflow.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 审计注解（架构原则 P5）：敏感操作强制留痕，动作类型枚举可扩展（03 附录 A 预留 Agent 动作） */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /** 动作名，如：代查租户数据 / 发起冲正（调整单） / 价格版本发布 */
    String action();
    /** 是否强制留痕的敏感操作（代查/冲正/停服恢复/调价/额度与白名单调整） */
    boolean sensitive() default false;
}
