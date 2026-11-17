package com.crag.kb.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 切片业务键与内容哈希（规范 4.2、4.3）。
 *
 * <p>chunk_key 由逻辑文档标识、条款序号与分片序号生成，跨版本稳定；content_hash 为规范化文本的 SHA-256，
 * 是“是否需要重新嵌入”的唯一判据。规范化只做空白折叠，避免把无意义的换行差异当成内容变化。</p>
 */
public final class KbChunkKeys {

    private KbChunkKeys() {
    }

    /**
     * 生成切片业务键。
     *
     * @param docKey     逻辑文档标识
     * @param clauseNo   条款序号
     * @param partIndex  分片序号，未二次切分为 0
     * @return 业务键
     */
    public static String chunkKey(String docKey, String clauseNo, int partIndex) {
        return docKey + "#" + clauseNo + "#" + partIndex;
    }

    /**
     * 计算规范化文本哈希。
     *
     * @param text 文本
     * @return 64 位十六进制 SHA-256
     */
    public static String contentHash(String text) {
        String normalized = normalize(text);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 规范化：去首尾空白、把连续空白折叠为单个空格。
     *
     * @param text 原文
     * @return 规范化文本
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replaceAll("[\\t\\u3000\\s]+", " ").strip();
    }

    /**
     * 生成逻辑文档标识：由标题派生的稳定键。
     *
     * @param title 标题
     * @return 小写十六进制摘要（16 位）
     */
    public static String docKeyOf(String title) {
        String base = normalize(title).toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(base.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
