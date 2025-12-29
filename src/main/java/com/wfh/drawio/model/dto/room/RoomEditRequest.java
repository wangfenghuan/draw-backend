package com.wfh.drawio.model.dto.room;

import lombok.Data;

import java.io.Serializable;

/**
 * @Title: RoomEditRequest
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.room
 * @Date 2025/12/29 09:47
 * @description:
 */
@Data
public class RoomEditRequest implements Serializable {

    /**
     * 房间id
     */
    private Long id;

    /**
     * 房间名称
     */
    private String roomName;

    /**
     * 0代表公开，1代表不公开
     */
    private Integer isPublic;

    /**
     * 是否关闭
     */
    private Integer isOpen;

    /**
     * 访问密码
     */
    private String accessKey;
}
