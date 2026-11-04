package com.meterflow.equity.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.equity.entity.GoodsOrder;
import com.meterflow.equity.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端：订单监控（05 §6.2.8） */
@RestController
@RequestMapping("/a/orders")
@RequiredArgsConstructor
public class OrdersAdminController {

    private final OrderService orderService;

    @GetMapping
    public Result<List<GoodsOrder>> list(@RequestParam(required = false) String status) {
        return Results.ok(orderService.page(null, status));
    }
}
