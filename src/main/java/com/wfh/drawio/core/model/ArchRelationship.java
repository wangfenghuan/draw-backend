package com.wfh.drawio.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 架构图中的"连线" (两个节点之间的依赖关系)
 * <p>
 * Token 节省策略：删除从未赋值的 label 字段；@JsonInclude(NON_NULL) 兜底。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArchRelationship {

    /** 起点节点 ID (全限定类名) */
    private String sourceId;

    /** 终点节点 ID (全限定类名) */
    private String targetId;

    /**
     * 关系类型 (用于 LLM 选择箭头样式)
     * 枚举值: DEPENDS_ON | EXTENDS | IMPLEMENTS
     * - DEPENDS_ON: 实线箭头 (注入/调用)
     * - EXTENDS: 空心三角实线 (继承)
     * - IMPLEMENTS: 空心三角虚线 (实现接口)
     */
    private String type;
}
