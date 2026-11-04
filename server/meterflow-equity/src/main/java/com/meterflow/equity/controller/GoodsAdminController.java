package com.meterflow.equity.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import com.meterflow.equity.dto.GoodsRequest;
import com.meterflow.equity.entity.Goods;
import com.meterflow.equity.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端：资源包商品（05 §6.2.4~6.2.7） */
@RestController
@RequestMapping("/a/goods")
@RequiredArgsConstructor
public class GoodsAdminController {

    private final GoodsService goodsService;


    @GetMapping
    public Result<List<Goods>> list() {
        return Results.ok(goodsService.list());
    }

    @PostMapping
    public Result<Goods> create(@RequestBody GoodsRequest req) {
        return Results.ok(goodsService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Goods> update(@PathVariable Long id, @RequestBody GoodsRequest req) {
        return Results.ok(goodsService.update(id, req));
    }

    @PostMapping("/{id}/status")
    public Result<Goods> status(@PathVariable Long id, @RequestBody StatusBody body) {
        return Results.ok(goodsService.changeStatus(id, body.status()));
    }

    public record StatusBody(String status) {}
}
