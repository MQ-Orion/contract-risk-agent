package com.crag.common.config;

import com.crag.common.text.ClauseSplitter;
import com.crag.common.text.ClauseTypeClassifier;
import com.crag.common.text.SplitConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 条款切分共享内核装配。
 *
 * <p>doc-module 与 kb-module 共用同一份切分实现与参数（规范 5.4），因此 Bean 在 common 中定义，
 * 避免两个模块各自 new 一份导致切分粒度漂移。</p>
 */
@Configuration
@EnableConfigurationProperties(SplitProperties.class)
public class ClauseTextConfig {

    /**
     * 切分参数。
     *
     * @param properties 配置
     * @return 切分参数
     */
    @Bean
    public SplitConfig splitConfig(SplitProperties properties) {
        return new SplitConfig(properties.getMaxChars(), properties.getOverlapRatio());
    }

    /**
     * 条款类型判定器。
     *
     * @return 判定器
     */
    @Bean
    public ClauseTypeClassifier clauseTypeClassifier() {
        return new ClauseTypeClassifier();
    }

    /**
     * 条款切分器。
     *
     * @param splitConfig 切分参数
     * @param classifier  类型判定器
     * @return 切分器
     */
    @Bean
    public ClauseSplitter clauseSplitter(SplitConfig splitConfig, ClauseTypeClassifier classifier) {
        return new ClauseSplitter(splitConfig, classifier);
    }
}
