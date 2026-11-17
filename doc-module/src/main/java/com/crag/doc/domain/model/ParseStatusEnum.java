package com.crag.doc.domain.model;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 解析任务状态机（规范 5.5）：待处理、解析中、切分中、向量化中、就绪；异常进入失败态，人工可重试。
 *
 * <p>状态流转只允许白名单边，禁止跳跃（规范 5.5、9.1）。{@link #EMBEDDING} 用于知识库文档增量同步，
 * 合同切片不参与向量召回（规范 4.2），因此合同解析链路为 解析中 → 切分中 → 就绪。</p>
 */
public enum ParseStatusEnum {

    /** 待处理。 */
    PENDING,
    /** 解析中。 */
    PARSING,
    /** 切分中。 */
    SPLITTING,
    /** 向量化中（仅知识库文档同步使用）。 */
    EMBEDDING,
    /** 就绪。 */
    READY,
    /** 失败。 */
    FAILED;

    private static final Map<ParseStatusEnum, Set<ParseStatusEnum>> ALLOWED_EDGES;

    static {
        Map<ParseStatusEnum, Set<ParseStatusEnum>> edges = new EnumMap<>(ParseStatusEnum.class);
        edges.put(PENDING, EnumSet.of(PARSING, FAILED));
        edges.put(PARSING, EnumSet.of(SPLITTING, FAILED));
        // SPLITTING → EMBEDDING 只给知识库同步用；合同解析直接 SPLITTING → READY（规范 4.2：合同切片无向量）。
        edges.put(SPLITTING, EnumSet.of(EMBEDDING, READY, FAILED));
        edges.put(EMBEDDING, EnumSet.of(READY, FAILED));
        // READY 是终态：不允许回到 PARSING/EMBEDDING，否则重复触发解析会覆盖已就绪的切片与条款序号。
        // 需要重解析必须先由人工把任务置回 PENDING（规范 5.5 人工重试）。
        edges.put(READY, EnumSet.noneOf(ParseStatusEnum.class));
        // FAILED → PENDING 是唯一的回退边，且只能由重试入口触发；重试计数在服务层累加并受上限约束。
        edges.put(FAILED, EnumSet.of(PENDING));
        ALLOWED_EDGES = Collections.unmodifiableMap(edges);
    }

    /**
     * 是否允许流转到目标状态。
     *
     * @param target 目标状态
     * @return 允许返回 true
     */
    public boolean canTransitionTo(ParseStatusEnum target) {
        return ALLOWED_EDGES.get(this).contains(target);
    }

    /**
     * 校验流转，非法时抛业务异常。
     *
     * @param target 目标状态
     * @throws BizException 状态非法
     */
    public void checkTransition(ParseStatusEnum target) {
        if (!canTransitionTo(target)) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL, "parse status " + this + " -> " + target);
        }
    }

    /**
     * 是否为终态。
     *
     * @return READY 或 FAILED 返回 true
     */
    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }
}
