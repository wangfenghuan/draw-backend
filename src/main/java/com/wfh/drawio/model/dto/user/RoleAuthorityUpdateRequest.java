package com.wfh.drawio.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 修改角色权限请求
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Data
@Schema(name = "RoleAuthorityUpdateRequest", description = "修改角色权限请求")
public class RoleAuthorityUpdateRequest implements Serializable {

    /**
     * 角色ID
     */
    @Schema(description = "角色ID", example = "1", required = true)
    private Long roleId;

    /**
     * 权限ID列表
     */
    @Schema(description = "权限ID列表", example = "[1, 2, 3, 4]", required = true)
    private List<Long> authorityIds;

    private static final long serialVersionUID = 1L;
}
