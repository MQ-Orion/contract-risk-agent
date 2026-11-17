package com.crag.kb.domain.model;

import com.crag.common.api.ErrorCode;
import com.crag.common.domain.BizException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 知识库同步状态（规范 5.6：待向量化、向量化中、就绪、失败；必须落库）。
 */
public enum KbSyncStatusEnum {

    /** 待向量化。 */
    PENDING,
    /** 向量化中。 */
    EMBEDDING,
    /** 就绪。 */
    READY,
    /** 失败。 */
    FAILED;

    private static final Map<KbSyncStatusEnum, Set<KbSyncStatusEnum>> EDGES;

    static {
        Map<KbSyncStatusEnum, Set<KbSyncStatusEnum>> edges = new EnumMap<>(KbSyncStatusEnum.class);
        edges.put(PENDING, EnumSet.of(EMBEDDING, FAILED));
        edges.put(EMBEDDING, EnumSet.of(READY, FAILED));
        // READY → PENDING 是合法边：正文被修改后需要重新向量化，且必须回到 PENDING 才能再进 EMBEDDING。
        // READY → EMBEDDING 不允许，避免“直接重嵌入”绕过哈希比对，造成未变化切片重复计费（规范 4.3）。
        edges.put(READY, EnumSet.of(PENDING, FAILED));
        // FAILED → PENDING 是唯一回退边，由人工同步入口或对账任务触发；失败原因保留在日志与任务表。
        edges.put(FAILED, EnumSet.of(PENDING));
        EDGES = Collections.unmodifiableMap(edges);
    }

    /**
     * 是否允许流转。
     *
     * @param target 目标状态
     * @return 允许返回 true
     */
    public boolean canTransitionTo(KbSyncStatusEnum target) {
        return EDGES.get(this).contains(target);
    }

    /**
     * 校验流转，非法抛业务异常。
     *
     * @param target 目标状态
     */
    public void checkTransition(KbSyncStatusEnum target) {
        if (!canTransitionTo(target)) {
            throw BizException.of(ErrorCode.TASK_STATE_ILLEGAL, "kb sync " + this + " -> " + target);
        }
    }
}
