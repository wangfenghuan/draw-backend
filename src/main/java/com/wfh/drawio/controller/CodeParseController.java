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
     * Upload and analyze Spring Boot project ZIP file
     *
     * @param file ZIP file containing Spring Boot source code
     * @return AI-optimized structured JSON with architecture information
     */
    @PostMapping("/springboot/upload")
    @Operation(summary = "Upload and Analyze Spring Boot Project", 
               description = "Upload a ZIP file containing Spring Boot source code and get AI-ready architecture analysis")
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
     * Upload and analyze - Architecture View (Ultra Simplified)
     *
     * @param file ZIP file containing Spring Boot source code
     * @return Architecture graph for AI diagram generation
     */
    @PostMapping("/springboot/upload/simple")
    @Operation(summary = "Upload and Analyze (Architecture Only)", 
               description = "Returns architecture graph (nodes and links) for diagram generation")
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
                // Filter interesting nodes (Classes with stereotype, or Middleware)
                if (node.getStereotype() == null) continue;
                
                SimplifiedProjectDTO.ComponentNode sNode = new SimplifiedProjectDTO.ComponentNode();
                
                // Use simple ClassName for ID to match previous behavior if possible, 
                // but ID in ArchNode is full class name.
                // Let's use node.getName() which is simple name.
                sNode.setId(node.getName()); 
                sNode.setType(node.getStereotype().replace("@", "")); // e.g. Controller
                sNode.setDescription(node.getId()); // Full name as description
                
                // Determine layer
                String layer = "Other";
                String type = sNode.getType();
                if (type.contains("Controller")) layer = "Controller Layer";
                else if (type.contains("Service")) layer = "Service Layer";
                else if (type.contains("Repository") || type.contains("Mapper")) layer = "Data Layer";
                else if (type.contains("Configuration")) layer = "Config Layer";
                else if (type.equalsIgnoreCase("Infrastructure")) layer = "Infrastructure";
                
                sNode.setLayer(layer);
                layers.add(layer);
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
        
        // 3. Process Middleware (In new model, they are just nodes)
        java.util.List<String> external = new java.util.ArrayList<>();
        full.getNodes().stream()
            .filter(n -> "MIDDLEWARE".equals(n.getType()))
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
    
    private boolean isMiddleware(String name, ProjectAnalysisResult full) {
         return full.getNodes().stream()
                 .anyMatch(n -> "MIDDLEWARE".equals(n.getType()) && n.getName().equals(name));
    }

    /**
     * Parse SQL DDL and return structured metadata with inferred relationships
     *
     * @param file SQL file (e.g., .sql)
     * @return List of parsed tables and relationships
     */
    @PostMapping("/parse/sql")
    @Operation(summary = "Parse SQL DDL (Druid + Semantic AI)", 
               description = "Parses SQL DDL to extracted tables, columns, and infers relationships using semantic analysis")
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
