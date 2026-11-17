package com.crag.agent.rule.application;

import com.crag.agent.rule.domain.RuleDefinition;
import com.crag.agent.rule.domain.RuleScopeEnum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则缓存（规范 8.1：规则加载走缓存，通过版本号与定时刷新生效）。
 *
 * <p>整体替换而不是逐条更新：求值过程中读到的必须是一致的规则集，逐条更新会出现“新规则已生效、
 * 旧规则还在”的混合状态。替换通过 {@link AtomicReference} 一次完成，读方无锁。</p>
 *
 * <p>版本口径：{@code version} 取所有规则 {@code updatedAt} 的最大 epoch 毫秒，
 * {@code fingerprint} 取规则关键字段的哈希。两者都相同才认为“未变化”，避免定时刷新产生无意义的缓存抖动。</p>
 *
 * <p>线程安全性：读无锁、写单点；刷新任务由 ShedLock 保证多实例不并发刷新。</p>
 */
public class RuleCache {

    private static final Logger log = LoggerFactory.getLogger(RuleCache.class);

    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.empty());

    /**
     * 当前快照。
     *
     * @return 快照，永不为 null
     */
    public Snapshot snapshot() {
        return current.get();
    }

    /**
     * 用最新规则集替换缓存。
     *
     * @param rules 全量规则（含未启用）
     * @return 发生变化返回 true
     */
    public boolean replace(List<RuleDefinition> rules) {
        Snapshot next = Snapshot.of(rules);
        Snapshot previous = current.get();
        if (previous.fingerprint() == next.fingerprint() && previous.version() == next.version()) {
            return false;
        }
        current.set(next);
        log.info("规则缓存已刷新 version={} fingerprint={} rules={} enabled={}",
                next.version(), next.fingerprint(), next.rules().size(), next.enabledCount());
        return true;
    }

    /**
     * 规则快照。
     *
     * @param version     版本（最大更新时间）
     * @param fingerprint 关键字段哈希
     * @param rules       全量规则，不可变
     */
    public record Snapshot(long version, long fingerprint, List<RuleDefinition> rules) {

        /**
         * 构造快照，规则列表做不可变复制。
         */
        public Snapshot {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }

        /**
         * 空快照。
         *
         * @return 空快照
         */
        public static Snapshot empty() {
            return new Snapshot(0L, 0L, List.of());
        }

        /**
         * 由规则集构造快照。
         *
         * @param rules 规则集
         * @return 快照
         */
        public static Snapshot of(List<RuleDefinition> rules) {
            List<RuleDefinition> safe = rules == null ? List.of() : rules;
            long version = 0L;
            long fingerprint = 17L;
            for (RuleDefinition rule : safe) {
                Instant updatedAt = rule.updatedAt();
                if (updatedAt != null) {
                    version = Math.max(version, updatedAt.toEpochMilli());
                }
                fingerprint = fingerprint * 31 + rule.ruleCode().hashCode();
                fingerprint = fingerprint * 31 + rule.versionNo();
                fingerprint = fingerprint * 31 + (rule.enabled() ? 1 : 0);
                fingerprint = fingerprint * 31 + rule.conditionExpr().hashCode();
                fingerprint = fingerprint * 31 + rule.riskLevel().name().hashCode();
            }
            return new Snapshot(version, fingerprint, safe);
        }

        /**
         * 取某租户、某作用域下适用于指定条款类型的启用规则。
         *
         * @param tenantId   租户
         * @param scope      作用域
         * @param clauseType 条款类型；合同级规则传 null
         * @return 规则列表
         */
        public List<RuleDefinition> enabledRules(long tenantId, RuleScopeEnum scope, String clauseType) {
            List<RuleDefinition> matched = new ArrayList<>();
            for (RuleDefinition rule : rules) {
                if (!rule.enabled() || rule.tenantId() != tenantId || rule.scope() != scope) {
                    continue;
                }
                if (scope == RuleScopeEnum.CLAUSE && !rule.appliesToClauseType(clauseType)) {
                    continue;
                }
                matched.add(rule);
            }
            return List.copyOf(matched);
        }

        /**
         * 启用规则数。
         *
         * @return 数量
         */
        public long enabledCount() {
            return rules.stream().filter(RuleDefinition::enabled).count();
        }

        /**
         * 全部启用规则，供回放用例使用（跨租户）。
         *
         * @return 规则列表
         */
        public List<RuleDefinition> allRules() {
            return rules;
        }
    }
}
