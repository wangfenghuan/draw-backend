package com.wfh.drawio.model.vo;

import com.wfh.drawio.model.entity.SysAuthority;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 用户视图（脱敏）
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Data
@Schema(name = "UserVO", description = "用户视图对象")
public class UserVO implements Serializable {

    /**
     * id
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
     * 用户账号/邮箱
     */
    @Schema(description = "用户账号/邮箱")
    private String userAccount;

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
     * 用户权限
     */
    private List<SysAuthority> authorities;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private Date createTime;

    private static final long serialVersionUID = 1L;
}