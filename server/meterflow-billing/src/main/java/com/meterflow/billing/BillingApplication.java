package com.meterflow.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 计费核心服务启动类（热路径内聚：受理→判重→计价→抵扣，P1/P7；ShardingSphere 分表接入在 04 落地阶段） */
@SpringBootApplication
@EnableScheduling
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
