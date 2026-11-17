package com.crag.kb.infrastructure.persistence.row;

/**
 * 切片写入行：embedding 以 PG 向量字面量字符串传递，SQL 侧 CAST 为 vector。
 */
public class KbChunkUpsertRow {

    private Long id;
    private Long tenantId;
    private Long kbDocId;
    private String chunkKey;
    private String clauseNo;
    private String chunkText;
    private String chunkType;
    private String contentHash;
    private String embeddingLiteral;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getKbDocId() {
        return kbDocId;
    }

    public void setKbDocId(Long kbDocId) {
        this.kbDocId = kbDocId;
    }

    public String getChunkKey() {
        return chunkKey;
    }

    public void setChunkKey(String chunkKey) {
        this.chunkKey = chunkKey;
    }

    public String getClauseNo() {
        return clauseNo;
    }

    public void setClauseNo(String clauseNo) {
        this.clauseNo = clauseNo;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getEmbeddingLiteral() {
        return embeddingLiteral;
    }

    public void setEmbeddingLiteral(String embeddingLiteral) {
        this.embeddingLiteral = embeddingLiteral;
    }
}
