package com.wfh.drawio.model.dto.feedback;

import com.wfh.drawio.common.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户反馈查询请求
 *
 * @author wangfenghuan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(name = "FeedbackQueryRequest", description = "用户反馈查询请求")
public class FeedbackQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    @Schema(description = "反馈ID", example = "10001")
    private Long id;

    /**
     * 用户id
     */
    @Schema(description = "用户ID", example = "10001")
    private Long userId;

    /**
     * 反馈内容（模糊搜索）
     */
    @Schema(description = "反馈内容（模糊搜索）", example = "bug")
    private String content;

    private static final long serialVersionUID = 1L;
}
