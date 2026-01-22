package com.wfh.drawio.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 修改用户角色及权限请求
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Data
@Schema(name = "UserRoleUpdateRequest", description = "修改用户角色及权限请求")
public class UserRoleUpdateRequest implements Serializable {

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", example = "10001", required = true)
    private Long userId;

    /**
     * 角色ID列表
     */
    @Schema(description = "角色ID列表", example = "[1, 2, 3]", required = true)
    private List<Long> roleIds;

    private static final long serialVersionUID = 1L;
}
