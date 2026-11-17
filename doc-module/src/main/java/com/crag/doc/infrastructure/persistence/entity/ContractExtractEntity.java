package com.crag.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 合同抽取结果实体（表 contract_extract_info）。
 */
@TableName("contract_extract_info")
public class ContractExtractEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long contractId;
    private BigDecimal contractAmount;
    private Integer paymentDays;
    private String jurisdiction;
    private Integer hasJointLiability;
    private String extractStatus;
    private String modelRawResponse;
    private String sourceSpanJson;
    private String promptVersion;
    private String modelName;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private Integer deleted;

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

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public BigDecimal getContractAmount() {
        return contractAmount;
    }

    public void setContractAmount(BigDecimal contractAmount) {
        this.contractAmount = contractAmount;
    }

    public Integer getPaymentDays() {
        return paymentDays;
    }

    public void setPaymentDays(Integer paymentDays) {
        this.paymentDays = paymentDays;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public Integer getHasJointLiability() {
        return hasJointLiability;
    }

    public void setHasJointLiability(Integer hasJointLiability) {
        this.hasJointLiability = hasJointLiability;
    }

    public String getExtractStatus() {
        return extractStatus;
    }

    public void setExtractStatus(String extractStatus) {
        this.extractStatus = extractStatus;
    }

    public String getModelRawResponse() {
        return modelRawResponse;
    }

    public void setModelRawResponse(String modelRawResponse) {
        this.modelRawResponse = modelRawResponse;
    }

    public String getSourceSpanJson() {
        return sourceSpanJson;
    }

    public void setSourceSpanJson(String sourceSpanJson) {
        this.sourceSpanJson = sourceSpanJson;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
