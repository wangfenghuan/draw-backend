package com.wfh.drawio.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 协同编辑增量表
 * @author fenghuanwang
 * @TableName room_updates
 */
@TableName(value ="room_updates")
@Data
public class RoomUpdates {
    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 房间/文件ID
     */
    private String roomName;

    /**
     * 产生时间
     */
    private Date createTime;

    /**
     * Yjs二进制增量数据(通常很小)
     */
    private byte[] updateData;

    /**
     * 是否删除（0未删除，1删除）
     */
    @TableLogic
    private Integer isDelete;
}