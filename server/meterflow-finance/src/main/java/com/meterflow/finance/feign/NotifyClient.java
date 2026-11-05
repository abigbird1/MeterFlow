package com.meterflow.finance.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** platform 内部通知触达（A3/D6：站内通知经 /feign/notify 落库） */
@FeignClient(name = "meterflow-platform-notify", url = "${meterflow.platform-url:http://localhost:8081}")
public interface NotifyClient {

    @PostMapping("/feign/notify")
    Map<String, Object> notify(@RequestBody Map<String, Object> payload);
}
