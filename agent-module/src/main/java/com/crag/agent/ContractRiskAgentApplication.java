package com.crag.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 单一 Spring Boot 应用启动类（规范 2.1：模块化单体，单进程部署）。
 *
 * <p>本类位于 agent-module —— 依赖图顶端（表 3 模块依赖白名单），因此只有它能看到全部业务模块。</p>
 */
@SpringBootApplication(scanBasePackages = "com.crag")
@EnableAsync
public class ContractRiskAgentApplication {

    /**
     * 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ContractRiskAgentApplication.class, args);
    }
}
