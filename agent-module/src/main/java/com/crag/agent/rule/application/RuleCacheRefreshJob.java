package com.crag.agent.rule.application;

import com.crag.agent.rule.application.port.RuleRepository;
import com.crag.agent.rule.domain.RuleDefinition;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 规则缓存刷新任务（规范 8.1：版本号 + 定时刷新，间隔不超过 60 秒，无需重启）。
 *
 * <p>时序：应用启动完成 → 本类 {@code afterPropertiesSet} 先做一次同步加载（避免启动后 30 秒内规则集为空，
 * 那段时间的审核会“无规则可跑”）；之后每 {@code crag.rule.refresh-millis} 毫秒刷新一次。</p>
 *
 * <p>多实例：定时刷新加 ShedLock，锁名与用途一一对应；即使锁失效，{@link RuleCache#replace} 也是幂等替换，
 * 重复刷新只增加一次数据库读取，不会造成缓存抖动（规范 8.1 允许“ShedLock 或幂等刷新”，这里两者都做）。</p>
 */
@Component
public class RuleCacheRefreshJob implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RuleCacheRefreshJob.class);

    private final RuleRepository repository;
    private final RuleCache cache;

    /**
     * 构造任务。
     *
     * @param repository 规则仓储
     * @param cache      规则缓存
     */
    public RuleCacheRefreshJob(RuleRepository repository, RuleCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public void afterPropertiesSet() {
        // 启动加载不加锁：每个实例都必须拥有自己的缓存副本，加锁只会让后启动的实例继续空跑
        List<RuleDefinition> rules = repository.findAllForRefresh();
        cache.replace(rules);
        log.info("规则缓存启动加载完成 rules={}", rules.size());
    }

    /**
     * 定时刷新规则缓存。
     */
    @Scheduled(fixedDelayString = "${crag.rule.refresh-millis:30000}",
            initialDelayString = "${crag.rule.initial-delay-millis:3000}")
    @SchedulerLock(name = "ruleConfigRefresh", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void refresh() {
        boolean changed = cache.replace(repository.findAllForRefresh());
        if (changed) {
            log.info("规则缓存刷新生效 version={}", cache.snapshot().version());
        }
    }
}
