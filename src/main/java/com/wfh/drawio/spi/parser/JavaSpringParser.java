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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spring Boot 项目 AST 解析器
 * <p>
 * 设计原则：
 * 1. 每个 Spring Bean 类生成一个 ArchNode，layer + role 双字段精确描述归属
 * 2. 关系仅保留 Spring 注入产生的真实调用链（过滤构造器噪音）
 * 3. @JsonInclude(NON_NULL) 保证空字段不传给 LLM，节省 Token
 */
@Slf4j
public class JavaSpringParser implements LanguageParser {

    // ─── 映射表：注解名 → (layer, role) ──────────────────────────────────────

    /** 注解 → 架构层 */
    private static final Map<String, String> ANNOTATION_TO_LAYER = new LinkedHashMap<>();
    /** 注解 → 技术角色 */
    private static final Map<String, String> ANNOTATION_TO_ROLE = new LinkedHashMap<>();

    static {
        // 顺序很重要：RestController 在 Controller 前面（更具体的注解先匹配）
        ANNOTATION_TO_LAYER.put("RestController",  "API");
        ANNOTATION_TO_LAYER.put("Controller",      "API");
        ANNOTATION_TO_LAYER.put("FeignClient",     "API");
        ANNOTATION_TO_LAYER.put("Service",         "BIZ");
        ANNOTATION_TO_LAYER.put("Repository",      "DATA");
        ANNOTATION_TO_LAYER.put("Mapper",          "DATA");
        ANNOTATION_TO_LAYER.put("Entity",          "DATA");
        ANNOTATION_TO_LAYER.put("Table",           "DATA");
        ANNOTATION_TO_LAYER.put("TableName",       "DATA");
        ANNOTATION_TO_LAYER.put("Component",       "INFRA");
        ANNOTATION_TO_LAYER.put("Configuration",   "INFRA");
        ANNOTATION_TO_LAYER.put("ConfigurationProperties", "INFRA");

        ANNOTATION_TO_ROLE.put("RestController",   "CONTROLLER");
        ANNOTATION_TO_ROLE.put("Controller",       "CONTROLLER");
        ANNOTATION_TO_ROLE.put("FeignClient",      "FEIGN_CLIENT");
        ANNOTATION_TO_ROLE.put("Service",          "SERVICE");
        ANNOTATION_TO_ROLE.put("Repository",       "REPOSITORY");
        ANNOTATION_TO_ROLE.put("Mapper",           "MAPPER");
        ANNOTATION_TO_ROLE.put("Entity",           "ENTITY");
        ANNOTATION_TO_ROLE.put("Table",            "ENTITY");
        ANNOTATION_TO_ROLE.put("TableName",        "ENTITY");
        ANNOTATION_TO_ROLE.put("Component",        "COMPONENT");
        ANNOTATION_TO_ROLE.put("Configuration",    "CONFIG");
        ANNOTATION_TO_ROLE.put("ConfigurationProperties", "CONFIG");
    }

    // ─── 中间件检测：字段类型名/注解名 → 虚拟节点定义 ────────────────────────

    /**
     * 中间件虚拟节点定义。
     * key   = 虚拟节点唯一 ID（固定字符串，全局唯一）
     * value = ArchNode 描述（name, role, description）
     *
     * 当某个 Spring Bean 字段类型 或 类注解 匹配到 MIDDLEWARE_FIELD_TRIGGERS/MIDDLEWARE_ANNOTATION_TRIGGERS
     * 时，自动创建对应的虚拟节点，并建立 USES 关系。
     */
    private static final Map<String, MiddlewareDef> MIDDLEWARE_DEFS = new LinkedHashMap<>();

    /** 字段类型名（简单类名） → 中间件节点 ID */
    private static final Map<String, String> MIDDLEWARE_FIELD_TRIGGERS = new LinkedHashMap<>();

    /** 类/方法注解名 → 中间件节点 ID */
    private static final Map<String, String> MIDDLEWARE_ANNOTATION_TRIGGERS = new LinkedHashMap<>();

    static {
        // ════════════════ 缓存 ════════════════
        MIDDLEWARE_DEFS.put("middleware:redis", new MiddlewareDef("Redis", "REDIS", "缓存/分布式锁/Session"));
        for (String t : List.of("RedisTemplate", "StringRedisTemplate",
                "RedissonClient", "RedissonReactiveClient", "RedisCache", "ReactiveRedisTemplate")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:redis");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("Cacheable",    "middleware:redis");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("CacheEvict",   "middleware:redis");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("CachePut",     "middleware:redis");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableCaching", "middleware:redis");

        // ════════════════ 消息队列 ════════════════
        // RabbitMQ
        MIDDLEWARE_DEFS.put("middleware:rabbitmq", new MiddlewareDef("RabbitMQ", "RABBITMQ", "消息队列（AMQP）"));
        for (String t : List.of("RabbitTemplate", "AmqpTemplate", "AmqpAdmin", "RabbitAdmin")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:rabbitmq");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("RabbitListener", "middleware:rabbitmq");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("RabbitHandler",  "middleware:rabbitmq");

        // Kafka
        MIDDLEWARE_DEFS.put("middleware:kafka", new MiddlewareDef("Kafka", "KAFKA", "消息流/事件流"));
        for (String t : List.of("KafkaTemplate", "ProducerFactory", "ConsumerFactory",
                "KafkaAdmin", "KafkaListenerContainerFactory", "KafkaProducer", "KafkaConsumer")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:kafka");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("KafkaListener", "middleware:kafka");

        // RocketMQ
        MIDDLEWARE_DEFS.put("middleware:rocketmq", new MiddlewareDef("RocketMQ", "ROCKETMQ", "消息队列（阿里/开源）"));
        for (String t : List.of("RocketMQTemplate", "DefaultMQProducer", "DefaultMQPushConsumer",
                "DefaultMQPullConsumer", "TransactionMQProducer")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:rocketmq");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("RocketMQMessageListener", "middleware:rocketmq");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("ExtRocketMQTemplateConfiguration", "middleware:rocketmq");

        // ActiveMQ
        MIDDLEWARE_DEFS.put("middleware:activemq", new MiddlewareDef("ActiveMQ", "ACTIVEMQ", "消息队列（JMS）"));
        for (String t : List.of("JmsTemplate", "ActiveMQConnectionFactory", "ActiveMQQueue", "ActiveMQTopic")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:activemq");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("JmsListener", "middleware:activemq");

        // Pulsar
        MIDDLEWARE_DEFS.put("middleware:pulsar", new MiddlewareDef("Pulsar", "PULSAR", "消息流（Apache Pulsar）"));
        for (String t : List.of("PulsarTemplate", "PulsarClient", "PulsarProducer", "PulsarConsumer")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:pulsar");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("PulsarListener", "middleware:pulsar");

        // ════════════════ 搜索引擎 ════════════════
        MIDDLEWARE_DEFS.put("middleware:elasticsearch", new MiddlewareDef("Elasticsearch", "ELASTICSEARCH", "全文搜索/分析引擎"));
        for (String t : List.of("ElasticsearchRestTemplate", "ElasticsearchTemplate",
                "RestHighLevelClient", "ElasticsearchClient", "ElasticsearchOperations",
                "ReactiveElasticsearchTemplate")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:elasticsearch");
        }

        // ════════════════ NoSQL 数据库 ════════════════
        // MongoDB
        MIDDLEWARE_DEFS.put("middleware:mongodb", new MiddlewareDef("MongoDB", "MONGODB", "文档型数据库"));
        for (String t : List.of("MongoTemplate", "MongoClient", "ReactiveMongoTemplate",
                "MongoOperations", "ReactiveMongoOperations")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:mongodb");
        }

        // Cassandra
        MIDDLEWARE_DEFS.put("middleware:cassandra", new MiddlewareDef("Cassandra", "CASSANDRA", "宽列式 NoSQL 数据库"));
        for (String t : List.of("CassandraTemplate", "CqlSession", "CassandraOperations",
                "ReactiveCassandraTemplate")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:cassandra");
        }

        // Neo4j
        MIDDLEWARE_DEFS.put("middleware:neo4j", new MiddlewareDef("Neo4j", "NEO4J", "图数据库"));
        for (String t : List.of("Neo4jTemplate", "Driver", "Neo4jClient", "ReactiveNeo4jTemplate")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:neo4j");
        }

        // InfluxDB
        MIDDLEWARE_DEFS.put("middleware:influxdb", new MiddlewareDef("InfluxDB", "INFLUXDB", "时序数据库"));
        for (String t : List.of("InfluxDB", "InfluxDBClient", "WriteApi", "QueryApi")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:influxdb");
        }

        // ════════════════ 对象存储 ════════════════
        MIDDLEWARE_DEFS.put("middleware:objectstorage", new MiddlewareDef("Object Storage", "OBJECT_STORAGE", "对象存储（MinIO/OSS/S3）"));
        for (String t : List.of("MinioClient", "OSSClient", "AmazonS3", "S3Client",
                "CosClient", "BosClient", "ObsClient")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:objectstorage");
        }

        // ════════════════ 注册中心 / 配置中心 ════════════════
        // Nacos
        MIDDLEWARE_DEFS.put("middleware:nacos", new MiddlewareDef("Nacos", "NACOS", "注册中心/配置中心"));
        for (String t : List.of("NamingService", "ConfigService", "NacosDiscoveryClient",
                "NacosConfigManager", "NacosTemplate")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:nacos");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("NacosValue",           "middleware:nacos");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("NacosConfigListener",  "middleware:nacos");

        // Apollo
        MIDDLEWARE_DEFS.put("middleware:apollo", new MiddlewareDef("Apollo", "APOLLO", "配置中心（携程开源）"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("ApolloConfig",         "middleware:apollo");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("ApolloConfigChangeListener", "middleware:apollo");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableApolloConfig",   "middleware:apollo");

        // Zookeeper
        MIDDLEWARE_DEFS.put("middleware:zookeeper", new MiddlewareDef("Zookeeper", "ZOOKEEPER", "分布式协调/注册中心"));
        for (String t : List.of("ZooKeeper", "CuratorFramework", "ZookeeperTemplate",
                "ZookeeperDiscoveryClient")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:zookeeper");
        }

        // Consul
        MIDDLEWARE_DEFS.put("middleware:consul", new MiddlewareDef("Consul", "CONSUL", "注册中心/配置中心"));
        for (String t : List.of("ConsulClient", "ConsulDiscoveryClient", "ConsulConfigWatch")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:consul");
        }

        // Etcd
        MIDDLEWARE_DEFS.put("middleware:etcd", new MiddlewareDef("Etcd", "ETCD", "分布式 KV 存储/注册中心"));
        for (String t : List.of("EtcdClient", "Client")) {  // jetcd 的主客户端
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:etcd");
        }

        // ════════════════ 任务调度 ════════════════
        // XXL-Job
        MIDDLEWARE_DEFS.put("middleware:xxljob", new MiddlewareDef("XXL-Job", "XXL_JOB", "分布式任务调度"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("XxlJob", "middleware:xxljob");
        for (String t : List.of("XxlJobSpringExecutor", "XxlJobHelper")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:xxljob");
        }

        // Quartz
        MIDDLEWARE_DEFS.put("middleware:quartz", new MiddlewareDef("Quartz", "QUARTZ", "本地/集群任务调度"));
        for (String t : List.of("Scheduler", "SchedulerFactory", "QuartzJobBean")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:quartz");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("Scheduled",       "middleware:quartz");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableScheduling", "middleware:quartz");

        // Elastic-Job
        MIDDLEWARE_DEFS.put("middleware:elasticjob", new MiddlewareDef("Elastic-Job", "ELASTIC_JOB", "分布式弹性任务调度"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("ElasticSimpleJob",   "middleware:elasticjob");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("ElasticDataflowJob", "middleware:elasticjob");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("ElasticScriptJob",   "middleware:elasticjob");

        // ════════════════ 限流 / 熔断 ════════════════
        // Sentinel
        MIDDLEWARE_DEFS.put("middleware:sentinel", new MiddlewareDef("Sentinel", "SENTINEL", "流量控制/熔断降级"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SentinelResource", "middleware:sentinel");
        for (String t : List.of("SphU", "SphO", "FlowRuleManager", "DegradeRuleManager")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:sentinel");
        }

        // Resilience4j
        MIDDLEWARE_DEFS.put("middleware:resilience4j", new MiddlewareDef("Resilience4j", "RESILIENCE4J", "容错/限流/熔断"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("CircuitBreaker",  "middleware:resilience4j");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("RateLimiter",     "middleware:resilience4j");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("Retry",           "middleware:resilience4j");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("Bulkhead",        "middleware:resilience4j");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("TimeLimiter",     "middleware:resilience4j");

        // Hystrix（遗留项目）
        MIDDLEWARE_DEFS.put("middleware:hystrix", new MiddlewareDef("Hystrix", "HYSTRIX", "熔断降级（Netflix）"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("HystrixCommand",      "middleware:hystrix");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableHystrix",       "middleware:hystrix");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableCircuitBreaker", "middleware:hystrix");

        // ════════════════ 分布式事务 ════════════════
        // Seata
        MIDDLEWARE_DEFS.put("middleware:seata", new MiddlewareDef("Seata", "SEATA", "分布式事务"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("GlobalTransactional", "middleware:seata");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("GlobalLock",          "middleware:seata");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("TwoPhaseBusinessAction", "middleware:seata");
        for (String t : List.of("GlobalTransaction", "UndoLogManager")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:seata");
        }

        // ════════════════ RPC 框架 ════════════════
        // Dubbo
        MIDDLEWARE_DEFS.put("middleware:dubbo", new MiddlewareDef("Dubbo", "DUBBO", "RPC 框架（阿里/Apache）"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("DubboService",    "middleware:dubbo");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("DubboReference",  "middleware:dubbo");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("Service",         "middleware:dubbo");  // 老版注解（与Spring冲突，ANNOTATION_TO_LAYER优先）
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("Reference",       "middleware:dubbo");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableDubbo",     "middleware:dubbo");

        // gRPC
        MIDDLEWARE_DEFS.put("middleware:grpc", new MiddlewareDef("gRPC", "GRPC", "高性能 RPC（Protobuf）"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("GrpcService",    "middleware:grpc");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("GrpcClient",     "middleware:grpc");
        for (String t : List.of("ManagedChannel", "ManagedChannelBuilder", "ServerBuilder")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:grpc");
        }

        // ════════════════ HTTP 客户端 ════════════════
        // OpenFeign
        MIDDLEWARE_DEFS.put("middleware:feign", new MiddlewareDef("OpenFeign", "FEIGN", "声明式 HTTP 客户端"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("FeignClient",    "middleware:feign");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableFeignClients", "middleware:feign");

        // WebClient / RestTemplate (外部 HTTP)
        MIDDLEWARE_DEFS.put("middleware:httpclient", new MiddlewareDef("HTTP Client", "HTTP_CLIENT", "外部 HTTP 调用"));
        for (String t : List.of("WebClient", "RestTemplate", "HttpClient",
                "OkHttpClient", "CloseableHttpClient")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:httpclient");
        }

        // ════════════════ WebSocket ════════════════
        MIDDLEWARE_DEFS.put("middleware:websocket", new MiddlewareDef("WebSocket", "WEBSOCKET", "全双工实时通信"));
        for (String t : List.of("SimpMessagingTemplate", "WebSocketSession",
                "WebSocketHandler", "WebSocketClient")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:websocket");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("ServerEndpoint",          "middleware:websocket");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableWebSocket",         "middleware:websocket");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableWebSocketMessageBroker", "middleware:websocket");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("MessageMapping",          "middleware:websocket");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SubscribeMapping",        "middleware:websocket");

        // ════════════════ 认证 / 安全 ════════════════
        MIDDLEWARE_DEFS.put("middleware:security", new MiddlewareDef("Spring Security", "SECURITY", "认证/鉴权"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableWebSecurity",    "middleware:security");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("EnableMethodSecurity", "middleware:security");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("PreAuthorize",         "middleware:security");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("PostAuthorize",        "middleware:security");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("Secured",              "middleware:security");
        for (String t : List.of("JwtTokenProvider", "JwtUtil", "TokenStore",
                "AuthorizationServerConfigurer", "ResourceServerConfigurer")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:security");
        }

        // Sa-Token
        MIDDLEWARE_DEFS.put("middleware:satoken", new MiddlewareDef("Sa-Token", "SA_TOKEN", "轻量级权限认证"));
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SaCheckLogin",      "middleware:satoken");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SaCheckPermission", "middleware:satoken");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SaCheckRole",       "middleware:satoken");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SaIgnore",          "middleware:satoken");
        for (String t : List.of("StpUtil", "SaManager", "SaTokenConfig")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:satoken");
        }

        // ════════════════ 监控 / 链路追踪 ════════════════
        // Micrometer / Prometheus
        MIDDLEWARE_DEFS.put("middleware:metrics", new MiddlewareDef("Metrics", "METRICS", "指标监控（Micrometer/Prometheus）"));
        for (String t : List.of("MeterRegistry", "Counter", "Timer", "Gauge",
                "PrometheusMeterRegistry")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:metrics");
        }

        // Sleuth / SkyWalking / Zipkin
        MIDDLEWARE_DEFS.put("middleware:tracing", new MiddlewareDef("Tracing", "TRACING", "分布式链路追踪"));
        for (String t : List.of("Tracer", "Span", "SpanBuilder", "Tracing")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:tracing");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("NewSpan",   "middleware:tracing");
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SpanTag",   "middleware:tracing");

        // ════════════════ 短信 / 邮件 / 通知推送 ════════════════
        MIDDLEWARE_DEFS.put("middleware:notification", new MiddlewareDef("Notification", "NOTIFICATION", "短信/邮件/消息推送"));
        for (String t : List.of("JavaMailSender", "JavaMailSenderImpl", "MailMessage",
                "SmsClient", "AliyunSmsClient", "TencentSmsClient")) {
            MIDDLEWARE_FIELD_TRIGGERS.put(t, "middleware:notification");
        }
        MIDDLEWARE_ANNOTATION_TRIGGERS.put("SendTo", "middleware:notification");
    }

    /** 中间件虚拟节点定义（不可变值对象） */
    private static class MiddlewareDef {
        final String name;
        final String role;
        final String description;
        MiddlewareDef(String name, String role, String description) {
            this.name = name; this.role = role; this.description = description;
        }
    }

    /**
     * 类名后缀黑名单：这些类是辅助类，不是架构主体，不出现在图中。
     * 注意：故意 **不** 包含 "Config"，因为 @Configuration 类是重要的 INFRA 节点。
     */
    private static final Set<String> IGNORED_SUFFIXES = Set.of(
            "Test", "Tests", "DTO", "VO", "Request", "Response",
            "Exception", "Constant", "Utils", "Properties", "Helper", "Util"
    );

    /** 第三方包前缀黑名单：这些包的类不建立 DEPENDS_ON 节点（中间件客户端已由虚拟节点表示） */
    private static final Set<String> IGNORED_PACKAGES = Set.of(
            // JDK / Jakarta
            "java.", "javax.", "jakarta.",
            // Spring 框架本身
            "org.springframework.", "org.slf4j.", "org.apache.",
            // ORM / 数据库框架
            "com.baomidou.", "com.mybatis.", "org.mybatis.",
            // 工具库
            "lombok.", "com.fasterxml.", "com.google.guava.",
            "cn.hutool.", "org.apache.commons.",
            // Redis
            "org.redisson.", "io.lettuce.", "redis.clients.",
            // MQ
            "com.rabbitmq.", "org.apache.kafka.", "org.apache.rocketmq.",
            "com.aliyun.openservices.ons.", "org.apache.activemq.",
            "org.apache.pulsar.",
            // 搜索
            "org.elasticsearch.", "co.elastic.",
            // 对象存储
            "io.minio.", "com.aliyun.oss.", "software.amazon.awssdk.", "com.qcloud.cos.",
            // 注册/配置中心
            "com.alibaba.nacos.", "com.ctrip.framework.apollo.",
            "org.apache.curator.", "org.apache.zookeeper.",
            "com.ecwid.consul.", "io.etcd.",
            // RPC
            "io.grpc.",
            // 监控
            "io.micrometer.", "io.prometheus.",
            // 安全
            "org.springframework.security."
    );

    // ─── LanguageParser 接口实现 ──────────────────────────────────────────────

    @Override
    public String getName() {
        return "Java-Spring";
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
        log.info("Starting Spring Boot AST analysis: {}", projectDir);
        JavaParser javaParser = initializeSymbolSolver(projectDir);
        List<ArchNode> rawNodes = new ArrayList<>();
        List<ArchRelationship> rawRelationships = new ArrayList<>();
        Set<String> processedClasses = new HashSet<>();
        // 收集本次实际用到的中间件 ID（避免重复创建虚拟节点）
        Set<String> usedMiddlewareIds = new LinkedHashSet<>();

        try {
            for (Path javaFile : findJavaFiles(projectDir)) {
                try {
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) continue;

                    String packageName = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString()).orElse("");

                    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                        String className = clazz.getNameAsString();
                        if (isIgnoredClass(className)) return;

                        String fullName = getFullyQualifiedName(clazz, packageName);
                        if (processedClasses.contains(fullName)) return;
                        processedClasses.add(fullName);

                        ArchNode node = analyzeClass(clazz, fullName, packageName);
                        // 只收录有 layer 的节点（即有 Spring 注解的类）
                        if (node != null) {
                            rawNodes.add(node);
                            rawRelationships.addAll(
                                analyzeRelationships(clazz, fullName, cu, usedMiddlewareIds));
                        }
                    });

                } catch (Exception e) {
                    log.warn("Parse error [{}]: {}", javaFile.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("IO Error scanning project dir", e);
        }

        return buildResult(rawNodes, rawRelationships, usedMiddlewareIds);
    }

    // ─── 核心分析：类 → ArchNode ─────────────────────────────────────────────

    private ArchNode analyzeClass(ClassOrInterfaceDeclaration clazz, String id, String packageName) {
        List<String> annotations = clazz.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .collect(Collectors.toList());

        // 同时支持接口名以 "Mapper" 结尾的 MyBatis Mapper（无 @Mapper 注解的情况）
        if (clazz.isInterface() && clazz.getNameAsString().endsWith("Mapper")) {
            annotations.add("Mapper");
        }

        // 按 ANNOTATION_TO_LAYER 顺序匹配，找到第一个有 Spring 注解的
        String layer = null;
        String role = null;
        for (Map.Entry<String, String> entry : ANNOTATION_TO_LAYER.entrySet()) {
            String annotationKey = entry.getKey();
            if (annotations.stream().anyMatch(a -> a.equals(annotationKey) || a.contains(annotationKey))) {
                layer = entry.getValue();
                role = ANNOTATION_TO_ROLE.get(annotationKey);
                break;
            }
        }

        // 没有 Spring 注解的类不生成节点（纯 POJO、工具类等）
        if (layer == null) return null;

        ArchNode node = new ArchNode();
        node.setId(id);
        node.setName(clazz.getNameAsString());
        node.setLayer(layer);
        node.setRole(role);
        node.setPackageName(packageName);

        // Javadoc 注释（仅取第一段有效描述，遇到 @tag 停止）
        String comment = extractClassComment(clazz);
        if (!comment.isEmpty()) {
            node.setDescription(comment);
        }

        // API 路由（仅 Controller 类）
        if ("API".equals(layer) && ("CONTROLLER".equals(role) || "FEIGN_CLIENT".equals(role))) {
            List<String> routes = extractApiRoutes(clazz);
            if (!routes.isEmpty()) {
                node.setApiRoutes(routes);
            }
        }

        // 数据库表名（仅 Entity 类）
        if ("ENTITY".equals(role)) {
            extractTableName(clazz).ifPresent(node::setTableName);
        }

        return node;
    }

    // ─── 关系分析 ─────────────────────────────────────────────────────────────

    private List<ArchRelationship> analyzeRelationships(ClassOrInterfaceDeclaration clazz,
                                                          String sourceId, CompilationUnit cu,
                                                          Set<String> usedMiddlewareIds) {
        List<ArchRelationship> rels = new ArrayList<>();

        // 1. 继承关系
        clazz.getExtendedTypes().forEach(t ->
                addRel(rels, sourceId, resolveClassOrInterfaceType(t, cu), "EXTENDS"));

        // 2. 接口实现
        clazz.getImplementedTypes().forEach(t ->
                addRel(rels, sourceId, resolveClassOrInterfaceType(t, cu), "IMPLEMENTS"));

        // 3. 字段注入（@Autowired / @Resource / @Inject）
        clazz.getFields().forEach(field -> {
            String fieldTypeName = field.getElementType().isClassOrInterfaceType()
                    ? field.getElementType().asClassOrInterfaceType().getNameAsString() : "";

            // 3a. 优先检测是否是中间件客户端类型（无需注解）
            String middlewareId = MIDDLEWARE_FIELD_TRIGGERS.get(fieldTypeName);
            if (middlewareId != null) {
                usedMiddlewareIds.add(middlewareId);
                addRel(rels, sourceId, middlewareId, "USES");
                return; // 不再作为普通 DEPENDS_ON 关系处理
            }

            // 3b. 普通 Spring 注入字段
            if (field.isAnnotationPresent("Autowired")
                    || field.isAnnotationPresent("Resource")
                    || field.isAnnotationPresent("Inject")) {
                field.getVariables().forEach(v -> {
                    List<String> targets = resolveTypeWithGenerics(field.getElementType(), cu);
                    targets.forEach(t -> addRel(rels, sourceId, t, "DEPENDS_ON"));
                });
            }
        });

        // 4. 构造器注入 —— **仅** 处理带 @Autowired 的构造器，避免普通 POJO 构造器噪音
        clazz.getConstructors().forEach(constructor -> {
            if (constructor.isAnnotationPresent("Autowired")) {
                constructor.getParameters().forEach(param -> {
                    List<String> targets = resolveTypeWithGenerics(param.getType(), cu);
                    targets.forEach(t -> addRel(rels, sourceId, t, "DEPENDS_ON"));
                });
            }
        });

        // 5. 类级别/方法级别注解中间件检测（MQ 消费者类：@RabbitListener、@KafkaListener 等在方法上）
        List<String> allAnnotations = new ArrayList<>();
        clazz.getAnnotations().stream().map(AnnotationExpr::getNameAsString).forEach(allAnnotations::add);
        clazz.getMethods().forEach(m ->
                m.getAnnotations().stream().map(AnnotationExpr::getNameAsString).forEach(allAnnotations::add));

        allAnnotations.forEach(ann -> {
            String mwId = MIDDLEWARE_ANNOTATION_TRIGGERS.get(ann);
            if (mwId != null) {
                usedMiddlewareIds.add(mwId);
                addRel(rels, sourceId, mwId, "USES");
            }
        });

        return rels;
    }

    /**
     * 解析类型，支持泛型拆包：
     * - 普通类型: UserService → "com.example.UserService"
     * - 泛型容器: List<UserService> → ["com.example.UserService"]
     * - Optional<UserService> → ["com.example.UserService"]
     */
    private List<String> resolveTypeWithGenerics(com.github.javaparser.ast.type.Type type,
                                                  CompilationUnit cu) {
        List<String> results = new ArrayList<>();
        if (!type.isClassOrInterfaceType()) return results;

        ClassOrInterfaceType cit = type.asClassOrInterfaceType();
        String simpleName = cit.getNameAsString();

        // 如果是已知泛型容器，解包类型参数
        if (Set.of("List", "Set", "Collection", "Optional", "Map").contains(simpleName)) {
            cit.getTypeArguments().ifPresent(args ->
                    args.forEach(arg -> {
                        if (arg.isClassOrInterfaceType()) {
                            String resolved = resolveClassOrInterfaceType(arg.asClassOrInterfaceType(), cu);
                            if (resolved != null) results.add(resolved);
                        }
                    })
            );
        } else {
            // 普通类型直接解析
            String resolved = resolveClassOrInterfaceType(cit, cu);
            if (resolved != null) results.add(resolved);
        }
        return results;
    }

    // ─── 结果后处理 ──────────────────────────────────────────────────────────

    private ProjectAnalysisResult buildResult(List<ArchNode> nodes, List<ArchRelationship> rels,
                                               Set<String> usedMiddlewareIds) {
        // 1. 为实际用到的中间件创建虚拟节点
        List<ArchNode> allNodes = new ArrayList<>(nodes);
        for (String mwId : usedMiddlewareIds) {
            MiddlewareDef def = MIDDLEWARE_DEFS.get(mwId);
            if (def == null) continue;
            ArchNode mwNode = new ArchNode();
            mwNode.setId(mwId);
            mwNode.setName(def.name);
            mwNode.setLayer("MIDDLEWARE");
            mwNode.setRole(def.role);
            mwNode.setDescription(def.description);
            allNodes.add(mwNode);
        }

        Set<String> validIds = allNodes.stream().map(ArchNode::getId).collect(Collectors.toSet());

        // 2. 过滤：起点和终点都必须是已知节点（含中间件虚拟节点），且不是自环
        List<ArchRelationship> cleanRels = rels.stream()
                .filter(r -> validIds.contains(r.getSourceId()) && validIds.contains(r.getTargetId()))
                .filter(r -> !r.getSourceId().equals(r.getTargetId()))
                // 去除重复关系（相同 src+target+type 只保留一条）
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(
                                Comparator.comparing(r -> r.getSourceId() + "|" + r.getTargetId() + "|" + r.getType())
                        )),
                        ArrayList::new
                ));

        // 3. 收集实际出现的 layer，排序后给 LLM 布局参考
        List<String> layers = allNodes.stream()
                .map(ArchNode::getLayer)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingInt(this::layerOrder))
                .collect(Collectors.toList());

        ProjectAnalysisResult result = new ProjectAnalysisResult();
        result.setNodes(allNodes);
        result.setRelationships(cleanRels);
        result.setLayers(layers);
        // framework 已在 ProjectAnalysisResult 中有默认值 "Spring Boot"
        return result;
    }

    /** Layer 排序权重：API → BIZ → DATA → INFRA */
    private int layerOrder(String layer) {
        switch (layer) {
            case "API":        return 0;
            case "BIZ":        return 1;
            case "DATA":       return 2;
            case "INFRA":      return 3;
            case "MIDDLEWARE": return 4;  // 外部中间件虚拟节点，排在最外围
            default:           return 9;
        }
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private String extractClassComment(ClassOrInterfaceDeclaration clazz) {
        return clazz.getComment()
                .map(Comment::getContent)
                .map(this::cleanJavadoc)
                .orElse("");
    }

    private String cleanJavadoc(String content) {
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            String clean = line.trim().replaceAll("^\\*+\\s?", "").trim();
            if (clean.startsWith("@")) break;  // 遇到 @author/@param 等停止
            if (!clean.isEmpty()) sb.append(clean).append(" ");
        }
        return sb.toString().trim();
    }

    private List<String> extractApiRoutes(ClassOrInterfaceDeclaration clazz) {
        List<String> routes = new ArrayList<>();
        String basePath = clazz.getAnnotationByName("RequestMapping")
                .map(this::extractPath).orElse("");
        String finalBase = basePath;
        clazz.getMethods().forEach(m -> {
            for (String mappingType : List.of("GetMapping", "PostMapping", "PutMapping",
                    "DeleteMapping", "PatchMapping", "RequestMapping")) {
                m.getAnnotationByName(mappingType).ifPresent(ann -> {
                    String methodPath = extractPath(ann);
                    String verb = mappingType.replace("Mapping", "").toUpperCase();
                    if ("REQUEST".equals(verb)) verb = "ALL";
                    String fullPath = (finalBase + methodPath).replace("//", "/");
                    routes.add("[" + verb + "] " + fullPath);
                });
            }
        });
        return routes;
    }

    private String extractPath(AnnotationExpr ann) {
        if (ann.isSingleMemberAnnotationExpr()) {
            return ann.asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
        } else if (ann.isNormalAnnotationExpr()) {
            return ann.asNormalAnnotationExpr().getPairs().stream()
                    .filter(p -> "value".equals(p.getNameAsString()) || "path".equals(p.getNameAsString()))
                    .findFirst().map(p -> p.getValue().toString().replace("\"", "")).orElse("");
        }
        return "";
    }

    private Optional<String> extractTableName(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("TableName").map(this::extractPath)
                .filter(s -> !s.isEmpty())
                .or(() -> clazz.getAnnotationByName("Table").map(this::extractPath).filter(s -> !s.isEmpty()))
                .or(() -> {
                    // fallback：类名驼峰转下划线，如 UserInfo → user_info
                    String name = clazz.getNameAsString().replace("Entity", "");
                    return Optional.of(camelToSnake(name));
                });
    }

    private String camelToSnake(String camel) {
        return camel.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("^_", "");
    }

    private boolean isIgnoredClass(String className) {
        return IGNORED_SUFFIXES.stream().anyMatch(className::endsWith);
    }

    private boolean isIgnoredPackage(String typeName) {
        if (typeName == null || typeName.isBlank()) return true;
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

    private String resolveClassOrInterfaceType(ClassOrInterfaceType type, CompilationUnit cu) {
        try {
            return type.resolve().asReferenceType().getQualifiedName();
        } catch (Exception e) {
            return inferFromImports(type.getNameAsString(), cu);
        }
    }

    private String inferFromImports(String simpleName, CompilationUnit cu) {
        return cu.getImports().stream()
                .filter(i -> !i.isAsterisk() && i.getNameAsString().endsWith("." + simpleName))
                .findFirst()
                .map(ImportDeclaration::getNameAsString)
                .orElseGet(() -> {
                    String pkg = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString()).orElse("");
                    return pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
                });
    }

    private String getFullyQualifiedName(ClassOrInterfaceDeclaration clazz, String packageName) {
        return packageName.isEmpty() ? clazz.getNameAsString()
                : packageName + "." + clazz.getNameAsString();
    }

    private JavaParser initializeSymbolSolver(String projectDir) {
        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        Path srcPath = Paths.get(projectDir, "src", "main", "java");
        if (Files.exists(srcPath)) {
            solver.add(new JavaParserTypeSolver(srcPath));
        } else {
            solver.add(new JavaParserTypeSolver(new File(projectDir)));
        }
        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(solver));
        return new JavaParser(config);
    }

    private List<Path> findJavaFiles(String projectPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }
}