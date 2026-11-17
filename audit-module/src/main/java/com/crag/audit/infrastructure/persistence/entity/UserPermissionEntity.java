package com.crag.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户权限表实体（表 user_permission）。
 */
@TableName("user_permission")
public class UserPermissionEntity {

    @TableId(type = IdType.INPUT)
    public Long id;
    public Long tenantId;
    public Long userId;
    public String userName;
    public String role;
    public String docScope;
    public Integer deleted;
}
