package com.meterflow.billing.controller;

import com.meterflow.billing.service.KeyCacheService;
import com.meterflow.billing.service.KeyCacheService.MeContext;
import com.meterflow.billing.service.OverviewService;
import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 用量与消费总览（05 §5.1.1）：部门级数据隔离——成员仅统计本部门 */
@RestController
@RequestMapping("/t/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final KeyCacheService keyCacheService;
    private final OverviewService overviewService;

    private record Me(Long tenantId, Long memberDeptId) {}

    private Me me(String authorization) {
        MeContext me = keyCacheService.resolveMe(authorization);
        if (me == null || !"t".equals(me.portal())) {
            throw new com.meterflow.common.exception.BusinessException(
                    new com.meterflow.common.exception.ErrorCode() {
                        @Override public String getCode() { return "GWY-0401"; }
                        @Override public String getMessage() { return "登录已失效，请重新登录"; }
                    });
        }
        // 部门隔离：成员（deptId 非空）仅本部门；管理员 deptId 为 NULL 看全租户
        return new Me(me.tenantId(), me.deptId());
    }

    @GetMapping
    public Result<Map<String, Object>> overview(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Me me = me(authorization);
        return Results.ok(overviewService.overview(me.tenantId(), me.memberDeptId()));
    }
}
