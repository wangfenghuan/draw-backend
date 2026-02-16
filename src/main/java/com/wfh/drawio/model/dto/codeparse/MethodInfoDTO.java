package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Title: MethodInfoDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Method information DTO
 */
@Data
public class MethodInfoDTO implements Serializable {
    /**
     * Method name
     */
    private String name;
    
    /**
     * Return type
     */
    private String returnType;
    
    /**
     * Parameters (type name)
     */
    private List<String> parameters;
    
    /**
     * Access modifier (public, private, protected, default)
     */
    private String accessModifier;
    
    /**
     * Is static method
     */
    private Boolean isStatic;
    
    /**
     * Is abstract method
     */
    private Boolean isAbstract;
    
    /**
     * Line number in source file
     */
    private Integer lineNumber;
    
    /**
     * Annotations on this method
     */
    private List<AnnotationInfoDTO> annotations;
    
    /**
     * Method calls within this method (for relationship tracking)
     */
    private List<String> methodCalls;
}
