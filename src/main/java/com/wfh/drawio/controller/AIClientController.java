package com.wfh.drawio.controller;

import com.wfh.drawio.ai.client.DrawClient;
import com.wfh.drawio.annotation.AiFeature;
import com.wfh.drawio.annotation.RateLimit;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.model.dto.diagram.CustomChatRequest;
import com.wfh.drawio.model.enums.RateLimitType;
import com.wfh.drawio.service.AiService;
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


    /**
     * (基础)图表生成对话
     * @param message
     * @param diagramId 图表id
     * @return
     */
    @PostMapping("/gen")
    @Deprecated
    @AiFeature
    public String doChat(String message, String diagramId, String modelId){
        return drawClient.doChat(message, diagramId, modelId);
    }

    /**
     * 系统默认llm流式生成图表
     * @param request
     * @return
     */
    @PostMapping("/stream")
    @AiFeature
    @RateLimit(limitType = RateLimitType.USER, rate = 1, rateInterval = 1)
    @Operation(summary = "系统默认llm流式生成图表")
    public SseEmitter doChatStream(@RequestBody CustomChatRequest request){
        String message = request.getMessage();
        String diagramId = request.getDiagramId();
        if (StringUtils.isAnyEmpty(message, diagramId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return aiService.getSseEmitter(request, drawClient);
    }

    /**
     * 使用自定义的llm生成图表
     *
     * @param request
     * @return
     */
    @PostMapping("/custom/stream")
    @Operation(summary = "使用自定义的llm生成图表")
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

}
