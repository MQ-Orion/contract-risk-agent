package com.crag.agent.config;

import com.crag.agent.clause.ModelClauseTypeFallback;
import com.crag.agent.extract.ContractExtractionService;
import com.crag.agent.extract.ExtractionValidator;
import com.crag.agent.extract.ExtractionVerifier;
import com.crag.agent.model.ChatModelProperties;
import com.crag.agent.model.ModelCallLogRepository;
import com.crag.agent.model.OpenAiCompatibleChatModelClient;
import com.crag.agent.prompt.PromptRegistry;
import com.crag.agent.report.DocxReportRenderer;
import com.crag.agent.report.ReportFileStorage;
import com.crag.agent.review.ClauseRiskService;
import com.crag.agent.review.ContractReviewOrchestrator;
import com.crag.agent.rule.application.RuleCache;
import com.crag.agent.rule.application.RuleEngine;
import com.crag.agent.rule.application.RuleExpressionEvaluator;
import com.crag.agent.rule.config.RuleProperties;
import com.crag.agent.rule.infrastructure.expression.AviatorRuleExpressionEvaluator;
import com.crag.agent.tool.ToolRegistry;
import com.crag.agent.tool.impl.DefaultReportExportTool;
import com.crag.agent.tool.impl.DefaultRiskRetrievalTool;
import com.crag.agent.tool.impl.DefaultRuleCheckTool;
import com.crag.doc.application.port.ContractChunkRepository;
import com.crag.doc.application.port.ContractRepository;
import com.crag.retrieval.application.RetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模块装配：规则引擎、Prompt 注册中心、抽取校验器与模型客户端。
 *
 * <p>所有实现类通过 Bean 注入，编排代码不得 new 具体实现（规范 7.1 对工具的要求同理适用于规则与模型调用）。</p>
 */
@Configuration
@EnableConfigurationProperties({RuleProperties.class, ChatModelProperties.class})
@MapperScan({"com.crag.agent.rule.infrastructure.persistence.mapper", "com.crag.agent.model.persistence"})
public class AgentModuleConfig {

    /**
     * 规则缓存：单实例一份，整体替换保证求值期间规则集一致。
     *
     * @return 缓存
     */
    @Bean
    public RuleCache ruleCache() {
        return new RuleCache();
    }

    /**
     * 表达式求值器（规范 8.2）。
     *
     * @param properties 规则配置
     * @return 求值器
     */
    @Bean
    public RuleExpressionEvaluator ruleExpressionEvaluator(RuleProperties properties) {
        return new AviatorRuleExpressionEvaluator(properties.getEvalTimeoutMillis());
    }

    /**
     * 规则引擎。
     *
     * @param cache     规则缓存
     * @param evaluator 表达式求值器
     * @return 引擎
     */
    @Bean
    public RuleEngine ruleEngine(RuleCache cache, RuleExpressionEvaluator evaluator) {
        return new RuleEngine(cache, evaluator);
    }

    /**
     * Prompt 注册中心（规范 7.3）：启动时加载并校验四项固定内容，缺项直接启动失败。
     *
     * @return 注册中心
     */
    @Bean
    public PromptRegistry promptRegistry() {
        return new PromptRegistry("classpath:prompts/*.md");
    }

    /**
     * 抽取输出校验器（规范 7.4）。
     *
     * @param objectMapper JSON 解析
     * @return 校验器
     */
    @Bean
    public ExtractionValidator extractionValidator(ObjectMapper objectMapper) {
        return new ExtractionValidator(objectMapper);
    }

    /**
     * 抽取原文回验器（规范 7.5、12.3）。
     *
     * @param objectMapper JSON 序列化
     * @return 回验器
     */
    @Bean
    public ExtractionVerifier extractionVerifier(ObjectMapper objectMapper) {
        return new ExtractionVerifier(objectMapper);
    }

    /**
     * 审核执行线程池（规范 3.9：按用途分离，拒绝策略为调用者执行）。
     *
     * @return 线程池
     */
    @Bean("auditRunExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor auditRunExecutor() {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("crag-audit-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 推送线程池（每个连接一个循环，与审核执行隔离，避免长连接占用审核线程）。
     *
     * @return 线程池
     */
    @Bean("sseExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor sseExecutor() {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("crag-sse-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 模型客户端（规范 7.6）：自带超时、重试、并发上限与熔断。
     *
     * @param properties    模型配置
     * @param objectMapper  JSON 解析
     * @param logRepository 调用日志仓储
     * @param clock         统一时钟
     * @return 客户端
     */
    @Bean(destroyMethod = "close")
    public OpenAiCompatibleChatModelClient chatModelClient(ChatModelProperties properties,
                                                           ObjectMapper objectMapper,
                                                           ModelCallLogRepository logRepository, Clock clock) {
        return new OpenAiCompatibleChatModelClient(properties, objectMapper, logRepository, clock);
    }

    /**
     * DOCX 渲染器（规范 9.3）。
     *
     * @return 渲染器
     */
    @Bean
    public DocxReportRenderer docxReportRenderer() {
        return new DocxReportRenderer();
    }

    /**
     * 报告落盘（规范 9.3：按合同覆盖写）。
     *
     * @param reportStorageRoot 报告根目录
     * @param clock             统一时钟
     * @return 存储
     */
    @Bean
    public ReportFileStorage reportFileStorage(
            @org.springframework.beans.factory.annotation.Value("${crag.report.storage-root:./data/reports}")
            String reportStorageRoot, Clock clock) {
        return new ReportFileStorage(java.nio.file.Path.of(reportStorageRoot), clock);
    }

    /**
     * 工具注册中心（规范 7.1）：三个工具在此统一注册，编排代码只经注册中心取用。
     *
     * @param ruleEngine     规则引擎
     * @param retrievalService 检索唯一出口
     * @param renderer       DOCX 渲染器
     * @param storage        报告存储
     * @return 注册中心
     */
    @Bean
    public ToolRegistry toolRegistry(RuleEngine ruleEngine, RetrievalService retrievalService,
                                     DocxReportRenderer renderer, ReportFileStorage storage) {
        return new ToolRegistry(new DefaultRuleCheckTool(ruleEngine),
                new DefaultRiskRetrievalTool(retrievalService),
                new DefaultReportExportTool(renderer, storage));
    }

    /**
     * 初审编排器（规范 7.2）。
     *
     * @param contractRepository 合同只读端口
     * @param chunkRepository    切片只读端口
     * @param extractionService  抽取服务
     * @param toolRegistry       工具注册中心
     * @param clauseRiskService  条款风险判断
     * @param ruleCache          规则缓存
     * @param modelProperties    模型配置
     * @param clock              统一时钟
     * @param maxModelClauses    单合同最多调用模型的条款数
     * @return 编排器
     */
    @Bean
    public ContractReviewOrchestrator contractReviewOrchestrator(
            ContractRepository contractRepository,
            ContractChunkRepository chunkRepository,
            ContractExtractionService extractionService,
            ToolRegistry toolRegistry,
            ClauseRiskService clauseRiskService,
            RuleCache ruleCache,
            ChatModelProperties modelProperties,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${crag.review.max-model-clauses:6}")
            int maxModelClauses, ObjectMapper objectMapper) {
        return new ContractReviewOrchestrator(contractRepository, chunkRepository, extractionService,
                toolRegistry, clauseRiskService, ruleCache, modelProperties, clock, maxModelClauses, objectMapper);
    }
}
