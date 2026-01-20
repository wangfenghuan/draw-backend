package com.wfh.drawio.model.vo;

import com.wfh.drawio.model.entity.SysAuthority;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * @Author FengHuan Wang
 * @Date 2026/1/20 11:22
 * @Version 1.0
 */
@Data
public class RoleAuthorityFlatVO implements Serializable {

    // 角色字段
    private Long roleId;
    private String roleName;
    private String roleDescription;
    private Date roleCreateTime;
    private Date roleUpdateTime;

    // 权限字段（可能为 null）
    private Long authorityId;
    private Long parentId;
    private String authorityName;
    private String authorityDescription;
    private String authority;      // 权限标识
    private Integer type;
    private Date authorityCreateTime;
    private Date authorityUpdateTime;
}
