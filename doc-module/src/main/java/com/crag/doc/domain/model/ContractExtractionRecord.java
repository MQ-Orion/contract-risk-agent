package com.crag.doc.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 合同结构化抽取结果（表 contract_extract_info，规范 4.2、7.4）。
 *
 * <p>归属 doc-module：agent 负责调用模型并产出结果，通过 doc 的端口写入，禁止跨模块注入 Mapper（规范 2.2）。</p>
 *
 * @param id                 记录标识
 * @param tenantId           租户
 * @param contractId         合同标识
 * @param contractAmount     合同金额
 * @param paymentDays        账期天数
 * @param jurisdiction       管辖地
 * @param hasJointLiability  是否含连带责任
 * @param status             抽取状态
 * @param modelRawResponse   模型原始返回（截断），仅供回溯
 * @param sourceSpanJson     字段原文片段与回验结果 JSON，必填
 * @param promptVersion      Prompt 版本
 * @param modelName          模型名
 * @param createdAt          创建时间
 * @param updatedAt          更新时间
 */
public record ContractExtractionRecord(long id,
                                       long tenantId,
                                       long contractId,
                                       BigDecimal contractAmount,
                                       Integer paymentDays,
                                       String jurisdiction,
                                       Boolean hasJointLiability,
                                       ExtractStatusEnum status,
                                       String modelRawResponse,
                                       String sourceSpanJson,
                                       String promptVersion,
                                       String modelName,
                                       Instant createdAt,
                                       Instant updatedAt) {
}
