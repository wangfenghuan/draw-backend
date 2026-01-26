package com.wfh.drawio.model.enums;

/**
 * @author fenghuanwang
 */

public enum RateLimitType {
    
    /**
     * 接口级别限流
     */
    API,
    
    /**
     * 用户级别限流
     */
    USER,
    
    /**
     * IP级别限流
     */
    IP
}