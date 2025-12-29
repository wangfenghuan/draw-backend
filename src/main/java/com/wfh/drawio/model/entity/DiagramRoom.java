package com.wfh.drawio.model.entity;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

import com.wfh.drawio.model.vo.RoomVO;
import lombok.Data;
import lombok.val;

/**
 * 
 * @author fenghuanwang
 * @TableName diagram_room
 */
@TableName(value ="diagram_room")
@Data
public class DiagramRoom {
    /**
     * 房间id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 房间名称
     */
    private String roomName;

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
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

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

    /**
     * 访问密码
     */
    private String accessKey;


}