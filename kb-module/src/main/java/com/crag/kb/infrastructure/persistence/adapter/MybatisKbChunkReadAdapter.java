package com.crag.kb.infrastructure.persistence.adapter;

import com.crag.common.text.ClauseTypeEnum;
import com.crag.kb.application.port.KbChunkReadPort;
import com.crag.kb.domain.model.RetrievedChunk;
import com.crag.kb.infrastructure.persistence.mapper.KbChunkMapper;
import com.crag.kb.infrastructure.persistence.row.KbChunkSearchRow;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 双路召回端口实现（规范 6.2）：SQL 只在本类背后的 Mapper 中出现。
 */
@Repository
public class MybatisKbChunkReadAdapter implements KbChunkReadPort {

    private final KbChunkMapper mapper;

    /**
     * 构造适配器。
     *
     * @param mapper 切片 Mapper
     */
    public MybatisKbChunkReadAdapter(KbChunkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RetrievedChunk> vectorSearch(String queryVector, String clauseType, long tenantId, int limit) {
        return toChunks(mapper.vectorSearch(queryVector, clauseType, tenantId, limit));
    }

    @Override
    public List<RetrievedChunk> keywordSearch(String query, String clauseType, long tenantId, int limit) {
        return toChunks(mapper.keywordSearch(query, clauseType, tenantId, limit));
    }

    private static List<RetrievedChunk> toChunks(List<KbChunkSearchRow> rows) {
        List<RetrievedChunk> chunks = new ArrayList<>(rows.size());
        for (KbChunkSearchRow row : rows) {
            chunks.add(new RetrievedChunk(
                    row.getId(),
                    row.getChunkKey(),
                    row.getChunkText(),
                    row.getSourceDocId(),
                    row.getSourceDocTitle(),
                    ClauseTypeEnum.fromCode(row.getChunkType()),
                    row.getScore() == null ? 0D : row.getScore()));
        }
        return List.copyOf(chunks);
    }
}
