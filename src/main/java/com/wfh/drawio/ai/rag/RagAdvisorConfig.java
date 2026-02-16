package com.wfh.drawio.ai.rag;

import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Title: RagAdvisorConfig
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.rag
 * @Date 2026/2/15 16:34
 * @description: RAG 检索 Advisor 统一配置
 */
@Configuration
public class RagAdvisorConfig {

    @Value("${spring.ai.rag.top-k}")
    private int topK;

    /**
     * 统一的 RAG 问答 Advisor
     * 所有 ChatClient 共享同一个检索配置
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore pgVectorStore) {
        SearchRequest searchRequest = SearchRequest.builder()
                .similarityThreshold(0.4)
                .topK(topK)
                .build();
        return QuestionAnswerAdvisor.builder(pgVectorStore)
                .searchRequest(searchRequest)
                .build();
    }
}
