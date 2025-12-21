package com.wfh.drawio.ai.tools;


import jakarta.annotation.Resource;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @Title: ToolRegisteration
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:41
 * @description: 工具注册器
 */
@Component
@Configuration
public class ToolRegisteration {

    @Resource
    private EditDiagramTool editDiagramTool;

    @Resource
    private AppendDiagramTool appendDiagramTool;

    @Resource
    private CreateDiagramTool createDiagramTool;


    /**
     * 注册所有工具
     * @return
     */
    @Bean
    public ToolCallback[] allTools(){
        return ToolCallbacks.from(appendDiagramTool, createDiagramTool, editDiagramTool);
    }

}
