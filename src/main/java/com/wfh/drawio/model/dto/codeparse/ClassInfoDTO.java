package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Title: ClassInfoDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Class information DTO
 */
@Data
public class ClassInfoDTO implements Serializable {
    /**
     * Class name
     */
    private String className;
    
    /**
     * Fully qualified class name
     */
    private String fullClassName;
    
    /**
     * Class type (class, interface, enum, annotation)
     */
    private String classType;
    
    /**
     * Access modifier
     */
    private String accessModifier;
    
    /**
     * Is abstract class
     */
    private Boolean isAbstract;
    
    /**
     * Is final class
     */
    private Boolean isFinal;
    
    /**
     * Extended class (parent class)
     */
    private String extendsClass;
    
    /**
     * Implemented interfaces
     */
    private List<String> implementsInterfaces;
    
    /**
     * Methods in this class
     */
    private List<MethodInfoDTO> methods;
    
    /**
     * Fields in this class
     */
    private List<String> fields;
    
    /**
     * File path
     */
    private String filePath;
    
    /**
     * Line number in source file
     */
    private Integer lineNumber;
    
    /**
     * Annotations on this class
     */
    private List<AnnotationInfoDTO> annotations;
    
    /**
     * Spring Bean information (if this is a Spring component)
     */
    private BeanInfoDTO beanInfo;
    
    /**
     * Autowired fields in this class
     */
    private List<String> autowiredFields;
}
