package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Title: BeanInfoDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Spring Bean information DTO
 */
@Data
public class BeanInfoDTO implements Serializable {
    /**
     * Bean name
     */
    private String beanName;
    
    /**
     * Bean class name
     */
    private String className;
    
    /**
     * Bean type (Controller, Service, Repository, Component, Configuration)
     */
    private String beanType;
    
    /**
     * Autowired dependencies (field injection)
     */
    private List<String> autowiredFields;
    
    /**
     * Constructor injection dependencies
     */
    private List<String> constructorDependencies;
    
    /**
     * Resource dependencies
     */
    private List<String> resourceDependencies;
}
