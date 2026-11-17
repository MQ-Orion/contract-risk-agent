package com.crag.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.doc.infrastructure.persistence.entity.ContractChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 合同切片 Mapper。
 *
 * <p>批量写入使用单条参数化 INSERT，避免循环内逐条提交（规范 4.6：批量写入按五百条一批）。</p>
 */
public interface ContractChunkMapper extends BaseMapper<ContractChunkEntity> {

    /**
     * 批量插入切片。
     *
     * @param chunks 切片，调用方保证非空且不超过批量上限
     * @return 插入行数
     */
    @Insert("<script>"
            + "INSERT INTO contract_chunk "
            + "(id, tenant_id, contract_id, clause_no, chunk_type, type_source, chunk_text, "
            + " full_text_index, char_start, char_end) VALUES "
            + "<foreach collection='chunks' item='c' separator=','>"
            + "(#{c.id}, #{c.tenantId}, #{c.contractId}, #{c.clauseNo}, #{c.chunkType}, #{c.typeSource}, "
            + " #{c.chunkText}, #{c.fullTextIndex}, #{c.charStart}, #{c.charEnd})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("chunks") List<ContractChunkEntity> chunks);
}
