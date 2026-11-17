package com.crag.doc.domain.model;

import com.crag.common.text.ClauseTypeEnum;
import com.crag.common.text.ParsedClause;
import com.crag.common.text.TypeSourceEnum;

/**
 * 合同切片记录（表 contract_chunk，归属 doc-module，规范 4.2）。
 *
 * <p>合同切片只作为查询输入，不存 embedding、不参与向量召回；tsv 列由数据库触发器维护。</p>
 *
 * @param id            切片标识
 * @param contractId    合同标识
 * @param tenantId      租户
 * @param clauseNo      条款序号
 * @param clauseType    条款类型
 * @param typeSource    类型来源
 * @param chunkText     切片文本
 * @param fullTextIndex 全文递增索引
 * @param charStart     原文起始偏移
 * @param charEnd       原文结束偏移
 */
public record ContractChunkRecord(long id,
                                  long contractId,
                                  long tenantId,
                                  String clauseNo,
                                  ClauseTypeEnum clauseType,
                                  TypeSourceEnum typeSource,
                                  String chunkText,
                                  int fullTextIndex,
                                  int charStart,
                                  int charEnd) {

    /**
     * 由切分结果构造记录。
     *
     * @param id         切片标识
     * @param contractId 合同标识
     * @param tenantId   租户
     * @param clause     切分结果
     * @return 切片记录
     */
    public static ContractChunkRecord from(long id, long contractId, long tenantId, ParsedClause clause) {
        return new ContractChunkRecord(id, contractId, tenantId, clause.clauseNo(), clause.clauseType(),
                clause.typeSource(), clause.text(), clause.fullTextIndex(), clause.charStart(), clause.charEnd());
    }
}
