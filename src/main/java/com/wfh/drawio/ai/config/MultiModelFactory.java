package com.wfh.drawio.ai.config;

import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
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

    public ChatModel getChatModel(String modelId){
        if (modelCache.containsKey(modelId)){
            return modelCache.get(modelId);
        }
        AiModelsProperties.ModelConfig config = properties.getModel().get(modelId);
        if (config == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知的模型");
        }

        // 手动构建openAiApi
        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .temperature(0.7)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        modelCache.put(modelId, chatModel);
        return chatModel;
    }
}
