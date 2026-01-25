package com.wfh.drawio.model.dto.feedback;

import lombok.Data;

import java.io.Serializable;

/**
 * @Title: FedbackUpdateRequest
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.feedback
 * @Date 2026/1/25 10:18
 * @description:
 */
@Data
public class FeedbackUpdateRequest implements Serializable {

    /**
     * 反馈ID
     */
    private Long id;

    /**
     * 是否处理（0未处理，1处理）
     */
    private Integer isHandle;

}
