package com.wfh.drawio.controller;

import cn.hutool.core.io.FileUtil;
import com.wfh.drawio.core.model.ArchNode;
import com.wfh.drawio.core.model.ArchRelationship;
import com.wfh.drawio.core.model.ProjectAnalysisResult;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.model.dto.codeparse.SimplifiedProjectDTO;
import com.wfh.drawio.model.dto.codeparse.SqlParseResultDTO;
import com.wfh.drawio.service.FileExtractionService;
import com.wfh.drawio.service.SpringBootJavaParserService;
import com.wfh.drawio.service.SqlParserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.nio.file.Path;
import java.util.List;

/**
 * @Title: CodePaseController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2026/2/16 10:51
 * @description: Spring Boot Architecture AST Parser Controller
 */
@RestController
@Slf4j
@RequestMapping("/codeparse")
@Tag(name = "Code Parser", description = "Spring Boot Architecture AST Parser API")
public class CodeParseController {

    @Resource
    private SpringBootJavaParserService javaParserService;

    @Resource
    private FileExtractionService fileExtractionService;

    @Resource
    private SqlParserService sqlParserService;

    /**
     * 上传并分析Spring Boot项目ZIP文件（完整版）
     *
     * @param file ZIP压缩包（包含Spring Boot源代码）
     * @return 完整的项目分析结果
     */
    @PostMapping("/springboot/upload/simple")
    @Operation(summary = "上传并分析Spring Boot项目（完整版）",
               description = """
                       上传Spring Boot项目ZIP文件，获取完整的架构分析结果。

                       **功能说明：**
                       - 解压并解析Spring Boot项目结构
                       - 提取Controller、Service、Repository等组件
                       - 分析组件间的依赖关系

                       **支持格式：**
                       - 仅支持ZIP压缩包

                       **返回内容：**
                       - 项目节点信息（类、接口、组件）
                       - 组件间关系
                       - 中间件信息""")
    public BaseResponse<ProjectAnalysisResult> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        log.info("Received file upload: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResultUtils.error(400, "Uploaded file is empty");
        }

        if (!file.getOriginalFilename().endsWith(".zip")) {
            return ResultUtils.error(400, "Only ZIP files are supported");
        }

        try {
            // Extract ZIP file to project tmp directory
            Path extractedPath = fileExtractionService.extractZipFile(file);
            log.info("Extracted to: {}", extractedPath.toAbsolutePath());

            // Parse project
            ProjectAnalysisResult result = javaParserService.parseProject(extractedPath.toString());
            FileUtil.del(extractedPath);
            return ResultUtils.success(result);
        } catch (Exception e) {
            log.error("Error analyzing uploaded project", e);
            return ResultUtils.error(500, "Failed to analyze project: " + e.getMessage());
        }
    }

    /**
     * 上传并分析Spring Boot项目（架构视图，简化版）
     *
     * @param file ZIP压缩包（包含Spring Boot源代码）
     * @return 简化的架构图数据（节点和连线）
     */
    @PostMapping("/springboot/upload")
    @Operation(summary = "上传并分析Spring Boot项目（架构视图）",
               description = """
                       上传Spring Boot项目ZIP文件，获取简化的架构图数据。

                       **功能说明：**
                       - 返回架构图所需的节点和连线数据
                       - 自动识别层级结构（API、业务、数据、中间件）
                       - 适合直接用于前端渲染架构图

                       **返回内容：**
                       - layers：层级列表
                       - components：组件节点列表
                       - links：组件间关系连线
                       - externalSystems：外部系统/中间件列表""")
    public BaseResponse<SimplifiedProjectDTO> uploadAndAnalyzeSimple(@RequestParam("file") MultipartFile file) {
        log.info("Received architecture analysis request: {}", file.getOriginalFilename());

        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".zip")) {
            return ResultUtils.error(400, "Please upload a valid ZIP file");
        }

        try {
            // Extract and parse
            Path extractedPath = fileExtractionService.extractZipFile(file);
            ProjectAnalysisResult fullResult = javaParserService.parseProject(extractedPath.toString());
            // Convert to architecture view
            SimplifiedProjectDTO architecture = convertToArchitecture(fullResult);
            // 删除解压后的临时文件
            FileUtil.del(extractedPath);
            return ResultUtils.success(architecture);
        } catch (Exception e) {
            log.error("Error analyzing project", e);
            return ResultUtils.error(500, "Analysis failed: " + e.getMessage());
        }
    }

    /**
     * Convert full project structure to architecture graph
     */
    private SimplifiedProjectDTO convertToArchitecture(ProjectAnalysisResult full) {
        SimplifiedProjectDTO arch = new SimplifiedProjectDTO();
        arch.setName("Analyzed Project");
        
        java.util.Set<String> layers = new java.util.HashSet<>();
        java.util.List<SimplifiedProjectDTO.ComponentNode> components = new java.util.ArrayList<>();
        // 1. Process Nodes
        if (full.getNodes() != null) {
            for (ArchNode node : full.getNodes()) {
                // Filter interesting nodes: must have a layer (i.e. a Spring Bean or Middleware)
                if (node.getLayer() == null) continue;

                SimplifiedProjectDTO.ComponentNode sNode = new SimplifiedProjectDTO.ComponentNode();
                sNode.setId(node.getName());
                // role 存储技术角色，如 CONTROLLER / SERVICE / MIDDLEWARE:REDIS 等
                sNode.setType(node.getRole() != null ? node.getRole() : node.getLayer());
                sNode.setDescription(node.getId()); // Full class name as description

                // 直接使用新的 layer 字段，无需再次从字符串推断
                String layerLabel = toLayerLabel(node.getLayer());
                sNode.setLayer(layerLabel);
                layers.add(layerLabel);
                components.add(sNode);
            }
        }
        arch.setLayers(layers);
        arch.setComponents(components);
        
        // 2. Process Relationships
        java.util.List<SimplifiedProjectDTO.RelationLink> links = new java.util.ArrayList<>();
        if (full.getRelationships() != null) {
            for (ArchRelationship rel : full.getRelationships()) {
                // Determine From/To simple names
                String from = getSimpleName(rel.getSourceId());
                String to = getSimpleName(rel.getTargetId());
                
                // Only include if both ends are likely components
                if (isComponent(from, components) && isComponent(to, components)) {
                    SimplifiedProjectDTO.RelationLink link = new SimplifiedProjectDTO.RelationLink();
                    link.setFrom(from);
                    link.setTo(to);
                    link.setType(rel.getType().equals("INJECTS") ? "USES" : rel.getType());
                    links.add(link);
                }
            }
        }
        arch.setLinks(links);
        
        // 3. Middleware nodes (新模型中中间件就是 layer=MIDDLEWARE 的节点)
        java.util.List<String> external = new java.util.ArrayList<>();
        full.getNodes().stream()
            .filter(n -> "MIDDLEWARE".equals(n.getLayer()))
            .forEach(n -> external.add(n.getName()));
        arch.setExternalSystems(external);
        
        return arch;
    }
    
    private String getSimpleName(String fullName) {
        if (fullName == null) return "";
        int lastDot = fullName.lastIndexOf(".");
        return lastDot > -1 ? fullName.substring(lastDot + 1) : fullName;
    }
    
    private boolean isComponent(String name, java.util.List<SimplifiedProjectDTO.ComponentNode> components) {
        return components.stream().anyMatch(c -> c.getId().equals(name));
    }
    
    /** 将 layer 枠为人可读的层级标签 */
    private String toLayerLabel(String layer) {
        if (layer == null) return "Other";
        switch (layer) {
            case "API":        return "API Layer";
            case "BIZ":        return "Business Layer";
            case "DATA":       return "Data Layer";
            case "INFRA":      return "Infrastructure";
            case "MIDDLEWARE": return "Middleware";
            default:           return layer;
        }
    }

    private boolean isMiddleware(String name, ProjectAnalysisResult full) {
        return full.getNodes().stream()
                .anyMatch(n -> "MIDDLEWARE".equals(n.getLayer()) && n.getName().equals(name));
    }

    /**
     * 解析SQL DDL语句
     *
     * @param file SQL文件（.sql格式）
     * @return 解析后的表结构和关系列表
     */
    @PostMapping("/parse/sql")
    @Operation(summary = "解析SQL DDL（Druid + 语义AI）",
               description = """
                       解析SQL DDL语句，提取表结构和推断关系。

                       **功能说明：**
                       - 使用Druid解析SQL语法
                       - 提取表名、字段名、字段类型
                       - 智能推断表间关系（外键、命名约定）

                       **支持格式：**
                       - 标准SQL DDL语句（CREATE TABLE等）

                       **返回内容：**
                       - 表名列表
                       - 字段信息（名称、类型、约束）
                       - 推断的表间关系""")
    public BaseResponse<List<SqlParseResultDTO>> parseSql(@RequestParam("file") MultipartFile file) {
        log.info("Received SQL parse request: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResultUtils.error(400, "File is empty");
        }

        try {
            String sqlContent = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            List<SqlParseResultDTO> result = sqlParserService.parseSql(sqlContent);
            return ResultUtils.success(result);
        } catch (Exception e) {
            log.error("Error parsing SQL", e);
            return ResultUtils.error(500, "SQL Parsing failed: " + e.getMessage());
        }
    }

    /**
     * Request DTO for analyze endpoint
     */
    @Data
    public static class AnalyzeRequest {
        private String projectPath;
    }
}
