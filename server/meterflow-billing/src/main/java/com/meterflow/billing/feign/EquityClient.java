package com.meterflow.billing.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/** equity 商品只读详情（S4 方案 B：事件只传 ID，展示字段回查） */
@FeignClient(name = "meterflow-equity-goods", url = "${meterflow.equity-url:http://localhost:8083}")
public interface EquityClient {
    @GetMapping("/feign/goods/{goodsId}")
    Map<String, Object> goods(@PathVariable("goodsId") Long goodsId);
}
