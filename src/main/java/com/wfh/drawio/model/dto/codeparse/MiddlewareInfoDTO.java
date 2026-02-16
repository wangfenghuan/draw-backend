package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Title: MiddlewareInfoDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Middleware detection DTO
 */
@Data
public class MiddlewareInfoDTO implements Serializable {
    /**
     * Middleware type (RabbitMQ, Redis, Kafka, MySQL, PostgreSQL, MongoDB, etc.)
     */
    private String type;
    
    /**
     * Usage locations (class names)
     */
    private List<String> usageLocations;
    
    /**
     * Configuration keys found
     */
    private List<String> configKeys;
    
    /**
     * Detected through (IMPORT, ANNOTATION, CONFIGURATION)
     */
    private String detectionMethod;
}
