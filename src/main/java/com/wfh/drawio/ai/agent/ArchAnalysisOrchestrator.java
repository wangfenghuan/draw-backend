package com.wfh.drawio.ai.agent;

import cn.hutool.json.JSONUtil;
import com.wfh.drawio.ai.model.StreamEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * @Title: ArchAnalysisOrchestrator
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.agent
 * @Date 2026/4/9 17:17
 * @description: 架构分析协调器，协调 AstSummaryAgent 和 ArchDiagramAgent 完成两阶段架构图生成
 */
@Component
@Slf4j
public class ArchAnalysisOrchestrator {

    @Resource
    private AstSummaryAgent astSummaryAgent;

    @Resource
    private ArchDiagramAgent archDiagramAgent;

    /**
     * 两阶段架构图生成（流式响应）
     *
     * 工作流程:
     * 1. AstSummaryAgent 分析 AST 数据，生成 YAML 格式的架构摘要
     * 2. ArchDiagramAgent 接收 YAML 摘要，生成 draw.io 架构图
     *
     * @param modelId   模型 ID（可选）
     * @param diagramId 图表 ID（用于保存生成的架构图）
     * @param astData   AST 抽象语法树数据
     * @return 流式响应，包含两个阶段的输出
     */
    public Flux<String> genArchDiagramFromAst(String modelId, String diagramId, String astData) {
        log.info("开始两阶段架构图生成，diagramId: {}", diagramId);

        // 阶段1: 生成架构摘要
        Mono<String> summaryMono = astSummaryAgent.genSummary(modelId, astData)
                .filter(Objects::nonNull)
                .map(json -> {
                    StreamEvent event = JSONUtil.toBean(json, StreamEvent.class);
                    return event.getContent() != null ? event.getContent().toString() : "";
                })
                .reduce("", (acc, text) -> acc + text)
                .doOnSuccess(summary -> log.info("阶段1完成，架构摘要长度: {} 字符", summary != null ? summary.length() : 0));

        // 阶段2: 根据摘要生成架构图
        return summaryMono.flatMapMany(yamlSummary -> {
            log.info("开始阶段2，生成架构图...");

            // 先推送阶段分隔标记
            Flux<String> phaseMarker = Flux.just(JSONUtil.toJsonStr(StreamEvent.builder()
                    .type("phase")
                    .content("summary_complete")
                    .build()));

            // 然后生成架构图
            Flux<String> diagramFlux = archDiagramAgent.genArchDiagram(modelId, diagramId, yamlSummary);

            return Flux.concat(phaseMarker, diagramFlux);
        });
    }

    /**
     * 两阶段架构图生成（同步模式）
     *
     * @param modelId   模型 ID（可选）
     * @param diagramId 图表 ID
     * @param astData   AST 数据
     * @return 生成的架构图 XML
     */
    public String genArchDiagramFromAstSync(String modelId, String diagramId, String astData) {
        log.info("开始同步两阶段架构图生成，diagramId: {}", diagramId);

        // 阶段1: 生成架构摘要
        String yamlSummary = astSummaryAgent.genSummarySync(modelId, astData);
        log.info("阶段1完成，架构摘要长度: {} 字符", yamlSummary != null ? yamlSummary.length() : 0);

        if (yamlSummary == null || yamlSummary.isEmpty()) {
            log.error("架构摘要生成失败，无法继续生成架构图");
            return null;
        }

        // 阶段2: 根据摘要生成架构图
        String result = archDiagramAgent.genArchDiagramSync(modelId, diagramId, yamlSummary);
        log.info("阶段2完成，架构图生成完成");

        return result;
    }
}