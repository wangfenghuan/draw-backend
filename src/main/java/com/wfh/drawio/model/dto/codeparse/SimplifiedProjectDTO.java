package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;
import java.util.List;
import java.util.Set;

/**
 * @Title: SimplifiedProjectDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Ultra-simplified architecture view for AI diagram generation
 */
@Data
public class SimplifiedProjectDTO {
    /**
     * Project name
     */
    private String name;
    
    /**
     * Architecture layers detected (e.g., Controller, Service, DAO)
     */
    private Set<String> layers;
    
    /**
     * Key components (Spring Beans)
     */
    private List<ComponentNode> components;
    
    /**
     * Relationships between components
     */
    private List<RelationLink> links;
    
    /**
     * External systems / Middleware
     */
    private List<String> externalSystems;
    
    @Data
    public static class ComponentNode {
        private String id;          // Class name
        private String type;        // Controller, Service, Repository, Component
        private String layer;       // Web, Business, Data
        private String description; // Brief description or path
    }
    
    @Data
    public static class RelationLink {
        private String from;        // Source component ID
        private String to;          // Target component ID
        private String type;        // CALLS, USES
    }
}
