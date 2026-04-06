# CLAUDE.md - drawio-backend 项目指南

## 项目概述

drawio-backend 是一个在线图表绘制平台的后端服务，支持图表管理、实时协作编辑、AI 生成图表等功能。

- **框架**: Spring Boot 3.5.9
- **Java 版本**: Java 21
- **构建工具**: Maven

## 技术栈

| 类别 | 技术 |
|-----|------|
| ORM | MyBatis-Plus 3.5.16 |
| 主数据库 | MySQL |
| 向量数据库 | PostgreSQL + PGVector |
| 缓存 | Redis + Spring Session |
| 分布式工具 | Redisson (限流、分布式锁) |
| 本地缓存 | Caffeine |
| AI 集成 | Spring AI 1.1.2 (OpenAI 兼容) |
| 认证 | Spring Security + OAuth2 (GitHub) + Session |
| 实时通信 | WebSocket (Y.js 协作协议) |
| 对象存储 | MinIO / AWS S3 |
| API 文档 | Knife4j / OpenAPI 3 |

## 包结构

```
com.wfh.drawio
├── controller/          # REST API 控制器
├── service/             # 业务逻辑接口
│   └── impl/            # 业务逻辑实现
├── mapper/              # MyBatis 数据访问层
├── model/
│   ├── entity/          # 数据库实体类
│   ├── dto/             # 数据传输对象 (请求)
│   ├── vo/              # 视图对象 (响应)
│   └── enums/           # 枚举类型
├── ai/                  # AI 模块
│   ├── client/          # AI 客户端 (DrawClient)
│   ├── advisor/         # AI Advisor
│   ├── chatmemory/      # 会话记忆管理
│   ├── rag/             # RAG 检索增强
│   ├── tools/           # AI Function Tools
│   └── config/          # AI 配置 (多模型工厂)
├── ws/                  # WebSocket 模块
│   ├── config/          # WebSocket 配置
│   ├── handler/         # YjsHandler
│   ├── interceptor/     # 认证握手拦截器
│   └── service/         # WebSocket 服务
├── security/            # 安全模块
│   ├── handler/         # 登录成功/失败处理器
│   └── UserDetailsServiceImpl
├── aop/                 # 切面
│   ├── RateLimitAspect  # 分布式限流
│   ├── AiCircuitBreakerAspect # AI 熔断
│   └── AuthInterceptor  # 认证拦截
├── annotation/          # 自定义注解
│   ├── RateLimit        # 限流注解
│   ├── AiFeature        # AI 功能标记
│   └── AuthCheck        # 权限检查
├── config/              # 配置类
│   ├── DataSourceConfig # 多数据源配置
│   ├── RedisConfig      # Redis 配置
│   ├── SecurityConfig   # Spring Security 配置
│   └── CorsConfig       # CORS 配置
├── exception/           # 异常处理
├── common/              # 公共类 (ErrorCode, BaseResponse)
├── manager/             # 管理器 (RustFsManager 文件操作)
├── scheduler/           # 定时任务
├── constant/            # 常量
└── core/                # 核心模型 (代码解析)
```

## 核心业务实体

| 实体 | 说明 |
|-----|------|
| User | 用户 |
| Space | 空间/项目 |
| SpaceUser | 空间成员 |
| Diagram | 图表 |
| DiagramRoom | 协作房间 |
| RoomMember | 房间成员 |
| RoomSnapshots | 房间快照 |
| RoomUpdates | 房间更新记录 |
| Material | 素材 |
| Announcement | 公告 |
| Feedback | 反馈 |
| SysRole / SysAuthority | RBAC 权限系统 |

## API 设计规范

### RESTful 端点

```
POST   /diagram/add           # 创建图表
POST   /diagram/edit          # 编辑图表
POST   /diagram/delete        # 删除图表
POST   /diagram/list/page/vo  # 分页查询
GET    /diagram/get/vo        # 获取单条
```

### 响应格式

所有 API 使用统一的响应包装类 `BaseResponse<T>`:

```java
{
  "code": 0,          // 状态码 (0=成功)
  "data": T,          // 业务数据
  "message": "ok"     // 提示信息
}
```

### 错误码

定义在 `ErrorCode` 枚举:
- `SUCCESS(0)` - 成功
- `PARAMS_ERROR(40000)` - 参数错误
- `NOT_LOGIN_ERROR(40100)` - 未登录
- `NO_AUTH_ERROR(40300)` - 无权限
- `NOT_FOUND_ERROR(40400)` - 资源不存在
- `SYSTEM_ERROR(50000)` - 系统错误
- `TOO_MANY_REQUEST(42900)` - 限流

## 开发规范

### 代码风格

- 使用 Lombok 简化 POJO (`@Data`, `@Slf4j`)
- 使用 MyBatis-Plus 注解 (`@TableName`, `@TableId`, `@TableLogic`)
- DTO 用于接收请求参数，VO 用于返回响应
- Service 接口 + Impl 实现类分离

### 限流使用

```java
@RateLimit(key = "ai:generate", rate = 10, rateInterval = 60, limitType = RateLimitType.USER)
public void aiGenerateDiagram() { ... }
```

限流类型:
- `USER` - 用户级别
- `IP` - IP 级别
- `API` - 接口级别

### 权限控制

使用 Spring Security 注解:

```java
@PreAuthorize("hasAuthority('diagram:create')")
public BaseResponse<Long> addDiagram(...) { ... }
```

或自定义注解:

```java
@AuthCheck(checkSpace = true, authority = "diagram:edit")
public BaseResponse<Boolean> editDiagram(...) { ... }
```

### AI 流式响应

使用 SSE (Server-Sent Events):

```java
@GetMapping("/ai/chat/stream")
public SseEmitter chatStream(@RequestParam String message) {
    return aiService.getSseEmitter(request, drawClient);
}
```

### WebSocket 协作

端点: `/yjs/{roomId}`

认证: 通过 `AuthHandshakeInterceptor` 验证 session

协议: Y.js CRDT 协作协议

## 数据源配置

项目支持多数据源:

1. **MySQL** - 主业务数据
2. **PostgreSQL** - 向量存储 (PGVector)

配置类: `DataSourceConfig`, `MysqlDataSourceProperties`, `PostgresDataSourceProperties`

## 文件存储

使用 Rust 文件服务 (通过 HTTP 调用):

```java
@Resource
private RustFsManager rustFsManager;

// 上传文件
rustFsManager.uploadFile(file, path);

// 获取文件 URL
rustFsManager.getFileUrl(path);
```

## 常用命令

### 启动项目

```bash
mvn spring-boot:run
```

### 构建

```bash
mvn clean package -DskipTests
```

### 运行测试

```bash
mvn test
```

## 注意事项

1. **认证方式**: 使用 Session + Spring Security，非 JWT
2. **限流实现**: 基于 Redisson 分布式限流器
3. **AI 调用**: 支持自定义 baseUrl/apiKey，默认使用系统配置
4. **协作编辑**: 使用 WebSocket + Y.js，需要先获取锁才能上传快照
5. **日志**: 使用 `@Slf4j` + Lombok
6. **异常处理**: 统一使用 `BusinessException` + `ErrorCode`