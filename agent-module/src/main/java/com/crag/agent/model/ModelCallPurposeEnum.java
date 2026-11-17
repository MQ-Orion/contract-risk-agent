package com.crag.agent.model;

/**
 * 模型调用用途（表 model_call_log.purpose）。
 */
public enum ModelCallPurposeEnum {

    /** 结构化工件字段抽取。 */
    EXTRACT,
    /** 条款风险判断。 */
    RISK_JUDGEMENT,
    /** 条款类型兜底分类。 */
    CLAUSE_TYPE
}
