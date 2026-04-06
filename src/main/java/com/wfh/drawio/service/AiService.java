package com.wfh.drawio.service;

import com.wfh.drawio.ai.client.DrawClient;
import com.wfh.drawio.model.dto.diagram.CustomChatRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.service.DiagramService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * @Title: AiService
 * @Author wangfenghuan
 * @Package com.wfh.drawio.service
 * @Date 2025/12/27 12:10
 * @description:
 */
@Service
@Slf4j
public class AiService {

    private final DiagramService diagramService;

    public AiService(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    @NotNull
    public SseEmitter getSseEmitter(CustomChatRequest request, DrawClient drawClient) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        StringBuilder fullResponse = new StringBuilder();
        String message = request.getMessage();
        String diagramId = request.getDiagramId();
        String modelId = request.getModelId();
        String baseUrl = request.getBaseUrl();
        String apiKey = request.getApiKey();
        // apikey和baseurl是空的话，就调用系统的模型
        if (StringUtils.isEmpty(baseUrl) || StringUtils.isEmpty(apiKey)){
            drawClient.doChatStream(message, diagramId, modelId)
                    .subscribe(chunk -> {
                        try{
                            fullResponse.append(chunk);
                            emitter.send(SseEmitter.event().data(chunk));
                        }catch (Exception e){
                            emitter.completeWithError(e);
                        }
                    }, error -> {
                        log.error("流式生成异常", error);
                        try{
                            String errorMsg = buildFriendlyErrorMessage(error);
                            emitter.send(SseEmitter.event().name("error").data(errorMsg));
                        }catch (Exception e){
                            emitter.completeWithError(e);
                        }
                    }, () -> {
                        emitter.complete();
                    });
        } else{
            drawClient.doCustomChatStream(request)
                    .subscribe(chunk -> {
                        try{
                            fullResponse.append(chunk);
                            emitter.send(SseEmitter.event().data(chunk));
                        }catch (Exception e){
                            emitter.completeWithError(e);
                        }
                    }, error -> {
                        log.error("流式生成异常", error);
                        try{
                            String errorMsg = buildFriendlyErrorMessage(error);
                            emitter.send(SseEmitter.event().name("error").data(errorMsg));
                        }catch (Exception e){
                            emitter.completeWithError(e);
                        }
                    }, () -> {
                        emitter.complete();
                    });
        }
        return emitter;
    }

    /**
     * 将底层异常转换为用户可读的错误信息。
     * 特别处理 tool argument JSON 截断场景（AI 生成 XML 过长，超出模型 max_tokens 限制）。
     */
    private String buildFriendlyErrorMessage(Throwable error) {
        String rootMsg = getRootCauseMessage(error);
        // JsonEOFException / 截断特征：'Unexpected end-of-input' 或 'ToolExecutionException'
        if (rootMsg != null && (rootMsg.contains("Unexpected end-of-input")
                || rootMsg.contains("JsonEOFException")
                || (error.getMessage() != null && error.getMessage().contains("Unexpected end-of-input")))) {
            return "生成失败：图表内容过于复杂，AI 输出超出了模型最大 token 限制导致截断。" +
                   "请尝试：① 简化图表要求（减少节点数）② 拆成多次生成 ③ 换用支持更长输出的模型";
        }
        return "生成失败: " + error.getMessage();
    }

    /** 递归获取最根本的异常信息 */
    private String getRootCauseMessage(Throwable t) {
        if (t == null) return null;
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return getRootCauseMessage(cause);
        }
        return t.getMessage();
    }

    /**
     * 创建免费试用的临时图表
     * 临时图表不绑定用户和空间，用于未登录用户的试用体验
     *
     * @param message 用户输入的消息
     * @return 临时图表的ID
     */
    public String createFreeTrialDiagram(String message) {
        Diagram diagram = new Diagram();
        // 不设置userId和spaceId，表示临时图表
        // 使用消息的前20个字符作为图表名称
        String name = message.length() > 20 ? message.substring(0, 20) + "..." : message;
        diagram.setName("[免费试用] " + name);
        diagram.setDescription("免费试用生成的临时图表");
        diagram.setDiagramType("free_trial");

        diagramService.save(diagram);
        log.info("创建免费试用临时图表: id={}, name={}", diagram.getId(), diagram.getName());

        return String.valueOf(diagram.getId());
    }

}
