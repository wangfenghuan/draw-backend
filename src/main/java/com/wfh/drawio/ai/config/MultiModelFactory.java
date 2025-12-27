package com.wfh.drawio.ai.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Title: MultiModelFactory
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.config
 * @Date 2025/12/24 14:44
 * @description:
 */
@Component
public class MultiModelFactory {

    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    private final AiModelsProperties properties;

    public MultiModelFactory(AiModelsProperties properties) {
        this.properties = properties;
    }

    /**
     * 动态构建模型
     * @param modelId
     * @return
     */
    public ChatModel getChatModel(String modelId) {
        // 1. 先查缓存
        if (modelCache.containsKey(modelId)) {
            return modelCache.get(modelId);
        }
        // 2. 智能查找配置
        AiModelsProperties.ModelConfig config = findConfigByModelId(modelId);
        if (config == null) {
            throw new IllegalArgumentException("未找到支持模型 [" + modelId + "] 的相关配置，请检查 application.yml");
        }
        // 3. 构建 API (复用查找到的配置中的 URL 和 Key)
        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();
        // 4. 构建 Options
        String actualModelName = determineModelName(modelId, config);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(actualModelName)
                .temperature(0.7)
                .build();

        // 5. 创建并缓存
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        modelCache.put(modelId, chatModel);
        return chatModel;
    }

    /**
     * 获取自定义的chatmodel
     *
     * @param modelId
     * @param apiKey
     * @param baseUrl
     * @return
     */
    public ChatModel getCustomModel(String modelId, String apiKey, String baseUrl) {
        // 构建API
        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        // 构建options
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(modelId)
                .temperature(0.7)
                .build();
        return OpenAiChatModel.builder()
                .defaultOptions(openAiChatOptions)
                .openAiApi(openAiApi)
                .build();
    }

    /**
     * 智能查找模型
     * @param targetModelId
     * @return
     */
    private AiModelsProperties.ModelConfig findConfigByModelId(String targetModelId) {
        Map<String, AiModelsProperties.ModelConfig> models = properties.getModels();
        // 直接 Key 匹配
        if (models.containsKey(targetModelId)) {
            return models.get(targetModelId);
        }
        // 遍历 Value 匹配
        for (AiModelsProperties.ModelConfig config : models.values()) {
            if (targetModelId.equals(config.getModel())) {
                return config;
            }
        }
        // 前缀/包含匹配
        for (Map.Entry<String, AiModelsProperties.ModelConfig> entry : models.entrySet()) {
            String key = entry.getKey();
            if (targetModelId.toLowerCase().contains(key.toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 确定最终传给 API 的模型名称
     */
    private String determineModelName(String reqModelId, AiModelsProperties.ModelConfig config) {
        // 如果请求的ID在配置Map的Key里 (比如请求 "qwen")，则使用配置里的默认模型名 (比如 "qwen-max")
        if (properties.getModels().containsKey(reqModelId)) {
            return config.getModel();
        }
        // 这样可以实现一套 API Key 调用该厂商下的所有模型
        return reqModelId;
    }
}
