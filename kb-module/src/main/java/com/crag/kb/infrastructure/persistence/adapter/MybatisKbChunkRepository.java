package com.crag.kb.infrastructure.persistence.adapter;

import com.crag.common.text.ClauseTypeEnum;
import com.crag.kb.application.port.KbChunkRepository;
import com.crag.kb.domain.model.KbChunkRecord;
import com.crag.kb.infrastructure.persistence.mapper.KbChunkMapper;
import com.crag.kb.infrastructure.persistence.row.KbChunkSearchRow;
import com.crag.kb.infrastructure.persistence.row.KbChunkUpsertRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 知识库切片仓储实现。
 */
@Repository
public class MybatisKbChunkRepository implements KbChunkRepository {

    /** 单批写入行数上限（规范 4.6）。 */
    private static final int BATCH_SIZE = 500;

    private final KbChunkMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 切片 Mapper
     */
    public MybatisKbChunkRepository(KbChunkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<KbChunkRecord> listByDocId(long kbDocId) {
        List<KbChunkSearchRow> rows = mapper.selectByDoc(kbDocId);
        List<KbChunkRecord> records = new ArrayList<>(rows.size());
        for (KbChunkSearchRow row : rows) {
            records.add(new KbChunkRecord(row.getId(), 0L, kbDocId, row.getChunkKey(), "", row.getChunkText(),
                    ClauseTypeEnum.fromCode(row.getChunkType()), "", null));
        }
        return List.copyOf(records);
    }

    @Override
    public Map<String, String> contentHashes(long kbDocId) {
        Map<String, String> hashes = new HashMap<>();
        for (Map<String, Object> row : mapper.selectHashes(kbDocId)) {
            putHash(hashes, row);
        }
        return hashes;
    }

    /**
     * 归一行映射：MyBatis 在不同配置下可能返回下划线或驼峰键名，两种都要兼容。
     *
     * @param target 目标映射
     * @param row    原始行
     */
    private static void putHash(Map<String, String> target, Map<String, Object> row) {
        Object key = row.get("chunk_key");
        if (key == null) {
            key = row.get("chunkKey");
        }
        Object hash = row.get("content_hash");
        if (hash == null) {
            hash = row.get("contentHash");
        }
        if (key != null) {
            target.put(String.valueOf(key), hash == null ? "" : String.valueOf(hash));
        }
    }

    @Override
    public Map<String, String> contentHashesByDocKey(long tenantId, String docKey) {
        Map<String, String> hashes = new HashMap<>();
        // 前缀必须带 %：chunk_key 形如 docKey#条款号#分片号，只传 docKey# 是等值比较，会永远查不到（已踩过）。
        for (Map<String, Object> row : mapper.selectHashesByDocKey(tenantId, docKey + "#%")) {
            putHash(hashes, row);
        }
        return hashes;
    }

    @Override
    public int upsertChanged(List<KbChunkRecord> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        List<KbChunkUpsertRow> rows = new ArrayList<>(chunks.size());
        for (KbChunkRecord chunk : chunks) {
            rows.add(toRow(chunk));
        }
        int written = 0;
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, rows.size());
            written += mapper.upsertChanged(rows.subList(from, to));
        }
        return written;
    }

    @Override
    public int rebindDocId(long tenantId, long kbDocId, List<String> chunkKeys) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return 0;
        }
        int rebound = 0;
        for (int from = 0; from < chunkKeys.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, chunkKeys.size());
            rebound += mapper.rebindDocId(tenantId, kbDocId, chunkKeys.subList(from, to));
        }
        return rebound;
    }

    @Override
    public int deleteByKeys(List<String> chunkKeys) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (int from = 0; from < chunkKeys.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, chunkKeys.size());
            deleted += mapper.deleteByKeys(chunkKeys.subList(from, to));
        }
        return deleted;
    }

    @Override
    public int countByDocId(long kbDocId) {
        return mapper.countByDoc(kbDocId);
    }

    private static KbChunkUpsertRow toRow(KbChunkRecord chunk) {
        KbChunkUpsertRow row = new KbChunkUpsertRow();
        row.setId(chunk.id());
        row.setTenantId(chunk.tenantId());
        row.setKbDocId(chunk.kbDocId());
        row.setChunkKey(chunk.chunkKey());
        row.setClauseNo(chunk.clauseNo());
        row.setChunkText(chunk.chunkText());
        row.setChunkType(chunk.chunkType().name());
        row.setContentHash(chunk.contentHash());
        row.setEmbeddingLiteral(toVectorLiteral(chunk.embedding()));
        return row;
    }

    /**
     * 向量转 PG 字面量。
     *
     * @param vector 向量
     * @return 形如 [0.1,0.2] 的字面量；空向量返回 null
     */
    static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder(vector.length * 12);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
