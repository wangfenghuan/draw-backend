package com.wfh.drawio.model.entity;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "DiagramRoom", description = "图表房间表")
public class DiagramRoom {
    /**
     * 房间id
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "房间ID", example = "123456789")
    private Long id;

    /**
     * 房间名称
     */
    @Schema(description = "房间名称", example = "协作编辑房间1")
    private String roomName;

    /**
     * 房间所关联的图表id
     */
    @Schema(description = "图表ID", example = "20001")
    private Long diagramId;

    /**
     * 创建者id
     */
    @Schema(description = "创建者ID", example = "10001")
    private Long owerId;

    /**
     * 加密后的图表数据
     */
    private byte[] encryptedData;

    /**
     * 加密向量
     */
    private String iv;

    /**
     * 0代表公开，1代表不公开
     */
    @Schema(description = "是否公开（0公开，1私有）", example = "0")
    private Integer isPublic;

    /**
     * 是否删除
     */
    @TableLogic
    @Schema(description = "是否删除（0未删除，1已删除）", example = "0")
    private Integer isDelete;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private Date createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2024-01-01 10:00:00")
    private Date updateTime;

    /**
     * 是否关闭
     */
    @Schema(description = "是否关闭（0开启，1关闭）", example = "0")
    private Integer isOpen;

    /**
     * 访问密码
     */
    @Schema(description = "访问密码", example = "123456")
    private String accessKey;


}