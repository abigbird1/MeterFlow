package com.meterflow.equity.controller;

import com.meterflow.common.api.Results;
import com.meterflow.common.api.Results.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 骨架探活：验证服务可运行基线 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Results.ok(Map.of("service", "meterflow-equity", "status", "UP"));
    }
}
