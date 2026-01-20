package com.wfh.drawio.model.vo;

import com.wfh.drawio.model.entity.SysAuthority;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @Author FengHuan Wang
 * @Date 2026/1/20 14:55
 * @Version 1.0
 */
@Data
public class RoleWithAuthoritiesVO implements Serializable {

    private Long id;
    private String roleName;
    private String description;
    private Date createTime;
    private Date updateTime;
    private List<SysAuthority> authorities = new ArrayList<>();
}
