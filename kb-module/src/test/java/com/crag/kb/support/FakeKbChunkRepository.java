package com.crag.kb.support;

import com.crag.kb.application.port.KbChunkRepository;
import com.crag.kb.domain.model.KbChunkRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库切片仓储内存实现，仅用于单元测试。
 */
public class FakeKbChunkRepository implements KbChunkRepository {

    private final Map<String, KbChunkRecord> storage = new HashMap<>();
    private int written;

    @Override
    public List<KbChunkRecord> listByDocId(long kbDocId) {
        return storage.values().stream().filter(chunk -> chunk.kbDocId() == kbDocId).toList();
    }

    @Override
    public Map<String, String> contentHashes(long kbDocId) {
        Map<String, String> hashes = new HashMap<>();
        for (KbChunkRecord chunk : storage.values()) {
            if (chunk.kbDocId() == kbDocId) {
                hashes.put(chunk.chunkKey(), chunk.contentHash());
            }
        }
        return hashes;
    }

    @Override
    public int upsertChanged(List<KbChunkRecord> chunks) {
        for (KbChunkRecord chunk : chunks) {
            storage.put(chunk.chunkKey(), chunk);
            written++;
        }
        return chunks.size();
    }

    @Override
    public Map<String, String> contentHashesByDocKey(long tenantId, String docKey) {
        Map<String, String> hashes = new HashMap<>();
        String prefix = docKey + "#";
        for (KbChunkRecord chunk : storage.values()) {
            if (chunk.chunkKey().startsWith(prefix)) {
                hashes.put(chunk.chunkKey(), chunk.contentHash());
            }
        }
        return hashes;
    }

    @Override
    public int rebindDocId(long tenantId, long kbDocId, List<String> chunkKeys) {
        int rebound = 0;
        for (String key : chunkKeys) {
            KbChunkRecord chunk = storage.get(key);
            if (chunk != null && chunk.kbDocId() != kbDocId) {
                storage.put(key, chunk.withKbDocId(kbDocId));
                rebound++;
            }
        }
        return rebound;
    }

    @Override
    public int deleteByKeys(List<String> chunkKeys) {
        int deleted = 0;
        for (String key : chunkKeys) {
            if (storage.remove(key) != null) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public int countByDocId(long kbDocId) {
        return (int) storage.values().stream().filter(chunk -> chunk.kbDocId() == kbDocId).count();
    }

    /**
     * 测试辅助：累计写入次数。
     *
     * @return 写入次数
     */
    public int written() {
        return written;
    }

    /**
     * 测试辅助：按文档列出切片。
     *
     * @param kbDocId 文档标识
     * @return 切片
     */
    public List<KbChunkRecord> chunksOf(long kbDocId) {
        return new ArrayList<>(listByDocId(kbDocId));
    }
}
