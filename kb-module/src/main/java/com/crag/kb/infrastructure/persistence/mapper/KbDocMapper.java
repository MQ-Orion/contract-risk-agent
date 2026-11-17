package com.crag.kb.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.kb.infrastructure.persistence.entity.KbDocEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper（仅 kb-module 可写，规范 2.2）。
 *
 * <p>自定义 SQL 在 {@code resources/mapper/KbDocMapper.xml}：Mapper XML 才会被 IDE 识别为 MyBatis 语句，
 * 注解形式会被当作裸 SQL 检查，导致“无法解析表 kb_doc/列 tenant_id”的误报。</p>
 */
@Mapper
public interface KbDocMapper extends BaseMapper<KbDocEntity> {

    /**
     * 查询有在架文档的租户列表，供每日对账任务遍历（任务本身跨租户，需关闭自动注入）。
     *
     * @return 租户标识
     */
    @InterceptorIgnore(tenantLine = "true")
    List<Long> selectActiveTenantIds();
}
