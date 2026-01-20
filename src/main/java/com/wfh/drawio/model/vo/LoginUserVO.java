package com.wfh.drawio.model.vo;

import com.wfh.drawio.model.entity.SysAuthority;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 已登录用户视图（脱敏）
 *
 * @author wangfenghuan
 * @from wangfenghuan
 **/
@Data
@Schema(name = "LoginUserVO", description = "已登录用户视图")
public class LoginUserVO implements Serializable {

    /**
     * 用户 id
     */
    @Schema(description = "用户ID", example = "10001")
    private Long id;

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称", example = "张三")
    private String userName;

    /**
     * 用户头像
     */
    @Schema(description = "用户头像", example = "https://example.com/avatar.jpg")
    private String userAvatar;

    /**
     * 用户简介
     */
    @Schema(description = "用户简介", example = "这是一个用户简介")
    private String userProfile;

    /**
     * 用户角色：user/admin/ban
     */
    @Schema(description = "用户角色", example = "user")
    private String userRole;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private Date createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2024-01-01 10:00:00")
    private Date updateTime;

    /**
     * 用户权限
     */
    private List<SysAuthority> authorities = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}