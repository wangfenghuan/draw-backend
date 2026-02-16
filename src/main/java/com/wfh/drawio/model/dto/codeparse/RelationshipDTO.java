package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;

/**
 * @Title: RelationshipDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Component relationship DTO
 */
@Data
public class RelationshipDTO implements Serializable {
    /**
     * Source class (caller)
     */
    private String sourceClass;
    
    /**
     * Target class (callee)
     */
    private String targetClass;
    
    /**
     * Relationship type (CALLS, INJECTS, EXTENDS, IMPLEMENTS)
     */
    private String relationshipType;
    
    /**
     * Method name (if applicable)
     */
    private String methodName;
    
    /**
     * Description
     */
    private String description;
}
