package com.wfh.drawio.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Data;

/**
 * 协同编辑快照表
 * @author fenghuanwang
 * @TableName room_snapshots
 */
@TableName(value ="room_snapshots")
@Data
@Schema(name = "RoomSnapshots", description = "协同编辑快照表")
public class RoomSnapshots {
    /**
     *
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID", example = "123456789")
    private Long id;

    /**
     * 房间/文件ID
     */
    @Schema(description = "房间/文件ID", example = "10001")
    private Long roomName;

    /**
     * 快照地址
     */
    @Schema(description = "快照地址")
    private String objectKey;

    /**
     * 该快照包含了截止到哪个update_id的数据
     */
    @Schema(description = "该快照包含了截止到哪个update_id的数据", example = "5000")
    private Long lastUpdateId;

    /**
     * 房间id
     */
    @Schema(description = "房间ID")
    private Long roomId;

    /**
     *
     */
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private Date createTime;

    /**
     * 合并后的完整Yjs文档状态
     */
    @Schema(description = "合并后的完整Yjs文档状态")
    private byte[] snapshotData;

    /**
     * 是否删除（0未删除，1删除）
     */
    @TableLogic
    @Schema(description = "是否删除（0未删除，1删除）", example = "0")
    private Integer isDelete;
}