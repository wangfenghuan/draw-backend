package com.wfh.drawio.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

/**
 * 完整的项目分析结果，直接传给 LLM 生成架构图
 * <p>
 * framework 和 layers 字段为 LLM 提供必要的上下文，
 * 让 LLM 知道：这是什么框架、有哪几个架构层、应该如何分区布局。
 * @author fenghuanwang
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectAnalysisResult {

    /**
     * 技术框架标识，固定值 "Spring Boot"
     * 供 LLM Prompt 使用，无需在 Prompt 里重复声明框架
     */
    private String framework = "Spring Boot";

    /**
     * 实际出现的架构层列表 (由 Parser 自动推断)
     * 例如: ["API", "BIZ", "DATA", "INFRA"]
     * LLM 根据此列表在图中创建分组泳道，保证布局清晰
     */
    private List<String> layers;

    /** 架构节点列表 */
    private List<ArchNode> nodes;

    /** 节点间关系列表 */
    private List<ArchRelationship> relationships;
}
