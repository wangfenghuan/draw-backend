package com.wfh.drawio.ai.tools;


import jakarta.annotation.Resource;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Title: ToolRegisteration (已废弃)
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:41
 * @description: 工具注册器 - 已废弃，工具由 Spring AI 自动扫描发现
 *
 * 在 Spring AI 1.1.2 中，工具通过 @Tool 注解自动发现，无需手动注册 ToolCallback数组
 * 手动注册 toolCallbacks 会导致流式响应时 toolName is null 错误
 *
 * @deprecated 被废弃 - Spring AI 1.1.2+ 中使用 @Tool 注解自动发现工具
 */
@Configuration
@Deprecated
public class ToolRegisteration {
    // 该类已废弃，不再使用，仅作为兼容性保留
}
