package com.wfh.drawio.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.wfh.drawio.model.dto.codeparse.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Title: SpringBootJavaParserService
 * @Author wangfenghuan
 * @Package com.wfh.drawio.service
 * @Date 2026/2/16
 * @description: Enhanced Java AST Parser Service with Spring Boot analysis
 */
@Service
@Slf4j
public class SpringBootJavaParserService {

    private final JavaParser javaParser = new JavaParser();

    // Spring annotation names
    private static final Set<String> SPRING_COMPONENT_ANNOTATIONS = Set.of(
            "Controller", "RestController", "Service", "Repository", "Component", "Configuration"
    );

    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            "Autowired", "Resource", "Inject"
    );

    // Middleware detection patterns
    private static final Map<String, List<String>> MIDDLEWARE_PATTERNS = Map.of(
            "RabbitMQ", List.of("rabbitmq", "RabbitTemplate", "RabbitListener", "AmqpTemplate"),
            "Redis", List.of("redis", "RedisTemplate", "Jedis", "Lettuce"),
            "Kafka", List.of("kafka", "KafkaTemplate", "KafkaListener"),
            "MySQL", List.of("mysql", "jdbc:mysql"),
            "PostgreSQL", List.of("postgresql", "jdbc:postgresql"),
            "MongoDB", List.of("mongodb", "MongoTemplate", "MongoRepository"),
            "Elasticsearch", List.of("elasticsearch", "ElasticsearchTemplate"),
            "MyBatis", List.of("mybatis", "Mapper", "@Mapper"),
            "JPA", List.of("javax.persistence", "jakarta.persistence", "EntityManager")
    );

    /**
     * Parse entire Java project with Spring Boot analysis
     *
     * @param projectPath Project root path
     * @return Enhanced project structure DTO
     */
    public ProjectStructureDTO parseProject(String projectPath) {
        log.info("Starting Spring Boot project analysis: {}", projectPath);

        ProjectStructureDTO projectStructure = new ProjectStructureDTO();
        projectStructure.setProjectPath(projectPath);
        projectStructure.setProjectName(new File(projectPath).getName());
        projectStructure.setTimestamp(System.currentTimeMillis());

        Map<String, PackageInfoDTO> packageMap = new HashMap<>();
        List<BeanInfoDTO> springBeans = new ArrayList<>();
        List<RelationshipDTO> relationships = new ArrayList<>();
        Map<String, MiddlewareInfoDTO> middlewareMap = new HashMap<>();
        
        int totalFiles = 0;
        int totalClasses = 0;

        try {
            // Find all Java files
            List<Path> javaFiles = findJavaFiles(projectPath);
            totalFiles = javaFiles.size();
            log.info("Found {} Java files", totalFiles);

            // Parse each Java file
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) {
                        log.warn("Failed to parse file: {}", javaFile);
                        continue;
                    }

                    String packageName = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString())
                            .orElse("default");

                    PackageInfoDTO packageInfo = packageMap.computeIfAbsent(packageName, k -> {
                        PackageInfoDTO pkg = new PackageInfoDTO();
                        pkg.setPackageName(packageName);
                        pkg.setClasses(new ArrayList<>());
                        pkg.setDependencies(new ArrayList<>());
                        return pkg;
                    });

                    // Extract dependencies and detect middleware
                    List<DependencyInfoDTO> dependencies = extractDependencies(cu);
                    packageInfo.getDependencies().addAll(dependencies);
                    detectMiddlewareFromImports(cu, middlewareMap);

                    // Extract classes with Spring Boot analysis
                    List<ClassInfoDTO> classes = extractClassesWithSpringAnalysis(cu, javaFile.toString());
                    packageInfo.getClasses().addAll(classes);
                    totalClasses += classes.size();

                    // Extract Spring Beans
                    for (ClassInfoDTO classInfo : classes) {
                        if (classInfo.getBeanInfo() != null) {
                            springBeans.add(classInfo.getBeanInfo());
                        }
                        
                        // Extract relationships
                        relationships.addAll(extractRelationships(classInfo));
                    }

                } catch (Exception e) {
                    log.error("Error parsing file: {}", javaFile, e);
                }
            }

            // Remove duplicate dependencies per package
            packageMap.values().forEach(pkg -> {
                Set<String> seen = new HashSet<>();
                List<DependencyInfoDTO> uniqueDeps = pkg.getDependencies().stream()
                        .filter(dep -> seen.add(dep.getImportStatement()))
                        .collect(Collectors.toList());
                pkg.setDependencies(uniqueDeps);
            });

        } catch (IOException e) {
            log.error("Error scanning project directory", e);
            throw new RuntimeException("Failed to scan project directory: " + e.getMessage());
        }

        projectStructure.setPackages(new ArrayList<>(packageMap.values()));
        projectStructure.setTotalFiles(totalFiles);
        projectStructure.setTotalClasses(totalClasses);
        projectStructure.setSpringBeans(springBeans);
        projectStructure.setRelationships(relationships);
        projectStructure.setMiddleware(new ArrayList<>(middlewareMap.values()));

        log.info("Analysis completed. Packages: {}, Classes: {}, Beans: {}, Relationships: {}, Middleware: {}",
                packageMap.size(), totalClasses, springBeans.size(), relationships.size(), middlewareMap.size());
        
        return projectStructure;
    }

    /**
     * Find all Java files in the project
     */
    private List<Path> findJavaFiles(String projectPath) throws IOException {
        Path startPath = Paths.get(projectPath);

        try (Stream<Path> paths = Files.walk(startPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Extract dependencies from compilation unit
     */
    private List<DependencyInfoDTO> extractDependencies(CompilationUnit cu) {
        return cu.getImports().stream()
                .map(this::createDependencyInfo)
                .collect(Collectors.toList());
    }

    /**
     * Create dependency info from import declaration
     */
    private DependencyInfoDTO createDependencyInfo(ImportDeclaration importDecl) {
        DependencyInfoDTO dependency = new DependencyInfoDTO();
        dependency.setImportStatement(importDecl.getNameAsString());
        dependency.setIsStatic(importDecl.isStatic());
        dependency.setIsWildcard(importDecl.isAsterisk());
        return dependency;
    }

    /**
     * Detect middleware from imports
     */
    private void detectMiddlewareFromImports(CompilationUnit cu, Map<String, MiddlewareInfoDTO> middlewareMap) {
        String fileName = cu.getStorage().map(s -> s.getFileName()).orElse("unknown");
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString().toLowerCase();
            
            for (Map.Entry<String, List<String>> entry : MIDDLEWARE_PATTERNS.entrySet()) {
                String middlewareType = entry.getKey();
                List<String> patterns = entry.getValue();
                
                for (String pattern : patterns) {
                    if (importName.contains(pattern.toLowerCase())) {
                        MiddlewareInfoDTO middleware = middlewareMap.computeIfAbsent(middlewareType, k -> {
                            MiddlewareInfoDTO m = new MiddlewareInfoDTO();
                            m.setType(middlewareType);
                            m.setUsageLocations(new ArrayList<>());
                            m.setConfigKeys(new ArrayList<>());
                            m.setDetectionMethod("IMPORT");
                            return m;
                        });
                        
                        if (!middleware.getUsageLocations().contains(fileName)) {
                            middleware.getUsageLocations().add(fileName);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Extract classes with Spring Boot analysis
     */
    private List<ClassInfoDTO> extractClassesWithSpringAnalysis(CompilationUnit cu, String filePath) {
        List<ClassInfoDTO> classes = new ArrayList<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            ClassInfoDTO classInfo = createClassInfoWithSpring(classDecl, filePath, cu);
            classes.add(classInfo);
        });

        cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
            ClassInfoDTO classInfo = createEnumInfo(enumDecl, filePath, cu);
            classes.add(classInfo);
        });

        return classes;
    }

    /**
     * Create class info with Spring analysis
     */
    private ClassInfoDTO createClassInfoWithSpring(ClassOrInterfaceDeclaration classDecl, String filePath, CompilationUnit cu) {
        ClassInfoDTO classInfo = new ClassInfoDTO();

        classInfo.setClassName(classDecl.getNameAsString());
        classInfo.setFullClassName(getFullClassName(cu, classDecl.getNameAsString()));
        classInfo.setClassType(classDecl.isInterface() ? "interface" : "class");
        classInfo.setAccessModifier(getAccessModifier(classDecl.getModifiers()));
        classInfo.setIsAbstract(classDecl.isAbstract());
        classInfo.setIsFinal(classDecl.isFinal());
        classInfo.setFilePath(filePath);
        classInfo.setLineNumber(classDecl.getBegin().map(pos -> pos.line).orElse(0));

        classInfo.setExtendsClass(
                classDecl.getExtendedTypes().stream()
                        .map(ClassOrInterfaceType::getNameAsString)
                        .findFirst()
                        .orElse(null)
        );

        classInfo.setImplementsInterfaces(
                classDecl.getImplementedTypes().stream()
                        .map(ClassOrInterfaceType::getNameAsString)
                        .collect(Collectors.toList())
        );

        // Extract annotations
        List<AnnotationInfoDTO> annotations = extractAnnotations(classDecl.getAnnotations());
        classInfo.setAnnotations(annotations);

        // Extract methods with Spring analysis
        classInfo.setMethods(extractMethodsWithSpring(classDecl));

        // Extract fields
        classInfo.setFields(extractFields(classDecl));

        // Extract autowired fields
        List<String> autowiredFields = extractAutowiredFields(classDecl);
        classInfo.setAutowiredFields(autowiredFields);

        // Create Bean info if this is a Spring component
        BeanInfoDTO beanInfo = createBeanInfo(classInfo, classDecl, autowiredFields);
        classInfo.setBeanInfo(beanInfo);

        return classInfo;
    }

    /**
     * Extract annotations from annotation expressions
     */
    private List<AnnotationInfoDTO> extractAnnotations(List<AnnotationExpr> annotationExprs) {
        return annotationExprs.stream()
                .map(this::createAnnotationInfo)
                .collect(Collectors.toList());
    }

    /**
     * Create annotation info
     */
    private AnnotationInfoDTO createAnnotationInfo(AnnotationExpr annotationExpr) {
        AnnotationInfoDTO annotation = new AnnotationInfoDTO();
        annotation.setName(annotationExpr.getNameAsString());
        annotation.setFullName(annotationExpr.getName().toString());

        Map<String, String> parameters = new HashMap<>();
        if (annotationExpr.isSingleMemberAnnotationExpr()) {
            SingleMemberAnnotationExpr singleMember = annotationExpr.asSingleMemberAnnotationExpr();
            parameters.put("value", singleMember.getMemberValue().toString());
        } else if (annotationExpr.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normalAnnotation = annotationExpr.asNormalAnnotationExpr();
            normalAnnotation.getPairs().forEach(pair -> {
                parameters.put(pair.getNameAsString(), pair.getValue().toString());
            });
        }
        annotation.setParameters(parameters);

        return annotation;
    }

    /**
     * Extract autowired fields
     */
    private List<String> extractAutowiredFields(ClassOrInterfaceDeclaration classDecl) {
        List<String> autowiredFields = new ArrayList<>();

        classDecl.getFields().forEach(field -> {
            boolean isAutowired = field.getAnnotations().stream()
                    .anyMatch(ann -> INJECTION_ANNOTATIONS.contains(ann.getNameAsString()));

            if (isAutowired) {
                field.getVariables().forEach(var -> {
                    autowiredFields.add(var.getTypeAsString() + " " + var.getNameAsString());
                });
            }
        });

        return autowiredFields;
    }

    /**
     * Create Bean info if this is a Spring component
     */
    private BeanInfoDTO createBeanInfo(ClassInfoDTO classInfo, ClassOrInterfaceDeclaration classDecl, List<String> autowiredFields) {
        // Check if class has Spring component annotation
        Optional<String> beanType = classInfo.getAnnotations().stream()
                .map(AnnotationInfoDTO::getName)
                .filter(SPRING_COMPONENT_ANNOTATIONS::contains)
                .findFirst();

        if (beanType.isEmpty()) {
            return null;
        }

        BeanInfoDTO beanInfo = new BeanInfoDTO();
        beanInfo.setBeanName(classInfo.getClassName());
        beanInfo.setClassName(classInfo.getFullClassName());
        beanInfo.setBeanType(beanType.get());
        beanInfo.setAutowiredFields(autowiredFields);

        // Extract constructor dependencies
        List<String> constructorDeps = new ArrayList<>();
        classDecl.getConstructors().forEach(constructor -> {
            constructor.getParameters().forEach(param -> {
                constructorDeps.add(param.getTypeAsString() + " " + param.getNameAsString());
            });
        });
        beanInfo.setConstructorDependencies(constructorDeps);

        beanInfo.setResourceDependencies(new ArrayList<>());

        return beanInfo;
    }

    /**
     * Extract methods with Spring analysis
     */
    private List<MethodInfoDTO> extractMethodsWithSpring(TypeDeclaration<?> typeDecl) {
        return typeDecl.getMethods().stream()
                .map(this::createMethodInfoWithSpring)
                .collect(Collectors.toList());
    }

    /**
     * Create method info with Spring analysis
     */
    private MethodInfoDTO createMethodInfoWithSpring(MethodDeclaration method) {
        MethodInfoDTO methodInfo = new MethodInfoDTO();

        methodInfo.setName(method.getNameAsString());
        methodInfo.setReturnType(method.getTypeAsString());
        methodInfo.setAccessModifier(getAccessModifier(method.getModifiers()));
        methodInfo.setIsStatic(method.isStatic());
        methodInfo.setIsAbstract(method.isAbstract());
        methodInfo.setLineNumber(method.getBegin().map(pos -> pos.line).orElse(0));

        List<String> parameters = method.getParameters().stream()
                .map(param -> param.getTypeAsString() + " " + param.getNameAsString())
                .collect(Collectors.toList());
        methodInfo.setParameters(parameters);

        // Extract annotations
        List<AnnotationInfoDTO> annotations = extractAnnotations(method.getAnnotations());
        methodInfo.setAnnotations(annotations);

        // Extract method calls
        List<String> methodCalls = extractMethodCalls(method);
        methodInfo.setMethodCalls(methodCalls);

        return methodInfo;
    }

    /**
     * Extract method calls from method body
     */
    private List<String> extractMethodCalls(MethodDeclaration method) {
        List<String> methodCalls = new ArrayList<>();

        method.findAll(MethodCallExpr.class).forEach(call -> {
            String callName = call.getNameAsString();
            call.getScope().ifPresent(scope -> {
                methodCalls.add(scope.toString() + "." + callName);
            });
            if (call.getScope().isEmpty()) {
                methodCalls.add(callName);
            }
        });

        return methodCalls;
    }

    /**
     * Extract relationships from class info
     */
    private List<RelationshipDTO> extractRelationships(ClassInfoDTO classInfo) {
        List<RelationshipDTO> relationships = new ArrayList<>();

        // Inheritance relationship
        if (classInfo.getExtendsClass() != null) {
            RelationshipDTO rel = new RelationshipDTO();
            rel.setSourceClass(classInfo.getFullClassName());
            rel.setTargetClass(classInfo.getExtendsClass());
            rel.setRelationshipType("EXTENDS");
            rel.setDescription(classInfo.getClassName() + " extends " + classInfo.getExtendsClass());
            relationships.add(rel);
        }

        // Interface implementation
        if (classInfo.getImplementsInterfaces() != null) {
            for (String interfaceName : classInfo.getImplementsInterfaces()) {
                RelationshipDTO rel = new RelationshipDTO();
                rel.setSourceClass(classInfo.getFullClassName());
                rel.setTargetClass(interfaceName);
                rel.setRelationshipType("IMPLEMENTS");
                rel.setDescription(classInfo.getClassName() + " implements " + interfaceName);
                relationships.add(rel);
            }
        }

        // Dependency injection relationships
        if (classInfo.getAutowiredFields() != null) {
            for (String field : classInfo.getAutowiredFields()) {
                String[] parts = field.split(" ");
                if (parts.length >= 2) {
                    RelationshipDTO rel = new RelationshipDTO();
                    rel.setSourceClass(classInfo.getFullClassName());
                    rel.setTargetClass(parts[0]);
                    rel.setRelationshipType("INJECTS");
                    rel.setDescription(classInfo.getClassName() + " autowires " + parts[0]);
                    relationships.add(rel);
                }
            }
        }

        return relationships;
    }

    /**
     * Create enum info
     */
    private ClassInfoDTO createEnumInfo(EnumDeclaration enumDecl, String filePath, CompilationUnit cu) {
        ClassInfoDTO classInfo = new ClassInfoDTO();

        classInfo.setClassName(enumDecl.getNameAsString());
        classInfo.setFullClassName(getFullClassName(cu, enumDecl.getNameAsString()));
        classInfo.setClassType("enum");
        classInfo.setAccessModifier(getAccessModifier(enumDecl.getModifiers()));
        classInfo.setIsAbstract(false);
        classInfo.setIsFinal(true);
        classInfo.setFilePath(filePath);
        classInfo.setLineNumber(enumDecl.getBegin().map(pos -> pos.line).orElse(0));

        classInfo.setImplementsInterfaces(
                enumDecl.getImplementedTypes().stream()
                        .map(ClassOrInterfaceType::getNameAsString)
                        .collect(Collectors.toList())
        );

        List<MethodInfoDTO> methods = new ArrayList<>();
        enumDecl.getMethods().forEach(method -> methods.add(createMethodInfoWithSpring(method)));
        classInfo.setMethods(methods);

        List<String> fields = enumDecl.getEntries().stream()
                .map(entry -> entry.getNameAsString())
                .collect(Collectors.toList());
        classInfo.setFields(fields);

        classInfo.setAnnotations(extractAnnotations(enumDecl.getAnnotations()));
        classInfo.setAutowiredFields(new ArrayList<>());

        return classInfo;
    }

    /**
     * Extract fields from type declaration
     */
    private List<String> extractFields(TypeDeclaration<?> typeDecl) {
        return typeDecl.getFields().stream()
                .flatMap(field -> field.getVariables().stream())
                .map(var -> var.getTypeAsString() + " " + var.getNameAsString())
                .collect(Collectors.toList());
    }

    /**
     * Get access modifier from modifiers
     */
    private String getAccessModifier(com.github.javaparser.ast.NodeList<Modifier> modifiers) {
        for (Modifier modifier : modifiers) {
            if (modifier.getKeyword() == Modifier.Keyword.PUBLIC) return "public";
            if (modifier.getKeyword() == Modifier.Keyword.PRIVATE) return "private";
            if (modifier.getKeyword() == Modifier.Keyword.PROTECTED) return "protected";
        }
        return "default";
    }

    /**
     * Get full class name (package + class name)
     */
    private String getFullClassName(CompilationUnit cu, String className) {
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        return packageName.isEmpty() ? className : packageName + "." + className;
    }
}
