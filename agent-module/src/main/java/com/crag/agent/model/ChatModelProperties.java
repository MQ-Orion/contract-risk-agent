package com.crag.agent.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话模型配置（规范 7.6、7.7）。
 *
 * <p>超时、重试、并发、熔断阈值、令牌预算与采样参数全部配置化，并随调用记录入库；
 * 代码里不得出现魔法值（规范 3.10、7.7）。</p>
 */
@ConfigurationProperties(prefix = "crag.model")
public class ChatModelProperties {

    /** OpenAI 兼容端点 base url。 */
    private String baseUrl = "https://cn-beijing.maas.qianwenaiapi.com/compatible-mode/v1";

    /** 模型名，规范 7.7 要求可配置并入库。 */
    private String model = "qwen3.8-flash";

    /** 模型版本描述，留空时以模型名代替。 */
    private String modelVersion;

    /** 密钥，仅经环境变量注入（规范 11.5）。 */
    private String apiKey;

    /** 温度，规范 7.7 取 0.1。 */
    private double temperature = 0.1D;

    /** 采样阈值，规范 7.7 取 0.9。 */
    private double topP = 0.9D;

    /** 单次最大输出长度（token）。 */
    private int maxOutputTokens = 2000;

    /** 连接超时（毫秒），规范 7.6 要求 5 秒。 */
    private long connectTimeoutMillis = 5_000L;

    /** 读取超时（毫秒），规范 7.6 要求 60 秒。 */
    private long readTimeoutMillis = 60_000L;

    /** 失败重试次数，规范 7.6 要求 2 次（退避 1s、3s）。 */
    private int retryTimes = 2;

    /** 并发上限，规范 7.6 要求 8。 */
    private int concurrency = 8;

    /** 排队上限，队列满直接降级（规范 7.6）。 */
    private int queueCapacity = 32;

    /** 熔断窗口内失败阈值，规范 7.6 要求 1 分钟内 10 次。 */
    private int failureThreshold = 10;

    /** 熔断统计窗口（毫秒），规范 7.6 要求 1 分钟。 */
    private long failureWindowMillis = 60_000L;

    /** 熔断持续时间（毫秒），规范 7.6 要求 5 分钟。 */
    private long openMillis = 300_000L;

    /** 单合同令牌预算，规范 7.6 要求 6 万。 */
    private int tokenBudgetPerContract = 60_000;

    /** 单条款召回片段上限，规范 7.6 要求 6 段。 */
    private int maxContextChunks = 6;

    /** 单段文本字符上限，规范 7.6 要求 800 字。 */
    private int maxContextChars = 800;

    /** 条款类型兜底的最低置信度：低于该值视为未命中（规范 5.3）。 */
    private double clauseTypeMinConfidence = 0.6D;

    /** 条款类型兜底送入模型的最大字符数，避免长条款放大成本。 */
    private int clauseTypeMaxChars = 600;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(long readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public long getFailureWindowMillis() {
        return failureWindowMillis;
    }

    public void setFailureWindowMillis(long failureWindowMillis) {
        this.failureWindowMillis = failureWindowMillis;
    }

    public long getOpenMillis() {
        return openMillis;
    }

    public void setOpenMillis(long openMillis) {
        this.openMillis = openMillis;
    }

    public int getTokenBudgetPerContract() {
        return tokenBudgetPerContract;
    }

    public void setTokenBudgetPerContract(int tokenBudgetPerContract) {
        this.tokenBudgetPerContract = tokenBudgetPerContract;
    }

    public int getMaxContextChunks() {
        return maxContextChunks;
    }

    public void setMaxContextChunks(int maxContextChunks) {
        this.maxContextChunks = maxContextChunks;
    }

    public double getClauseTypeMinConfidence() {
        return clauseTypeMinConfidence;
    }

    public void setClauseTypeMinConfidence(double clauseTypeMinConfidence) {
        this.clauseTypeMinConfidence = clauseTypeMinConfidence;
    }

    public int getClauseTypeMaxChars() {
        return clauseTypeMaxChars;
    }

    public void setClauseTypeMaxChars(int clauseTypeMaxChars) {
        this.clauseTypeMaxChars = clauseTypeMaxChars;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    /**
     * 模型版本展示值：未配置时回落到模型名。
     *
     * @return 版本描述
     */
    public String effectiveModelVersion() {
        return modelVersion == null || modelVersion.isBlank() ? model : modelVersion;
    }
}
