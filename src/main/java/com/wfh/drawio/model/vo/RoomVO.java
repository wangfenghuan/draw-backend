package com.wfh.drawio.model.vo;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.wfh.drawio.model.entity.DiagramRoom;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @Title: RoomVO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.vo
 * @Date 2025/12/28 11:21
 * @description: 房间视图对象
 */
@Data
@Schema(name = "RoomVO", description = "房间视图对象")
public class RoomVO implements Serializable {

    /**
     * 房间id
     */
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
    private Long ownerId;

    /**
     * 0代表公开，1代表不公开
     */
    @Schema(description = "是否公开（0公开，1私有）", example = "0")
    private Integer isPublic;

    /**
     * 是否删除
     */
    @Schema(description = "是否删除（0未删除，1已删除）", example = "0")
    private Integer isDelete;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private Date createTime;

    /**
     * 访问地址
     */
    @Schema(description = "访问地址")
    private String roomUrl;

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
     * 创建用户信息
     */
    @Schema(description = "创建用户信息")
    private UserVO userVO;

    /**
     * 空间id
     */
    @Schema(description = "空间id", example = "1111111")
    private Long spaceId;

    /**
     * 访问密码
     */
    @Schema(description = "访问密码", example = "123456")
    private String accessKey;

    public static RoomVO objToVo(DiagramRoom room) {
        return BeanUtil.copyProperties(room, RoomVO.class);
    }


}
