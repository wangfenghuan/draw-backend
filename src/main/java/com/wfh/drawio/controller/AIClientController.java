package com.wfh.drawio.controller;

import com.wfh.drawio.ai.client.DrawClient;
import com.wfh.drawio.ai.utils.DiagramContextUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
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


    /**
     * (基础)图表生成对话
     * @param message
     * @param diagramId 图表id
     * @return
     */
    @PostMapping("/gen")
    public String doChat(String message, String diagramId, String modelId){
        return ScopedValue.where(DiagramContextUtil.CONVERSATION_ID, String.valueOf(diagramId))
                .call(() -> drawClient.doChat(message, diagramId, modelId));
    }

    /**
     * 流式生成图表
     * @param message
     * @param diagramId
     * @return
     */
    @PostMapping("/stream")
    public SseEmitter doChatStream(String message, String diagramId, String modelId){
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        // 用于收集完整的内容
        StringBuilder fullResponse = new StringBuilder();

        drawClient.doChatStream(message, diagramId, modelId)
                .subscribe(chunk -> {
                    try{
                        fullResponse.append(chunk);
                        emitter.send(SseEmitter.event().data(chunk));
                    }catch (Exception e){
                        emitter.completeWithError(e);
                    }
                }, error -> {
                    try{
                        emitter.send(SseEmitter.event().name("error").data("生成失败"));
                    }catch (Exception e){
                        emitter.completeWithError(e);
                    }
                        }, () -> {
                    // 流结束
                            emitter.complete();
                        }
                );

        return emitter;
    }

}
