package com.wfh.drawio.spi.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.wfh.drawio.core.model.ArchNode;
import com.wfh.drawio.core.model.ArchRelationship;
import com.wfh.drawio.core.model.ProjectAnalysisResult;
import com.wfh.drawio.spi.LanguageParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Title: JavaSpringParser
 * @Author wangfenghuan
 * @description: 极致优化的 Spring Boot 项目解析器 (支持 Javadoc 提取)
 */
@Slf4j
public class JavaSpringParser implements LanguageParser {

    private static final Map<String, String> STEREOTYPE_MAPPING = Map.of(
            "RestController", "API Layer",
            "Controller", "API Layer",
            "Service", "Business Layer",
            "Repository", "Data Layer",
            "Mapper", "Data Layer",
            "Component", "Infrastructure",
            "Configuration", "Infrastructure",
            "Entity", "Data Layer",
            "Table", "Data Layer"
    );

    private static final Set<String> IGNORED_SUFFIXES = Set.of(
            "Test", "Tests", "DTO", "VO", "Request", "Response", "Exception", "Constant", "Config", "Utils", "Properties"
    );

    private static final Set<String> IGNORED_PACKAGES = Set.of(
            "java.", "javax.", "jakarta.",
            "org.springframework.", "org.slf4j.", "org.apache.",
            "com.baomidou.", "lombok.", "com.fasterxml."
    );

    @Override
    public String getName() {
        return "Java-Spring-Doc-Enhanced";
    }

    @Override
    public boolean canParse(String projectDir) {
        Path rootPath = Paths.get(projectDir);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) return false;
        try (Stream<Path> stream = Files.walk(rootPath, 2)) {
            return stream.filter(Files::isRegularFile).anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts");
            });
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public ProjectAnalysisResult parse(String projectDir) {
        log.info("Starting Analysis with Javadoc Extraction: {}", projectDir);
        JavaParser javaParser = initializeSymbolSolver(projectDir);
        List<ArchNode> rawNodes = new ArrayList<>();
        List<ArchRelationship> rawRelationships = new ArrayList<>();
        Set<String> processedClasses = new HashSet<>();

        try {
            List<Path> javaFiles = findJavaFiles(projectDir);
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) continue;

                    String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

                    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                        String className = clazz.getNameAsString();
                        if (isIgnoredClass(className)) return;

                        String fullClassName = getFullyQualifiedName(clazz, packageName);
                        if (processedClasses.contains(fullClassName)) return;

                        ArchNode node = analyzeClass(clazz, fullClassName);
                        if (node != null) rawNodes.add(node);

                        rawRelationships.addAll(analyzeRelationships(clazz, fullClassName, cu));
                        processedClasses.add(fullClassName);
                    });

                } catch (Exception e) {
                    log.warn("Parse error: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("IO Error", e);
        }

        return optimizeResult(rawNodes, rawRelationships);
    }

    private ArchNode analyzeClass(ClassOrInterfaceDeclaration clazz, String id) {
        ArchNode node = new ArchNode();
        node.setId(id);
        node.setName(clazz.getNameAsString());

        // 1. 提取 Javadoc 注释 (新增功能)
        String comment = extractClassComment(clazz);

        String type = "Class";
        String stereotype = "Model";

        if (clazz.isInterface()) type = "Interface";

        List<String> annotations = clazz.getAnnotations().stream().map(a -> a.getNameAsString()).collect(Collectors.toList());

        if (clazz.getNameAsString().endsWith("Mapper") || annotations.contains("Mapper")) {
            type = "Interface";
            stereotype = "Data Layer";
        }

        for (Map.Entry<String, String> entry : STEREOTYPE_MAPPING.entrySet()) {
            if (annotations.stream().anyMatch(a -> a.contains(entry.getKey()))) {
                type = entry.getKey().toUpperCase();
                stereotype = entry.getValue();
                break;
            }
        }

        node.setType(type);
        node.setStereotype(stereotype);

        // 2. 构建 Description (合并 注释 + 技术细节)
        List<String> descParts = new ArrayList<>();

        // Part A: 中文注释
        if (comment != null && !comment.isEmpty()) {
            descParts.add(comment);
        }

        // Part B: 技术细节 (API 路由 或 表名)
        if ("API Layer".equals(stereotype)) {
            List<String> routes = extractApiRoutes(clazz);
            if (!routes.isEmpty()) {
                descParts.add("APIs:\n" + String.join("\n", routes));
            }
        } else if (clazz.getNameAsString().endsWith("Entity") || annotations.contains("TableName")) {
            extractTableName(clazz).ifPresent(t -> descParts.add("Table: " + t));
        }

        if (!descParts.isEmpty()) {
            node.setDescription(String.join("\n\n", descParts));
        }

        node.setFields(Collections.emptyList());
        node.setMethods(Collections.emptyList());

        return node;
    }

    /**
     * 新增：提取并清洗 Javadoc
     */
    private String extractClassComment(ClassOrInterfaceDeclaration clazz) {
        return clazz.getComment()
                .map(Comment::getContent)
                .map(this::cleanJavadoc)
                .orElse("");
    }

    /**
     * 清洗 Javadoc：去除 * 号、@author 等标签
     */
    private String cleanJavadoc(String content) {
        if (content == null) return "";
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            // 去除开头的 * 和空格
            String cleanLine = line.trim().replaceAll("^\\*+\\s?", "").trim();

            // 遇到 @ 标签（如 @author, @date）停止读取，或者跳过
            // 这里策略是：只读取第一段描述，遇到 @ 就停止，通常第一段是核心描述
            if (cleanLine.startsWith("@")) {
                break;
            }
            // 忽略空行
            if (!cleanLine.isEmpty()) {
                sb.append(cleanLine).append(" "); // 将多行描述合并为一行
            }
        }
        return sb.toString().trim();
    }

    private ProjectAnalysisResult optimizeResult(List<ArchNode> nodes, List<ArchRelationship> relationships) {
        Set<String> validNodeIds = nodes.stream().map(ArchNode::getId).collect(Collectors.toSet());
        List<ArchRelationship> cleanRelationships = relationships.stream()
                .filter(r -> validNodeIds.contains(r.getSourceId()) && validNodeIds.contains(r.getTargetId()))
                .filter(r -> !r.getSourceId().equals(r.getTargetId()))
                .collect(Collectors.toList());

        Set<String> connectedNodes = new HashSet<>();
        cleanRelationships.forEach(r -> {
            connectedNodes.add(r.getSourceId());
            connectedNodes.add(r.getTargetId());
        });

        List<ArchNode> cleanNodes = nodes.stream()
                .filter(n -> {
                    if ("API Layer".equals(n.getStereotype())) return true;
                    return connectedNodes.contains(n.getId());
                })
                .collect(Collectors.toList());

        ProjectAnalysisResult result = new ProjectAnalysisResult();
        result.setNodes(cleanNodes);
        result.setRelationships(cleanRelationships);
        return result;
    }

    private List<ArchRelationship> analyzeRelationships(ClassOrInterfaceDeclaration clazz, String sourceId, CompilationUnit cu) {
        List<ArchRelationship> rels = new ArrayList<>();
        clazz.getExtendedTypes().forEach(t -> addRel(rels, sourceId, resolveType(t, cu), "EXTENDS"));
        clazz.getImplementedTypes().forEach(t -> addRel(rels, sourceId, resolveType(t, cu), "IMPLEMENTS"));
        clazz.getFields().forEach(field -> {
            if (field.isAnnotationPresent("Autowired") || field.isAnnotationPresent("Resource") || field.isAnnotationPresent("Inject")) {
                field.getVariables().forEach(v -> addRel(rels, sourceId, resolveType(field.getElementType(), cu), "DEPENDS_ON"));
            }
        });
        clazz.getConstructors().forEach(c -> c.getParameters().forEach(p -> addRel(rels, sourceId, resolveType(p.getType(), cu), "DEPENDS_ON")));
        return rels;
    }

    private boolean isIgnoredClass(String className) {
        return IGNORED_SUFFIXES.stream().anyMatch(className::endsWith);
    }

    private boolean isIgnoredPackage(String typeName) {
        if (typeName == null) return true;
        return IGNORED_PACKAGES.stream().anyMatch(typeName::startsWith);
    }

    private void addRel(List<ArchRelationship> rels, String src, String target, String type) {
        if (target == null || target.equals(src) || isIgnoredPackage(target)) return;
        ArchRelationship rel = new ArchRelationship();
        rel.setSourceId(src);
        rel.setTargetId(target);
        rel.setType(type);
        rels.add(rel);
    }

    private JavaParser initializeSymbolSolver(String projectDir) {
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        combinedSolver.add(new ReflectionTypeSolver());
        Path srcPath = Paths.get(projectDir, "src", "main", "java");
        if (Files.exists(srcPath)) {
            combinedSolver.add(new JavaParserTypeSolver(srcPath));
        } else {
            combinedSolver.add(new JavaParserTypeSolver(new File(projectDir)));
        }
        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(combinedSolver));
        return new JavaParser(config);
    }

    private String resolveType(ClassOrInterfaceType type, CompilationUnit cu) {
        try {
            return type.resolve().asReferenceType().getQualifiedName();
        } catch (Exception e) {
            return inferFullyQualifiedNameFromImports(type.getNameAsString(), cu);
        }
    }

    private String resolveType(com.github.javaparser.ast.type.Type type, CompilationUnit cu) {
        try {
            if (type.isClassOrInterfaceType()) return type.asClassOrInterfaceType().resolve().asReferenceType().getQualifiedName();
            return null;
        } catch (Exception e) {
            return inferFullyQualifiedNameFromImports(type.asString(), cu);
        }
    }

    private String inferFullyQualifiedNameFromImports(String simpleName, CompilationUnit cu) {
        Optional<ImportDeclaration> match = cu.getImports().stream()
                .filter(i -> !i.isAsterisk() && i.getNameAsString().endsWith("." + simpleName))
                .findFirst();
        if (match.isPresent()) return match.get().getNameAsString();
        String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    private String getFullyQualifiedName(ClassOrInterfaceDeclaration clazz, String packageName) {
        return packageName.isEmpty() ? clazz.getNameAsString() : packageName + "." + clazz.getNameAsString();
    }

    private List<String> extractApiRoutes(ClassOrInterfaceDeclaration clazz) {
        List<String> routes = new ArrayList<>();
        String basePath = "";
        Optional<AnnotationExpr> classMapping = clazz.getAnnotationByName("RequestMapping");
        if (classMapping.isPresent()) basePath = extractPath(classMapping.get());
        String finalBasePath = basePath;
        clazz.getMethods().forEach(m -> {
            Stream.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "RequestMapping").forEach(methodType -> {
                m.getAnnotationByName(methodType).ifPresent(ann -> {
                    String methodPath = extractPath(ann);
                    String httpMethod = methodType.replace("Mapping", "").toUpperCase();
                    if ("REQUEST".equals(httpMethod)) httpMethod = "ALL";
                    routes.add("[" + httpMethod + "] " + (finalBasePath + methodPath).replaceAll("//", "/"));
                });
            });
        });
        return routes;
    }

    private String extractPath(AnnotationExpr ann) {
        if (ann.isSingleMemberAnnotationExpr()) {
            return ann.asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
        } else if (ann.isNormalAnnotationExpr()) {
            return ann.asNormalAnnotationExpr().getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                    .findFirst().map(p -> p.getValue().toString().replace("\"", "")).orElse("");
        }
        return "";
    }

    private Optional<String> extractTableName(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("TableName").map(this::extractPath)
                .or(() -> clazz.getAnnotationByName("Table").map(this::extractPath));
    }

    private List<Path> findJavaFiles(String projectPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            return paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }
}