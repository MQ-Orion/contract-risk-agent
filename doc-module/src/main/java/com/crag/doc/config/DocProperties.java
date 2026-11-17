package com.crag.doc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档模块配置（规范 15.2：配置项必须有默认值、单位与说明）。
 */
@ConfigurationProperties(prefix = "crag.doc")
public class DocProperties {

    /**
     * 合同原文存储根目录。必须与网站静态资源目录分离（规范 5.1、11.2）。
     */
    private String storageRoot = "./data/contracts";

    /**
     * 解析锁实现：redis 为多实例正确实现，memory 仅限本地单实例调试（规范 5.5、3.9）。
     */
    private String lockMode = "redis";

    /**
     * 解析线程池核心线程数（规范 3.9）。
     */
    private int parsePoolCoreSize = 4;

    /**
     * 解析线程池最大线程数（规范 3.9）。
     */
    private int parsePoolMaxSize = 8;

    /**
     * 解析线程池队列容量；队列满时由提交线程执行，禁止静默丢弃（规范 3.9）。
     */
    private int parsePoolQueueCapacity = 100;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getLockMode() {
        return lockMode;
    }

    public void setLockMode(String lockMode) {
        this.lockMode = lockMode;
    }

    public int getParsePoolCoreSize() {
        return parsePoolCoreSize;
    }

    public void setParsePoolCoreSize(int parsePoolCoreSize) {
        this.parsePoolCoreSize = parsePoolCoreSize;
    }

    public int getParsePoolMaxSize() {
        return parsePoolMaxSize;
    }

    public void setParsePoolMaxSize(int parsePoolMaxSize) {
        this.parsePoolMaxSize = parsePoolMaxSize;
    }

    public int getParsePoolQueueCapacity() {
        return parsePoolQueueCapacity;
    }

    public void setParsePoolQueueCapacity(int parsePoolQueueCapacity) {
        this.parsePoolQueueCapacity = parsePoolQueueCapacity;
    }
}
