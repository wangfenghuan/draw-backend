package com.wfh.drawio.controller;

import com.wfh.drawio.ai.agent.ArchAnalysisOrchestrator;
import com.wfh.drawio.annotation.AiFeature;
import com.wfh.drawio.annotation.RateLimit;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.model.dto.diagram.ArchAnalysisRequest;
import com.wfh.drawio.model.enums.RateLimitType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @Title: ArchAnalysisController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2026/4/9
 * @description: 架构分析接口（基于AST生成项目架构图）
 */
@Tag(name = "archAnalysisController", description = "架构分析接口")
@RestController
@RequestMapping("/arch")
@Slf4j
public class ArchAnalysisController {

    @Resource
    private ArchAnalysisOrchestrator archAnalysisOrchestrator;

    /**
     * 两阶段架构图生成（流式响应）
     *
     * 流程:
     * 1. AstSummaryAgent 分析 AST 数据，生成 YAML 架构摘要
     * 2. ArchDiagramAgent 接收 YAML 摘要，生成 draw.io 架构图
     *
     * @param request 架构分析请求
     * @return SSE流式响应
     */
    @PostMapping("/generate/stream")
    @AiFeature
    @RateLimit(limitType = RateLimitType.USER, rate = 1, rateInterval = 3)
    @Operation(summary = "流式生成项目架构图",
            description = """
                    两阶段架构图生成流程：

                    **阶段1：架构摘要生成**
                    - 分析AST抽象语法树数据
                    - 识别项目分层结构（Web层、服务层、缓存层、持久层等）
                    - 识别中间件依赖（Redis、MySQL、PostgreSQL等）
                    - 推导设计策略和优化策略
                    - 输出YAML格式的架构摘要

                    **阶段2：架构图生成**
                    - 根据YAML摘要生成draw.io架构图
                    - 采用分层布局展示各层组件
                    - 使用颜色区分不同层次
                    - 标注核心流转路径和数据流向

                    **请求参数：**
                    - diagramId：图表ID（必填，用于保存生成的架构图）
                    - astData：AST抽象语法树数据（必填）
                    - modelId：模型ID（可选，默认使用系统配置）

                    **限流规则：**
                    - 用户级别限流，3秒内最多1次

                    **权限要求：**
                    - 需要登录
                    - 需要消耗AI调用额度""")
    public SseEmitter genArchDiagramStream(@RequestBody ArchAnalysisRequest request) {
        String diagramId = request.getDiagramId();
        String astData = request.getAstData();

        if (StringUtils.isAnyEmpty(diagramId, astData)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "diagramId和astData不能为空");
        }

        log.info("开始架构分析流式生成，diagramId: {}, astData长度: {}", diagramId, astData.length());

        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        archAnalysisOrchestrator.genArchDiagramFromAst(request.getModelId(), diagramId, astData)
                .subscribe(chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }, error -> {
                    log.error("架构分析流式生成异常", error);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("架构分析失败: " + error.getMessage()));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }, () -> {
                    emitter.complete();
                });

        return emitter;
    }

    /**
     * 两阶段架构图生成（同步响应）
     *
     * @param request 架构分析请求
     * @return 生成的架构图XML
     */
    @PostMapping("/generate/sync")
    @AiFeature
    @RateLimit(limitType = RateLimitType.USER, rate = 1, rateInterval = 5)
    @Operation(summary = "同步生成项目架构图",
            description = """
                    同步模式生成项目架构图（适用于小规模AST数据）。

                    **与流式接口的区别：**
                    - 流式接口实时返回生成过程，适合大规模AST分析
                    - 同步接口等待完整生成后返回，适合需要完整结果的场景

                    **请求参数：**
                    - diagramId：图表ID（必填）
                    - astData：AST抽象语法树数据（必填）
                    - modelId：模型ID（可选）

                    **限流规则：**
                    - 用户级别限流，5秒内最多1次

                    **权限要求：**
                    - 需要登录""")
    public BaseResponse<String> genArchDiagramSync(@RequestBody ArchAnalysisRequest request) {
        String diagramId = request.getDiagramId();
        String astData = request.getAstData();

        if (StringUtils.isAnyEmpty(diagramId, astData)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "diagramId和astData不能为空");
        }

        log.info("开始架构分析同步生成，diagramId: {}", diagramId);

        String result = archAnalysisOrchestrator.genArchDiagramFromAstSync(request.getModelId(), diagramId, astData);

        if (result == null || result.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "架构图生成失败");
        }

        return ResultUtils.success(result);
    }
}