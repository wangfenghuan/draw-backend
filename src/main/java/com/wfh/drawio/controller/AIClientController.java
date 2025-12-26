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
     * @param message
     * @param diagramId
     * @return
     */
    @PostMapping("/stream")
    public SseEmitter doChatStream(String message, String diagramId, String modelId){
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        StringBuilder fullResponse = new StringBuilder();

        // 1. 【修复】必须先在主线程绑定，否则 contextCapture 抓不到值
        DiagramContextUtil.bindConversationId(diagramId);
        // 如果你需要日志流，这里应该也要绑定 Sink，否则 Tool 里 log 会失效
        // DiagramContextUtil.bindSink(XXX);

        drawClient.doChatStream(message, diagramId, modelId)
                // 2. 【关键】捕获当前线程的上下文（ThreadLocal），并在后续 Reactor 线程中自动恢复
                .contextCapture()
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
                        emitter.send(SseEmitter.event().name("error").data("生成失败: " + error.getMessage()));
                    }catch (Exception e){
                        emitter.completeWithError(e);
                    }
                    // 出现异常也要清理资源（虽然 ThreadLocalAccessor 会自动 reset，但主线程的要手动清）
                    DiagramContextUtil.clear();
                }, () -> {
                    // 流结束
                    emitter.complete();
                    // 清理主线程资源
                    DiagramContextUtil.clear();
                });

        return emitter;
    }

}
