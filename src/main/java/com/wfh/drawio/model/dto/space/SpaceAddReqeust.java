package com.wfh.drawio.model.dto.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @Title: SpaceAddReqeust
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.space
 * @Date 2026/1/6 09:57
 * @description:
 */
@Data
public class SpaceAddReqeust implements Serializable {

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;



    /**
     * 创建用户 id
     */
    private Long userId;
}
