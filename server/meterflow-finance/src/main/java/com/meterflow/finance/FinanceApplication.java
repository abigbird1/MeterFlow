package com.meterflow.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 财务治理服务启动类（账单/对账/催缴/争议；XXL-Job 执行器在任务面阶段接入） */
@SpringBootApplication
@EnableScheduling
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
