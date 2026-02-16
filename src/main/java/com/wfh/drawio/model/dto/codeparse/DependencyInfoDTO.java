package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;

/**
 * @Title: DependencyInfoDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Dependency information DTO
 */
@Data
public class DependencyInfoDTO implements Serializable {
    /**
     * Import statement
     */
    private String importStatement;
    
    /**
     * Is static import
     */
    private Boolean isStatic;
    
    /**
     * Is wildcard import
     */
    private Boolean isWildcard;
}
