package com.meterflow.equity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 权益服务启动类（商品/订单/抢购；Redis Lua 原子预扣在抢购落地阶段接入） */
@SpringBootApplication
public class EquityApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquityApplication.class, args);
    }
}
