package com.wfh.drawio.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 协同编辑快照表
 * @author fenghuanwang
 * @TableName room_snapshots
 */
@TableName(value ="room_snapshots")
@Data
public class RoomSnapshots {
    /**
     * 
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 房间/文件ID
     */
    private String roomName;

    /**
     * 该快照包含了截止到哪个update_id的数据
     */
    private Long lastUpdateId;

    /**
     * 
     */
    private Date createTime;

    /**
     * 合并后的完整Yjs文档状态
     */
    private byte[] snapshotData;

    /**
     * 是否删除（0未删除，1删除）
     */
    @TableLogic
    private Integer isDelete;
}