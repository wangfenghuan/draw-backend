package com.wfh.drawio.controller;

import com.wfh.drawio.ai.client.DrawClient;
import com.wfh.drawio.ai.utils.DiagramContextUtil;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.model.dto.diagram.CustomChatRequest;
import com.wfh.drawio.service.AiService;
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
    public String doChat(String message, String diagramId, String modelId){
        // 绑定会话ID到ThreadLocal
        DiagramContextUtil.bindConversationId(diagramId);
        try {
            return drawClient.doChat(message, diagramId, modelId);
        } finally {
            // 确保清理ThreadLocal
            DiagramContextUtil.clear();
        }
    }

    /**
     * 流式生成图表
     * @param request
     * @return
     */
    @PostMapping("/stream")
    public SseEmitter doChatStream(@RequestBody CustomChatRequest request){
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
     * 使用自定义的模型生成图表
     *
     * @param request
     * @return
     */
    @PostMapping("/custom/stream")
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
