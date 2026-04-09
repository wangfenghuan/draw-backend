package com.wfh.drawio.model.dto.diagram;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @Title: ArchAnalysisRequest
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.diagram
 * @Date 2026/4/9
 * @description: 架构分析请求（基于AST生成项目架构图）
 */
@Data
@Schema(name = "ArchAnalysisRequest", description = "架构分析请求")
public class ArchAnalysisRequest implements Serializable {

    /**
     * 图表ID（用于保存生成的架构图）
     */
    @Schema(description = "图表ID", example = "123456789", requiredMode = Schema.RequiredMode.REQUIRED)
    private String diagramId;

    /**
     * AST抽象语法树数据（类名、注解、依赖关系等）
     */
    @Schema(description = "AST抽象语法树数据，包含类名、注解、依赖关系等信息",
            example = "Class: UserController\nAnnotations: @RestController, @RequestMapping\nDependencies: UserService, DiagramService...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String astData;

    /**
     * 模型ID（可选，为空时使用系统默认模型）
     */
    @Schema(description = "模型ID（可选，为空时使用系统默认模型）", example = "gpt-4")
    private String modelId;
}