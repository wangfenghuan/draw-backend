package com.wfh.drawio.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 图表表
 * @author fenghuanwang
 * @TableName diagram
 */
@TableName(value ="diagram")
@Data
public class Diagram {
    /**
     * 图表主键id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 图表代码
     */
    private String diagramCode;

    /**
     * 图表名称
     */
    private String name;

    /**
     * 图片url
     */
    private String pictureUrl;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;


    /**
     * 是否删除（0未删除，1删除）
     */
    @TableLogic
    private Integer isDelete;


}