package com.wfh.drawio.ai.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Title: DocumentLoader
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.rag
 * @Date 2026/2/15 14:57
 * @description: 文档加载器，支持 Markdown 读取 + 语义化分块
 *
 * 分块策略：
 * 1. MarkdownDocumentReader 按标题/水平线分段（结构化语义分割）
 * 2. TokenTextSplitter 对大段落进一步按 Token 分块，在句子边界处切割（细粒度语义分割）
 * 3. 块间重叠保证上下文连贯性
 */
@Component
@Slf4j
public class DocumentLoader {

    @Resource
    private ResourcePatternResolver resourcePatternResolver;

    /**
     * 语义化分块器
     * - chunkSize: 每块约 800 token（适合 embedding 模型的上下文窗口）
     * - minChunkSizeChars: 最少 350 字符才尝试分割
     * - minChunkLengthToEmbed: 低于 5 字符的块不做 embedding
     * - maxNumChunks: 单个文档最多切 100 块
     * - keepSeparator: 保留分隔符，保持语义连贯
     */
    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(100)
            .withKeepSeparator(true)
            .build();

    /**
     * 不需要嵌入到向量数据库的文件（系统提示词相关）
     */
    private static final List<String> EXCLUDED_FILES = List.of(
            "system_prompt.md",
            "xml_guide.md"
    );

    public List<Document> loadDoc() {
        List<Document> allDoc = new ArrayList<>();

        try {
            org.springframework.core.io.Resource[] resources = resourcePatternResolver.getResources("classpath:doc/*.md");
            for (org.springframework.core.io.Resource resource : resources) {
                String filename = resource.getFilename();

                // 跳过系统提示词相关文件，不做向量嵌入
                if (EXCLUDED_FILES.contains(filename)) {
                    log.debug("跳过系统提示词文件: {}", filename);
                    continue;
                }

                // 提取文档倒数第 3 和第 2 个字作为标签
                String status = filename.substring(filename.length() - 6, filename.length() - 4);

                // 第一层：Markdown 结构化分段
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("status", status)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                List<Document> rawDocs = markdownDocumentReader.get();

                // 第二层：Token 语义分块（在句子边界处切割）
                List<Document> chunkedDocs = textSplitter.apply(rawDocs);

                log.info("文件 {} -> 原始段落 {} 个，分块后 {} 个", filename, rawDocs.size(), chunkedDocs.size());
                allDoc.addAll(chunkedDocs);
            }
        } catch (IOException e) {
            log.error("文档加载失败", e);
        }

        log.info("共加载 {} 个文档块", allDoc.size());
        return allDoc;
    }
}
