package com.meterflow.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 平台支撑服务启动类（M1/M2/M11/M12/M13；扫描范围含 common 组件层：全局异常/审计切面） */
@SpringBootApplication(scanBasePackages = "com.meterflow")
@EnableFeignClients(basePackages = "com.meterflow.platform.feign")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
