package com.crag.doc.domain.model;

/**
 * 抽取状态（表 contract_extract_info.extract_status，规范 7.4、12.3）。
 */
public enum ExtractStatusEnum {

    /** 全部字段通过原文回验。 */
    VERIFIED,
    /** 存在无原文片段或回验失败的字段，整体不对外直接采信，转人工确认。 */
    PENDING_MANUAL,
    /** 模型不可用或输出连续两次不合法，本次仅保留规则结论。 */
    DEGRADED;

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名
     * @return 枚举；未知返回 null
     */
    public static ExtractStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ExtractStatusEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
