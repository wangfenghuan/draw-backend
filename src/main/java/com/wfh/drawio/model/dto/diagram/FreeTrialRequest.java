package com.wfh.drawio.model.dto.diagram;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @Title: FreeTrialRequest
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.diagram
 * @Date 2026/04/06
 * @description: 免费试用AI生成图表请求
 */
@Data
@Schema(name = "FreeTrialRequest", description = "免费试用AI生成图表请求")
public class FreeTrialRequest implements Serializable {

    /**
     * 对话消息
     */
    @Schema(description = "对话消息", example = "帮我生成一个简单的用户登录流程图")
    private String message;

    /**
     * 模型名称（可选，默认使用系统模型）
     */
    @Schema(description = "模型名称（可选）", example = "gpt-4o-mini")
    private String modelId;
}