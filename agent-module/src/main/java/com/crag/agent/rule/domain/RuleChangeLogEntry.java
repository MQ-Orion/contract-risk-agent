package com.crag.agent.rule.domain;

import java.time.Instant;

/**
 * 规则变更日志条目（表 rule_change_log，规范 8.3）。
 *
 * <p>记录变更前后值与操作人：规范要求“每次变更版本号加一并记录变更前后值与操作人”，
 * 否则无法回答“这条规则上一次是谁、把阈值从多少改到多少”。</p>
 *
 * @param id            日志标识
 * @param tenantId      租户
 * @param ruleId        规则标识
 * @param ruleCode      规则编码
 * @param action        动作
 * @param versionBefore 变更前版本，创建时为 null
 * @param versionAfter  变更后版本
 * @param beforeJson    变更前快照，创建时为 null
 * @param afterJson     变更后快照
 * @param operatorId    操作人标识
 * @param operatorName  操作人名称
 * @param createdAt     发生时间
 */
public record RuleChangeLogEntry(long id,
                                 long tenantId,
                                 long ruleId,
                                 String ruleCode,
                                 RuleChangeActionEnum action,
                                 Integer versionBefore,
                                 Integer versionAfter,
                                 String beforeJson,
                                 String afterJson,
                                 Long operatorId,
                                 String operatorName,
                                 Instant createdAt) {
}
