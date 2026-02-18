package com.wfh.drawio.core.model;

import lombok.Data;

/**
 * 2. 架构图中的“连线” (比如继承、调用、外键)
 * @author fenghuanwang
 */
@Data
public class ArchRelationship {
    private String sourceId;    // 起点
    private String targetId;    // 终点
    private String type;        // 关系类型 (e.g., "DEPENDS_ON", "INHERITS", "CALLS", "FOREIGN_KEY")
    private String label;       // 连线上的文字 (e.g., "findUserById")
}
