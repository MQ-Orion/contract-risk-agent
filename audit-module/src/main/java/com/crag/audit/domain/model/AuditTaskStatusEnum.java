package com.crag.audit.domain.model;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 审核任务状态机（规范 9.1）。
 *
 * <p>状态序列：已创建 → 解析中 → 审核中 → 待复核 → 已完成；异常分支为失败，人工中止为已取消。
 * 只允许白名单边，禁止跳跃与随意回退。</p>
 *
 * <p>唯一允许的回退：待复核 → 审核中（复核不通过时重新审核）。其余边一律拒绝——回退会让“已完成”的结论
 * 在无人察觉的情况下被改写，审计上无法解释。</p>
 */
public enum AuditTaskStatusEnum {

    /** 已创建：任务已登记，尚未开始解析。 */
    CREATED,
    /** 解析中：等待 doc 模块完成解析与切分。 */
    PARSING,
    /** 审核中：规则、检索与模型判断执行中。 */
    AUDITING,
    /** 待复核：风险清单已生成，等待人工复核。 */
    PENDING_REVIEW,
    /** 已完成：复核结束，结论已出具。 */
    COMPLETED,
    /** 失败：不可继续，需人工介入。 */
    FAILED,
    /** 已取消：人工中止。 */
    CANCELLED;

    private static final Map<AuditTaskStatusEnum, Set<AuditTaskStatusEnum>> EDGES;

    static {
        Map<AuditTaskStatusEnum, Set<AuditTaskStatusEnum>> edges = new EnumMap<>(AuditTaskStatusEnum.class);
        edges.put(CREATED, EnumSet.of(PARSING, AUDITING, CANCELLED, FAILED));
        // 合同已解析完成时允许 CREATED → AUDITING 直达；解析中的合同必须走 PARSING
        edges.put(PARSING, EnumSet.of(AUDITING, FAILED, CANCELLED));
        edges.put(AUDITING, EnumSet.of(PENDING_REVIEW, FAILED, CANCELLED));
        // 复核不通过返回审核中是唯一允许的回退（规范 9.1）
        edges.put(PENDING_REVIEW, EnumSet.of(COMPLETED, AUDITING, CANCELLED));
        edges.put(COMPLETED, EnumSet.noneOf(AuditTaskStatusEnum.class));
        edges.put(FAILED, EnumSet.noneOf(AuditTaskStatusEnum.class));
        edges.put(CANCELLED, EnumSet.noneOf(AuditTaskStatusEnum.class));
        EDGES = Collections.unmodifiableMap(edges);
    }

    /**
     * 是否允许流转到目标状态。
     *
     * @param target 目标状态
     * @return 允许返回 true
     */
    public boolean canTransitionTo(AuditTaskStatusEnum target) {
        return EDGES.get(this).contains(target);
    }

    /**
     * 校验流转，非法时抛 40001。
     *
     * @param target 目标状态
     * @throws BizException 状态非法
     */
    public void checkTransition(AuditTaskStatusEnum target) {
        if (!canTransitionTo(target)) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL, "audit status " + this + " -> " + target);
        }
    }

    /**
     * 是否为终态。
     *
     * @return COMPLETED / FAILED / CANCELLED 返回 true
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * 是否允许复核（只有待复核状态可复核）。
     *
     * @return 允许返回 true
     */
    public boolean reviewable() {
        return this == PENDING_REVIEW;
    }

    /**
     * 由存库 code 还原。
     *
     * @param code 枚举名
     * @return 枚举；未知返回 null
     */
    public static AuditTaskStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AuditTaskStatusEnum value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
