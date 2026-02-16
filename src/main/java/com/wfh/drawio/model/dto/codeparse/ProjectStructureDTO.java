package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Title: ProjectStructureDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Root project structure DTO
 */
@Data
public class ProjectStructureDTO implements Serializable {
    /**
     * Project name
     */
    private String projectName;
    
    /**
     * Project path
     */
    private String projectPath;
    
    /**
     * Total number of files analyzed
     */
    private Integer totalFiles;
    
    /**
     * Total number of classes
     */
    private Integer totalClasses;
    
    /**
     * Packages in the project
     */
    private List<PackageInfoDTO> packages;
    
    /**
     * Analysis timestamp
     */
    private Long timestamp;
    
    /**
     * Spring Beans detected in the project
     */
    private List<BeanInfoDTO> springBeans;
    
    /**
     * Component relationships (calls, injections, etc.)
     */
    private List<RelationshipDTO> relationships;
    
    /**
     * Middleware detected in the project
     */
    private List<MiddlewareInfoDTO> middleware;
}
