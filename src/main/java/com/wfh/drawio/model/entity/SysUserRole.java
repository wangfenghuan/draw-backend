package com.wfh.drawio.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户和角色关联表
 * @TableName sys_user_role
 */
@TableName(value ="sys_user_role")
@Data
public class SysUserRole {
    /**
     * 用户ID
     */
    @TableId
    private Long id;

    /**
     * 角色ID
     */
    @TableId
    private Long roleId;

    /**
     * 是否删除
     */
    private Integer isDelete;
}