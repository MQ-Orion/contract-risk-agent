package com.crag.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crag.doc.infrastructure.persistence.entity.ContractEntity;

/**
 * 合同主表 Mapper。仅 doc-module 可写（规范 2.2 单写者原则）。
 */
public interface ContractMapper extends BaseMapper<ContractEntity> {
}
