package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @Title: AnnotationInfoDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Annotation information DTO
 */
@Data
public class AnnotationInfoDTO implements Serializable {
    /**
     * Annotation name (e.g., "Autowired", "RequestMapping")
     */
    private String name;
    
    /**
     * Full annotation name (e.g., "org.springframework.beans.factory.annotation.Autowired")
     */
    private String fullName;
    
    /**
     * Annotation parameters (key-value pairs)
     */
    private Map<String, String> parameters;
}
