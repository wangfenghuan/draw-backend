package com.wfh.drawio.ai.utils;

import com.wfh.drawio.ai.model.StreamEvent;
import reactor.core.publisher.Sinks;

/**
 * @Title: DiagramContextUtil
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.utils
 * @Date 2025/12/21 08:57
 * @description:
 */
public class DiagramContextUtil {


    public static final String KEY_SINK = "CTX_EVENT_SINK";
    public static final String KEY_CONVERSATION_ID = "CTX_CONVERSATION_ID";

    /**
     * 当前绑定的会话ID
     */
    private static final ThreadLocal<String> CURRENT_CONVERSATION_ID = new ThreadLocal<>();

    /**
     * 用于存放"旁路管道"
     */
    public static final ThreadLocal<Sinks.Many<StreamEvent>> EVENT_SINK = new ThreadLocal<>();

    /**
     * 绑定管道
     * @param sink
     */
    public static void bindSink(Sinks.Many<StreamEvent> sink){
        EVENT_SINK.set(sink);
    }

    /**
     * 绑定会话ID到作用域值
     * @param conversationId
     */
    public static void bindConversationId(String conversationId){
        CURRENT_CONVERSATION_ID.set(conversationId);
    }

    /**
     * 发送工具日志，给前端展示工具调用过程
     * @param message
     */
    public static void log(String message){
        Sinks.Many<StreamEvent> sink = EVENT_SINK.get();
        if (sink != null){
            sink.tryEmitNext(StreamEvent.builder()
                    .type("too_call")
                    .content(message)
                    .build());
        }
    }

    /**
     * 发送工具结果，给前端渲染图表
     * @param data
     */
    public static void result(Object data){
        Sinks.Many<StreamEvent> sink = EVENT_SINK.get();
        if (sink != null){
            sink.tryEmitNext(StreamEvent.builder()
                    .type("tool_call_result")
                    .content(data)
                    .build());
        }
    }

    /**
     * 清理资源 (在流结束时调用)
     */
    public static void clear() {
        EVENT_SINK.remove();
        CURRENT_CONVERSATION_ID.remove();
    }

    /**
     * 获取当前会话id
     * @return
     */
    public static String getConversationId() {
        return CURRENT_CONVERSATION_ID.get();
    }

    public static Sinks.Many<StreamEvent> getSink() {
        return EVENT_SINK.get();
    }

}