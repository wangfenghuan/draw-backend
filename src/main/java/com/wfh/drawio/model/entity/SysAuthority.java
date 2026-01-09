package com.wfh.drawio.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

/**
 * 系统权限资源表
 * @author fenghuanwang
 * @TableName sys_authority
 */
@TableName(value ="sys_authority")
@Data
public class SysAuthority implements GrantedAuthority, Serializable {
    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 父权限ID(0为顶级)
     */
    private Long parentId;

    /**
     * 权限/菜单名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 后端接口权限标识(如 sys:user:list)
     */
    @TableField(value = "resource")
    private String authority;

    /**
     * 类型(0:菜单目录 1:具体接口/按钮)
     */
    private Integer type;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除(0:未删 1:已删)
     */
    @TableLogic
    private Integer isDelete;

    @Override
    public String getAuthority() {
        return this.authority;
    }
}