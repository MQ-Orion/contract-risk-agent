package com.crag.kb.infrastructure.persistence.row;

/**
 * 召回查询行：承载双路召回的原始字段，由适配器转换为领域对象。
 */
public class KbChunkSearchRow {

    private Long id;
    private String chunkKey;
    private String chunkText;
    private String chunkType;
    private Long sourceDocId;
    private String sourceDocTitle;
    private Double score;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChunkKey() {
        return chunkKey;
    }

    public void setChunkKey(String chunkKey) {
        this.chunkKey = chunkKey;
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

    public Long getSourceDocId() {
        return sourceDocId;
    }

    public void setSourceDocId(Long sourceDocId) {
        this.sourceDocId = sourceDocId;
    }

    public String getSourceDocTitle() {
        return sourceDocTitle;
    }

    public void setSourceDocTitle(String sourceDocTitle) {
        this.sourceDocTitle = sourceDocTitle;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
