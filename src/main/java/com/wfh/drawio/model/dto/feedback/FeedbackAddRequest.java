package com.wfh.drawio.model.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户反馈创建请求
 *
 * @author wangfenghuan
 */
@Data
@Schema(name = "FeedbackAddRequest", description = "用户反馈添加请求")
public class FeedbackAddRequest implements Serializable {

    /**
     * 反馈内容
     */
    @Schema(description = "反馈内容", example = "在使用过程中发现了一个bug...")
    private String content;

    /**
     * 反馈图片URL（可选）
     */
    @Schema(description = "反馈图片URL", example = "https://example.com/feedback.jpg")
    private String pictureUrl;

    private static final long serialVersionUID = 1L;
}
