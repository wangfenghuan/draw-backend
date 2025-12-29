package com.wfh.drawio.model.dto.room;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.wfh.drawio.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * @Title: RoomQueryRequest
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.room
 * @Date 2025/12/29 09:46
 * @description:
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RoomQueryRequest extends PageRequest implements Serializable {

    /**
     * 房间id
     */
    private Long id;

    /**
     * 房间名称
     */
    private String roomName;

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 房间所关联的图表id
     */
    private Long diagramId;

    /**
     * 创建者id
     */
    private Long owerId;

    /**
     * 0代表公开，1代表不公开
     */
    private Integer isPublic;


    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否关闭
     */
    private Integer isOpen;

}
