package com.meterflow.equity.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/** goods 只读详情（供 billing 回查，方案 B） */
@FeignClient(name = "meterflow-equity-goods", url = "${meterflow.equity-url:http://localhost:8083}")
public interface GoodsClient {
    @GetMapping("/feign/goods/{goodsId}")
    Map<String, Object> goods(@PathVariable("goodsId") Long goodsId);
}
