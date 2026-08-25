package com.xn.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用程序主启动类。
 * <p>
 * 提供组件的 Spring 上下文自动配置入口。
 * </p>
 */
@SpringBootApplication
public class XnReportApplication {

    /**
     * 应用程序主方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(XnReportApplication.class, args);
    }
}
