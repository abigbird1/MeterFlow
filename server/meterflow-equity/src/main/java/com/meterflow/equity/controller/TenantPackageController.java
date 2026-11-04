package com.meterflow.equity.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.equity.entity.Goods;
import com.meterflow.equity.entity.GoodsOrder;
import com.meterflow.equity.feign.BillingCallbackClient;
import com.meterflow.equity.service.GoodsService;
import com.meterflow.equity.service.IdentityService;
import com.meterflow.equity.service.IdentityService.Me;
import com.meterflow.equity.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 租户端：资源包大厅 / 我的资源包 / 下单 / 支付 / 取消（05 §5.3） */
@RestController
@RequestMapping("/t")
@RequiredArgsConstructor
public class TenantPackageController {

    private final GoodsService goodsService;
    private final OrderService orderService;
    private final IdentityService identityService;
    private final BillingCallbackClient billingCallbackClient;

    public record OrderRequest(Long goodsId, Integer qty) {}
    public record PayRequest(String method) {}

    /** 抢购大厅（R31：库存/限购/已购/窗口；按钮态由前端计算） */
    @GetMapping("/packages")
    public Result<List<Goods>> hall(@RequestHeader("Authorization") String authorization) {
        if (identityService.requireTenant(authorization) == null) return unauthorized();
        return Results.ok(goodsService.list());
    }

    /** 我的资源包：实例台账归 billing（S4），经 Feign 只读聚合 */
    @GetMapping("/packages/mine")
    @SuppressWarnings("unchecked")
    public Result<List<Map<String, Object>>> mine(@RequestHeader("Authorization") String authorization) {
        Me me = identityService.requireTenant(authorization);
        if (me == null) return unauthorized();
        Map<String, Object> resp = billingCallbackClient.instances(me.tenantId());
        List<Map<String, Object>> list = (List<Map<String, Object>>) resp.get("data");
        return Results.ok(list == null ? List.of() : list);
    }

    /** 下单（锁库存，R32） */
    @PostMapping("/orders")
    public Result<OrderService.OrderVO> place(@RequestHeader("Authorization") String authorization,
                                              @RequestBody OrderRequest req) {
        Me me = identityService.requireTenant(authorization);
        if (me == null) return unauthorized();
        return Results.ok(orderService.place(me.tenantId(), req.goodsId(),
                req.qty() == null ? 1 : req.qty()));
    }

    /** 模拟支付（R43 防超付）→ T_ORDER_PAID → billing 发放实例 */
    @PostMapping("/orders/{orderNo}/pay")
    public Result<OrderService.OrderVO> pay(@RequestHeader("Authorization") String authorization,
                                            @PathVariable String orderNo,
                                            @RequestBody(required = false) PayRequest req) {
        Me me = identityService.requireTenant(authorization);
        if (me == null) return unauthorized();
        return Results.ok(orderService.pay(me.tenantId(), orderNo, req == null ? null : req.method()));
    }

    /** 取消（库存释放） */
    @PostMapping("/orders/{orderNo}/cancel")
    public Result<OrderService.OrderVO> cancel(@RequestHeader("Authorization") String authorization,
                                               @PathVariable String orderNo) {
        Me me = identityService.requireTenant(authorization);
        if (me == null) return unauthorized();
        return Results.ok(orderService.cancel(me.tenantId(), orderNo));
    }

    private <T> Result<T> unauthorized() {
        return Results.fail("GWY-0401", "登录已失效，请重新登录");
    }
}
