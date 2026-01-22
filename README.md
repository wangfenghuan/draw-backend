# Draw.io 后端系统

一个基于 Spring Boot 的在线绘图协作平台后端系统，提供类似 draw.io 的团队协作绘图功能，支持 AI 辅助绘图和多人实时协作。

## 项目简介

本项目是一个功能完整的在线绘图平台后端系统，主要特性包括：

- 🎨 **多种绘图方式**：支持手动绘图、AI 辅助生成图表
- 👥 **多人实时协作**：基于 WebSocket 的多人实时编辑功能
- 🤖 **AI 智能助手**：集成 Spring AI + OpenAI，支持创建、编辑、追加图表内容
- 📁 **空间管理**：支持团队空间，多级权限控制
- 🔐 **权限管理**：完整的 RBAC 权限模型，细粒度权限控制
- 💾 **对象存储**：集成 MinIO/S3，支持文件上传和管理
- 📄 **格式转换**：支持 PNG、SVG、XML 等多种格式导出

## 技术栈

### 核心框架
- **Spring Boot 3.5.9** - 基础框架
- **Java 21** - 开发语言
- **Maven** - 项目管理

### 数据层
- **MyBatis Plus 3.5.15** - ORM 框架
- **MySQL** - 关系型数据库
- **Redis** - 缓存、会话存储、分布式锁
- **Redisson 3.52.0** - Redis 客户端
- **Caffeine 3.1.8** - 本地缓存

### 安全与会话
- **Spring Security** - 安全框架
- **Spring Session** - 分布式会话管理（Redis 存储）

### AI 与文件处理
- **Spring AI 1.1.2** - AI 集成框架（OpenAI）
- **MinIO 8.5.17** - 对象存储
- **AWS S3 SDK 2.25.27** - S3 协议支持

### 实时通信
- **WebSocket** - 实时双向通信
- **Spring WebSocket** - WebSocket 支持

### 工具库
- **Hutool 5.8.38** - Java 工具集
- **Lombok** - 简化代码
- **Knife4j 4.5.0** - API 文档
- **Springdoc OpenAPI 2.8.9** - OpenAPI 规范
- **Apache Commons Lang3 3.19.0** - 通用工具
- **FreeMarker 2.3.34** - 模板引擎

## 项目结构

```
drawio-backend/
├── src/main/java/com/wfh/drawio/
│   ├── controller/          # API 接口层
│   │   ├── UserController.java           # 用户接口
│   │   ├── SpaceController.java          # 空间接口
│   │   ├── DiagramController.java        # 图表接口
│   │   ├── RoomController.java           # 协作房间接口
│   │   ├── AIClientController.java       # AI 客户端接口
│   │   ├── ConversionController.java     # 格式转换接口
│   │   └── FileController.java           # 文件上传接口
│   │
│   ├── service/             # 业务逻辑层
│   │   ├── UserService.java              # 用户服务
│   │   ├── SpaceService.java             # 空间服务
│   │   ├── DiagramService.java           # 图表服务
│   │   ├── DiagramRoomService.java       # 房间服务
│   │   ├── AiService.java                # AI 服务
│   │   ├── ConversionService.java        # 转换服务
│   │   └── ...                            # 其他业务服务
│   │
│   ├── mapper/              # 数据访问层
│   │   ├── UserMapper.java
│   │   ├── SpaceMapper.java
│   │   ├── DiagramMapper.java
│   │   └── ...
│   │
│   ├── model/               # 数据模型
│   │   ├── entity/          # 数据库实体
│   │   │   ├── User.java                    # 用户实体
│   │   │   ├── Space.java                   # 空间实体
│   │   │   ├── Diagram.java                 # 图表实体
│   │   │   ├── DiagramRoom.java             # 协作房间实体
│   │   │   ├── SysRole.java                 # 角色实体
│   │   │   ├── SysAuthority.java            # 权限实体
│   │   │   └── ...
│   │   ├── dto/             # 数据传输对象
│   │   │   ├── user/                        # 用户相关 DTO
│   │   │   ├── space/                       # 空间相关 DTO
│   │   │   ├── diagram/                     # 图表相关 DTO
│   │   │   └── ...
│   │   └── vo/              # 视图对象
│   │       ├── UserVO.java
│   │       ├── DiagramVO.java
│   │       └── ...
│   │
│   ├── config/              # 配置类
│   │   ├── CorsConfig.java               # 跨域配置
│   │   ├── JsonConfig.java               # JSON 配置
│   │   └── ...
│   │
│   ├── security/            # 安全相关
│   │   └── UserDetailsServiceImpl.java   # 用户详情服务
│   │
│   ├── ai/                  # AI 模块
│   │   ├── tools/           # AI 工具
│   │   │   ├── CreateDiagramTool.java    # 创建图表工具
│   │   │   ├── EditDiagramTool.java      # 编辑图表工具
│   │   │   ├── AppendDiagramTool.java    # 追加图表工具
│   │   │   └── ...
│   │   ├── config/          # AI 配置
│   │   ├── client/          # AI 客户端
│   │   └── chatmemory/      # 聊天记忆
│   │
│   ├── ws/                  # WebSocket 模块
│   │   ├── config/          # WebSocket 配置
│   │   ├── handler/         # WebSocket 处理器
│   │   └── service/         # WebSocket 服务
│   │
│   ├── common/              # 公共类
│   │   ├── BaseResponse.java              # 基础响应
│   │   ├── ErrorCode.java                 # 错误码
│   │   ├── ResultUtils.java               # 结果工具
│   │   └── ...
│   │
│   ├── exception/           # 异常处理
│   │   ├── BusinessException.java         # 业务异常
│   │   └── GlobalExceptionHandler.java    # 全局异常处理
│   │
│   ├── aop/                 # 切面编程
│   │   ├── AuthInterceptor.java           # 认证拦截器
│   │   └── LogInterceptor.java            # 日志拦截器
│   │
│   ├── enums/               # 枚举类
│   │   ├── UserRoleEnum.java              # 用户角色枚举
│   │   ├── SpaceLevelEnum.java            # 空间等级枚举
│   │   └── ...
│   │
│   └── constant/            # 常量定义
│       ├── UserConstant.java
│       ├── FileConstant.java
│       └── ...
│
└── src/main/resources/
    ├── application.yml              # 主配置文件
    ├── mapper/                      # MyBatis XML 映射文件
    └── ...
```

## 核心功能模块

### 1. 用户管理
- 用户注册、登录、注销
- 用户信息查询与修改
- 用户权限管理
- 基于 Spring Security 的安全认证

### 2. 空间管理
- 创建/编辑/删除空间
- 空间成员管理
- 空间角色权限控制（所有者、管理员、编辑者、查看者）
- 查询我创建的空间/我加入的空间

### 3. 图表管理
- 创建/编辑/删除图表
- 图表上传
- 图表分页查询
- 图表权限控制
- 支持多种格式导出（PNG、SVG、XML）

### 4. 实时协作
- 基于 WebSocket 的实时通信
- 多人同时编辑图表
- 房间成员管理
- 协作权限控制
- 实时更新和快照保存

### 5. AI 辅助绘图
- 集成 Spring AI + OpenAI
- AI 创建图表：通过自然语言描述生成图表
- AI 编辑图表：智能修改现有图表
- AI 追加内容：在图表中智能添加内容
- 支持流式响应
- 聊天记忆功能

### 6. 文件转换
- Draw.io XML 格式转换
- PNG 图片导出
- SVG 矢量图导出
- 批量转换支持

### 7. 权限管理
- RBAC 权限模型
- 角色管理（系统角色、空间角色）
- 权限管理
- 用户-角色关联
- 角色-权限关联
- 基于 Spring Security 的权限校验

### 8. 文件存储
- MinIO 对象存储
- 支持 S3 协议
- 文件上传
- 文件下载
- 文件删除

## 数据库设计

### 核心表结构

- **user** - 用户表
- **space** - 空间表
- **space_user** - 空间成员关系表
- **diagram** - 图表表
- **diagram_room** - 协作房间表
- **room_member** - 房间成员表
- **room_updates** - 房间更新记录表
- **room_snapshots** - 房间快照表
- **sys_role** - 角色表
- **sys_authority** - 权限表
- **sys_user_role_rel** - 用户角色关联表
- **sys_role_authority_rel** - 角色权限关联表
- **conversion** - 转换记录表

## 接口文档

项目集成了 Knife4j 和 Springdoc OpenAPI，启动后访问：

- **Knife4j UI**: http://localhost:8081/api/doc.html
- **Swagger UI**: http://localhost:8081/api/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8081/api/v3/api-docs

## 快速开始

### 环境要求
- JDK 21+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- MinIO 或其他 S3 兼容存储

### 配置说明

1. **修改数据库配置** (`application.yml`)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/drawio
    username: root
    password: your_password
```

2. **修改 Redis 配置**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_password
```

3. **配置 MinIO**
```yaml
minio:
  endpoint: http://localhost:9000
  accessKey: your_access_key
  secretKey: your_secret_key
```

4. **配置 OpenAI API**
```yaml
spring:
  ai:
    openai:
      api-key: your_openai_api_key
```

### 启动项目

```bash
# 克隆项目
git clone <repository-url>

# 进入项目目录
cd drawio-backend

# 编译打包
mvn clean package

# 运行项目
mvn spring-boot:run
```

项目启动后，访问地址：http://localhost:8081/api

## 主要依赖版本

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.5.9</spring-boot.version>
    <mybatis-plus.version>3.5.15</mybatis-plus.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <redisson.version>3.52.0</redisson.version>
    <minio.version>8.5.17</minio.version>
    <hutool.version>5.8.38</hutool.version>
    <knife4j.version>4.5.0</knife4j.version>
</properties>
```

## 特性亮点

1. **AI 集成**
   - 基于 Spring AI 框架，支持多种 AI 模型
   - 自定义 Function Calling 工具
   - 流式响应支持
   - 对话记忆管理

2. **实时协作**
   - WebSocket 长连接
   - 分布式锁保证数据一致性
   - 快照和增量更新机制
   - 在线成员管理

3. **权限体系**
   - 完整的 RBAC 模型
   - 支持系统级和空间级权限
   - 细粒度权限控制
   - Spring Security 集成

4. **分布式架构**
   - Redis Session 共享
   - Redisson 分布式锁
   - Caffeine 本地缓存
   - MinIO 分布式存储

5. **代码规范**
   - RESTful API 设计
   - 统一异常处理
   - 统一响应格式
   - AOP 日志记录
   - Lombok 简化代码

## 开发规范

- API 路径：`/api/{module}/{action}`
- 请求方式：GET（查询）、POST（创建）、PUT（更新）、DELETE（删除）
- 响应格式：统一使用 `BaseResponse` 包装
- 异常处理：使用 `BusinessException` 抛出业务异常
- 日志记录：使用 `@Slf4j` 注解

## License

本项目采用 MIT 许可证。

## 作者

fenghuanwang

---

**注意**: 本项目仅供学习和研究使用，请勿用于商业用途。
