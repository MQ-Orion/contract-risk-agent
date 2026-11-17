package com.crag.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量模型配置（规范 2.4、4.3、7.7）。
 *
 * <p>模型通过 OpenAI 兼容协议接入；密钥只能来自环境变量 {@code DASHSCOPE_API_KEY}；
 * 维度集中在本配置中，禁止散落到多个类。</p>
 */
@ConfigurationProperties(prefix = "crag.embedding")
public class EmbeddingProperties {

    /** OpenAI 兼容基础地址，例如 https://host/compatible-mode/v1。 */
    private String baseUrl = "https://cn-beijing.maas.qianwenaiapi.com/compatible-mode/v1";

    /** 模型名称，必须与维度一致。 */
    private String model = "qwen3.7-text-embedding-flash";

    /** 向量维度，与数据库列 vector(1024) 必须一致（规范 4.3）。 */
    private int dimension = 1024;

    /** 密钥，仅从环境变量注入，禁止写入配置仓库（规范 11.5）。 */
    private String apiKey = "";

    /** 单次请求最大文本条数。 */
    private int batchSize = 10;

    /** 连接超时（毫秒，规范 7.6：5 秒）。 */
    private int connectTimeoutMillis = 5000;

    /** 读取超时（毫秒，规范 7.6：60 秒）。 */
    private int readTimeoutMillis = 60000;

    /** 失败重试次数（规范 7.6：两次）。 */
    private int retryTimes = 2;

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

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }
}
