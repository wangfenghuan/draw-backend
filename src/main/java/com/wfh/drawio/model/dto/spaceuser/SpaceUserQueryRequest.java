package com.wfh.drawio.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceUserQueryRequest implements Serializable {

    /**
     * ID
     */
    private Long id;

    /**
     * 空间 ID
     */
    private Long spaceId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 空间角色：space:viewer/space:editor/space:admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
