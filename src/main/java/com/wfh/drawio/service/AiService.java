package com.wfh.drawio.service;

import com.wfh.drawio.ai.client.DrawClient;
import com.wfh.drawio.ai.utils.DiagramContextUtil;
import com.wfh.drawio.model.dto.diagram.CustomChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
            // 必须先在主线程绑定，否则 contextCapture 抓不到值
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
        } else{
            // 必须先在主线程绑定，否则 contextCapture 抓不到值
            DiagramContextUtil.bindConversationId(diagramId);
            // 如果你需要日志流，这里应该也要绑定 Sink，否则 Tool 里 log 会失效
            // DiagramContextUtil.bindSink(XXX);
            drawClient.doCustomChatStream(request)
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
        }
        return emitter;
    }

}
