package com.meterflow.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Sentinel 网关限流兜底（03 §2.2 / meterflow.md #7.3）：
 *  - ingest 事件上报：500 TPS（设计峰值口径）
 *  - t-equity 抢购链路：1000 QPS（R31 三重限制前的网关兜底）
 *  - 触发限流统一返回 429 + GWY-0429（规范响应体，业务侧据此感知退避）
 */
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void init() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(route("ingest", 500));
        rules.add(route("t-equity", 1000));
        GatewayRuleManager.loadRules(rules);

        // 统一限流响应体（规范 #7.3：HTTP 429 + GWY-0429）
        GatewayCallbackManager.setBlockHandler((exchange, ex) -> ServerResponse
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of(
                        "code", "GWY-0429",
                        "message", "触发限流，请稍后重试",
                        "data", ""))));
    }

    /** 路由级 QPS 流控规则（resource=路由 id） */
    private GatewayFlowRule route(String routeId, int qps) {
        GatewayFlowRule r = new GatewayFlowRule(routeId);
        r.setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID);
        r.setGrade(RuleConstant.FLOW_GRADE_QPS);
        r.setCount(qps);
        r.setIntervalSec(1);
        return r;
    }
}
