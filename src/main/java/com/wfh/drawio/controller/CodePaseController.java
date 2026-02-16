package com.wfh.drawio.controller;

import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.model.dto.codeparse.ProjectStructureDTO;
import com.wfh.drawio.model.dto.codeparse.SimplifiedProjectDTO;
import com.wfh.drawio.service.FileExtractionService;
import com.wfh.drawio.service.SpringBootJavaParserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.nio.file.Path;

/**
 * @Title: CodePaseController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2026/2/16 10:51
 * @description: Spring Boot Architecture AST Parser Controller
 */
@RestController
@Slf4j
@RequestMapping("/pasecode")
@Tag(name = "Code Parser", description = "Spring Boot Architecture AST Parser API")
public class CodePaseController {

    @Resource
    private SpringBootJavaParserService javaParserService;

    @Resource
    private FileExtractionService fileExtractionService;

    /**
     * Upload and analyze Spring Boot project ZIP file
     *
     * @param file ZIP file containing Spring Boot source code
     * @return AI-optimized structured JSON with architecture information
     */
    @PostMapping("/upload")
    @Operation(summary = "Upload and Analyze Spring Boot Project", 
               description = "Upload a ZIP file containing Spring Boot source code and get AI-ready architecture analysis")
    public BaseResponse<ProjectStructureDTO> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
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
            ProjectStructureDTO result = javaParserService.parseProject(extractedPath.toString());
            
            // Add extracted path to result for reference
            result.setProjectPath(extractedPath.toAbsolutePath().toString());
            
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
    @PostMapping("/upload/simple")
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
            ProjectStructureDTO fullResult = javaParserService.parseProject(extractedPath.toString());
            
            // Convert to architecture view
            SimplifiedProjectDTO architecture = convertToArchitecture(fullResult);
            
            return ResultUtils.success(architecture);
            
        } catch (Exception e) {
            log.error("Error analyzing project", e);
            return ResultUtils.error(500, "Analysis failed: " + e.getMessage());
        }
    }

    /**
     * Convert full project structure to architecture graph
     */
    private SimplifiedProjectDTO convertToArchitecture(ProjectStructureDTO full) {
        SimplifiedProjectDTO arch = new SimplifiedProjectDTO();
        arch.setName(full.getProjectName());
        
        java.util.Set<String> layers = new java.util.HashSet<>();
        java.util.List<SimplifiedProjectDTO.ComponentNode> components = new java.util.ArrayList<>();
        
        // 1. Process Components (Beans)
        if (full.getSpringBeans() != null) {
            for (com.wfh.drawio.model.dto.codeparse.BeanInfoDTO bean : full.getSpringBeans()) {
                SimplifiedProjectDTO.ComponentNode node = new SimplifiedProjectDTO.ComponentNode();
                node.setId(bean.getBeanName());
                node.setType(bean.getBeanType());
                
                // Determine layer
                String layer = "Other";
                if (bean.getBeanType().contains("Controller")) layer = "Controller Layer";
                else if (bean.getBeanType().contains("Service")) layer = "Service Layer";
                else if (bean.getBeanType().contains("Repository") || bean.getBeanType().contains("Mapper")) layer = "Data Layer";
                else if (bean.getBeanType().contains("Configuration")) layer = "Config Layer";
                
                node.setLayer(layer);
                node.setDescription(bean.getClassName());
                
                layers.add(layer);
                components.add(node);
            }
        }
        arch.setLayers(layers);
        arch.setComponents(components);
        
        // 2. Process Relationships
        java.util.List<SimplifiedProjectDTO.RelationLink> links = new java.util.ArrayList<>();
        if (full.getRelationships() != null) {
            for (com.wfh.drawio.model.dto.codeparse.RelationshipDTO rel : full.getRelationships()) {
                // Simplify: class names to bean names (simple heuristic)
                String from = getSimpleName(rel.getSourceClass());
                String to = getSimpleName(rel.getTargetClass());
                
                // Only include if both ends are likely components
                if (isComponent(from, components) && (isComponent(to, components) || isMiddleware(to, full))) {
                    SimplifiedProjectDTO.RelationLink link = new SimplifiedProjectDTO.RelationLink();
                    link.setFrom(from);
                    link.setTo(to);
                    link.setType(rel.getRelationshipType().equals("INJECTS") ? "USES" : rel.getRelationshipType());
                    links.add(link);
                }
            }
        }
        arch.setLinks(links);
        
        // 3. Process Middleware
        java.util.List<String> external = new java.util.ArrayList<>();
        if (full.getMiddleware() != null) {
            for (com.wfh.drawio.model.dto.codeparse.MiddlewareInfoDTO mw : full.getMiddleware()) {
                external.add(mw.getType());
                
                // Add middleware as component nodes to show connections
                SimplifiedProjectDTO.ComponentNode node = new SimplifiedProjectDTO.ComponentNode();
                node.setId(mw.getType());
                node.setType("Middleware");
                node.setLayer("Infrastructure");
                node.setDescription("External System");
                components.add(node);
            }
        }
        arch.setExternalSystems(external);
        
        return arch;
    }
    
    private String getSimpleName(String fullName) {
        if (fullName == null) return "";
        int lastDot = fullName.lastIndexOf(".");
        return lastDot > -1 ? fullName.substring(lastDot + 1) : fullName;
    }
    
    private boolean isComponent(String name, java.util.List<SimplifiedProjectDTO.ComponentNode> components) {
        // Adjust logic to match bean names or class names
        return components.stream().anyMatch(c -> c.getId().equalsIgnoreCase(name) || c.getDescription().endsWith("." + name));
    }
    
    private boolean isMiddleware(String name, ProjectStructureDTO full) {
        if (full.getMiddleware() == null) return false;
        return full.getMiddleware().stream().anyMatch(m -> m.getType().equalsIgnoreCase(name));
    }

    /**
     * Request DTO for analyze endpoint
     */
    @Data
    public static class AnalyzeRequest {
        private String projectPath;
    }
}
