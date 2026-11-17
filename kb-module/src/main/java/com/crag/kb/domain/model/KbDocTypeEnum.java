package com.crag.kb.domain.model;

/**
 * 知识库文档类型（规范 4.2：制度或判例）。
 */
public enum KbDocTypeEnum {

    /** 制度（内部规章、管理办法）。 */
    INSTITUTION("制度"),
    /** 判例（案例摘要）。 */
    CASE("判例");

    private final String label;

    KbDocTypeEnum(String label) {
        this.label = label;
    }

    /**
     * 中文展示名。
     *
     * @return 展示名
     */
    public String label() {
        return label;
    }
}
