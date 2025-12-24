package com.wfh.drawio.ai.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @Title: AiModelsProperties
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.config
 * @Date 2025/12/24 14:36
 * @description:
 */
@Data
@Configuration
@ConfigurationProperties("spring.ai.custom")
public class AiModelsProperties {

    private Map<String, ModelConfig> model;

    @Data
    public static class ModelConfig{
        private String baseUrl;

        private String apiKey;

        private String modelName;
    }
}
