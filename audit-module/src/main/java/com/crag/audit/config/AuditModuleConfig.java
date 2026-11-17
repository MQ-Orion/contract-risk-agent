package com.crag.audit.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 审核模块装配：Mapper 扫描。
 *
 * <p>租户拦截器由 common-module 统一装配，本模块不再单独配置（规范 4.4）。</p>
 */
@Configuration
@MapperScan("com.crag.audit.infrastructure.persistence.mapper")
public class AuditModuleConfig {
}
