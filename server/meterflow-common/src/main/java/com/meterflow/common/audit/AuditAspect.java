package com.meterflow.common.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 审计切面骨架：真实落库（MQ → platform 入 ES）在各服务接入 RocketMQ 后实现 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("[AUDIT] action={} sensitive={} cost={}ms result=成功",
                    auditLog.action(), auditLog.sensitive(), System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            log.warn("[AUDIT] action={} sensitive={} result=失败 reason={}",
                    auditLog.action(), auditLog.sensitive(), e.getMessage());
            throw e;
        }
    }
}
