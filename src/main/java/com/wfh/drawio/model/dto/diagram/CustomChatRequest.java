package com.wfh.drawio.model.dto.diagram;

import lombok.Data;

import java.io.Serializable;

/**
 * @Title: CustomChatRequest
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.diagram
 * @Date 2025/12/27 11:52
 * @description:
 */
@Data
public class CustomChatRequest implements Serializable {

    /**
     * 对话消息
     */
    private String message;

    /**
     * 图表id
     */
    private String diagramId;

    /**
     * 模型名称
     */
    private String modelId;

    /**
     * 接口
     */
    private String baseUrl;

    private String apiKey;
}
