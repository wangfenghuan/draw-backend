package com.wfh.drawio.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 图表表
 * @author fenghuanwang
 * @TableName diagram
 */
@TableName(value ="diagram")
@Data
@Schema(name = "Diagram", description = "图表表")
public class Diagram {
    /**
     * 图表主键id
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "图表主键ID", example = "123456789")
    private Long id;

    /**
     * 用户id
     */
    @Schema(description = "用户ID", example = "10001")
    private Long userId;

    /**
     * 图表代码
     */
    @Schema(description = "图表代码", example = "DIAGRAM_20240101_001")
    private String diagramCode;

    /**
     * 图表名称
     */
    @Schema(description = "图表名称", example = "系统架构图")
    private String name;

    /**
     * 图表描述
     */
    @Schema(description = "图表描述", example = "前后端分离架构")
    private String description;

    /**
     * 图片url
     */
    @Schema(description = "图片URL", example = "https://example.com/image.png")
    private String pictureUrl;

    /**
     * 空间id
     */
    @Schema(description = "空间id", example = "1111111")
    private Long spaceId;

    /**
     * 矢量图url
     */
    @Schema(description = "矢量图URL", example = "https://example.com/image.svg")
    private String svgUrl;

    /**
     * SVG文件大小（字节）
     */
    @Schema(description = "SVG文件大小（字节）", example = "51200")
    private Long svgSize;

    /**
     * PNG文件大小（字节）
     */
    @Schema(description = "PNG文件大小（字节）", example = "51200")
    private Long pngSize;

    /**
     * 图片总大小（字节）= svgSize + pngSize
     */
    @Schema(description = "图片总大小（字节）", example = "102400")
    private Long picSize;

    /**
     * 图表类型
     */
    @Schema(description = "图表类型", example = "UML类图")
    private String diagramType;

    /**
     * 免费试用图表类型标识
     */
    public static final String FREE_TRIAL_TYPE = "free_trial";

    /**
     * 判断是否是免费试用图表
     * @return true 如果是免费试用图表
     */
    public boolean isFreeTrial() {
        return FREE_TRIAL_TYPE.equals(this.diagramType);
    }

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
     * 是否删除（0未删除，1删除）
     */
    @TableLogic
    @Schema(description = "是否删除（0未删除，1删除）", example = "0")
    private Integer isDelete;


}