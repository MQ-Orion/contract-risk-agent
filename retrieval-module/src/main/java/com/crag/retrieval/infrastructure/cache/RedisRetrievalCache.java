package com.crag.retrieval.infrastructure.cache;

import com.crag.retrieval.application.port.RetrievalCachePort;
import com.crag.retrieval.config.RetrievalProperties;
import com.crag.retrieval.domain.model.RetrievalResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 检索缓存的 Redis 实现（规范 6.6）。
 *
 * <p>键规范：租户 + 条款类型 + 查询哈希 + 索引版本；值携带快照版本，索引版本变化后旧键不再命中。
 * 上/下架与版本切换通过递增租户索引版本实现“按租户批量失效”，避免扫描删除。</p>
 */
public class RedisRetrievalCache implements RetrievalCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisRetrievalCache.class);
    private static final String CACHE_PREFIX = "crag:retrieval:";
    private static final String INDEX_VERSION_PREFIX = "crag:kb:index-version:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RetrievalProperties properties;

    /**
     * 构造缓存。
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper  JSON 序列化器
     * @param properties    检索参数
     */
    public RedisRetrievalCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                               RetrievalProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public RetrievalResult get(long tenantId, String clauseType, String query, String indexVersion) {
        try {
            String value = redisTemplate.opsForValue().get(cacheKey(tenantId, clauseType, query, indexVersion));
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, RetrievalResult.class);
        } catch (JsonProcessingException e) {
            log.warn("检索缓存反序列化失败，按未命中处理 tenantId={}", tenantId);
            return null;
        }
    }

    @Override
    public void put(long tenantId, String clauseType, String query, String indexVersion, RetrievalResult result) {
        try {
            String value = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey(tenantId, clauseType, query, indexVersion), value,
                    Duration.ofMinutes(properties.getCacheTtlMinutes()));
        } catch (JsonProcessingException e) {
            log.warn("检索缓存写入失败 tenantId={}", tenantId);
        }
    }

    @Override
    public String indexVersion(long tenantId) {
        String key = INDEX_VERSION_PREFIX + tenantId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            redisTemplate.opsForValue().setIfAbsent(key, "0");
            value = redisTemplate.opsForValue().get(key);
        }
        return value == null ? "0" : value;
    }

    @Override
    public void invalidateTenant(long tenantId) {
        String key = INDEX_VERSION_PREFIX + tenantId;
        Long version = redisTemplate.opsForValue().increment(key);
        log.info("检索缓存按租户失效 tenantId={} indexVersion={}", tenantId, version);
    }

    private String cacheKey(long tenantId, String clauseType, String query, String indexVersion) {
        return CACHE_PREFIX + tenantId + ":" + (clauseType == null ? "-" : clauseType) + ":"
                + sha256(query) + ":" + indexVersion;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
