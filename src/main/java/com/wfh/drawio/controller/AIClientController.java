package com.wfh.drawio.controller;

import com.wfh.drawio.ai.client.DrawClient;
import com.wfh.drawio.annotation.AiFeature;
import com.wfh.drawio.annotation.RateLimit;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.model.dto.diagram.CustomChatRequest;
import com.wfh.drawio.model.dto.diagram.FreeTrialRequest;
import com.wfh.drawio.model.enums.RateLimitType;
import com.wfh.drawio.service.AiService;
import com.wfh.drawio.service.DiagramService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @Title: AIClientController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/20 20:05
 * @description:
 */
@RestController
@RequestMapping("/chat")
@Slf4j
public class AIClientController {

    @Resource
    private DrawClient drawClient;

    @Resource
    private AiService aiService;

    @Resource
    private DiagramService diagramService;


    /**
     * 基础图表生成对话（已废弃）
     *
     * @param message   用户消息
     * @param diagramId 图表ID
     * @param modelId   模型ID
     * @return AI生成的回复
     * @deprecated 请使用流式接口 /stream 或 /custom/stream
     */
    @PostMapping("/gen")
    @Deprecated
    @AiFeature
    public String doChat(String message, String diagramId, String modelId){
        return drawClient.doChat(message, diagramId, modelId);
    }

    /**
     * 系统默认LLM流式生成图表
     *
     * @param request 聊天请求（包含消息、图表ID）
     * @return SSE流式响应
     */
    @PostMapping("/stream")
    @AiFeature
    @RateLimit(limitType = RateLimitType.USER, rate = 1, rateInterval = 1)
    @Operation(summary = "系统默认LLM流式生成图表",
            description = """
                    使用系统默认的AI模型流式生成图表内容。

                    **功能说明：**
                    - 使用SSE（Server-Sent Events）实现流式响应
                    - 基于用户消息生成或修改图表
                    - 自动记录对话历史

                    **请求参数：**
                    - message：用户消息（必填）
                    - diagramId：图表ID（必填）

                    **限流规则：**
                    - 用户级别限流，每秒最多1次

                    **权限要求：**
                    - 需要登录
                    - 需要消耗AI调用额度""")
    public SseEmitter doChatStream(@RequestBody CustomChatRequest request){
        String message = request.getMessage();
        String diagramId = request.getDiagramId();
        if (StringUtils.isAnyEmpty(message, diagramId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return aiService.getSseEmitter(request, drawClient);
    }

    /**
     * 使用自定义LLM流式生成图表
     *
     * @param request 自定义聊天请求（包含消息、图表ID、模型配置）
     * @return SSE流式响应
     */
    @PostMapping("/custom/stream")
    @Operation(summary = "使用自定义LLM生成图表",
            description = """
                    使用用户自定义的AI模型流式生成图表内容。

                    **功能说明：**
                    - 支持用户自定义AI模型（baseUrl、apiKey、modelId）
                    - 使用SSE实现流式响应

                    **请求参数：**
                    - message：用户消息（必填）
                    - diagramId：图表ID（必填）
                    - modelId：模型ID（必填）
                    - baseUrl：API基础URL（必填）
                    - apiKey：API密钥（必填）

                    **限流规则：**
                    - 用户级别限流，每秒最多1次

                    **权限要求：**
                    - 需要登录""")
    @RateLimit(limitType = RateLimitType.USER, rate = 1, rateInterval = 1)
    public SseEmitter doCustomChatStream(@RequestBody CustomChatRequest request) {
        String message = request.getMessage();
        String diagramId = request.getDiagramId();
        String modelId = request.getModelId();
        String baseUrl = request.getBaseUrl();
        String apiKey = request.getApiKey();
        if (StringUtils.isAnyEmpty(message, diagramId, modelId, baseUrl, apiKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return aiService.getSseEmitter(request, drawClient);
    }

    /**
     * 免费试用AI生成图表（无需登录）
     *
     * @param request 免费试用请求（包含消息内容）
     * @return SSE流式响应
     */
    @PostMapping("/free/stream")
    @Operation(summary = "免费试用AI生成图表",
            description = """
                    无需登录即可体验AI生成图表功能。

                    **功能说明：**
                    - 创建临时图表用于体验
                    - 使用系统默认模型生成
                    - 生成结果可后续登录保存

                    **限流规则：**
                    - IP级别限流，每天最多3次
                    - 限流周期：24小时

                    **权限要求：**
                    - 无需登录""")
    @RateLimit(key = "free_trial", rate = 3, rateInterval = 86400, limitType = RateLimitType.IP, message = "免费试用次数已用完，每天最多3次，请明天再试或注册登录使用")
    public SseEmitter freeTrialStream(@RequestBody FreeTrialRequest request) {
        String message = request.getMessage();
        if (StringUtils.isEmpty(message)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }

        // 创建临时图表用于免费试用
        String diagramId = aiService.createFreeTrialDiagram(message);

        // 构建请求，使用系统默认模型
        CustomChatRequest chatRequest = new CustomChatRequest();
        chatRequest.setMessage(message);
        chatRequest.setDiagramId(diagramId);
        chatRequest.setModelId(request.getModelId()); // 可选，默认使用系统配置的模型

        return aiService.getSseEmitter(chatRequest, drawClient);
    }

}
