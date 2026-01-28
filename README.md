# 🚀 Draw.io Backend (AI Enhanced)

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-green.svg)](https://spring.io/projects/spring-boot)
[![Node.js](https://img.shields.io/badge/Node.js-18+-339933.svg)](https://nodejs.org/)
[![Hocuspocus](https://img.shields.io/badge/Hocuspocus-2.x-blue.svg)](https://hocuspocus.dev/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-Powered-blueviolet.svg)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> A high-performance Draw.io backend service powered by Spring Boot 3 + Spring AI + Node.js. Supports real-time collaboration, AI-assisted drawing, and distributed architecture.

---

## 📖 简介 | Introduction

本项目采用了 **Spring Boot + Node.js** 的双端混合架构，旨在构建下一代智能绘图平台：
- **Spring Boot 后端**: 负责核心业务逻辑、用户管理、文件存储、AI 对话接口以及数据持久化。
- **Node.js (Hocuspocus) 微服务**: 专为 **实时协作** 设计，基于 WebSocket 和 Yjs CRDT 算法，提供毫秒级的多人同步编辑体验，并负责将文档快照持久化回 Spring Boot。

## 📑 目录 | Table of Contents
- [简介 | Introduction](#-简介--introduction)
- [核心特性 | Key Features](#-核心特性--key-features)
- [演示截图 | Demo & Verify](#-演示截图--demo--verify)
- [技术栈 | Tech Stack](#-技术栈--tech-stack)
- [快速开始 | Quick Start](#-快速开始--quick-start)
- [项目结构 | Project Structure](#-项目结构--project-structure)
- [接口文档 | API Documentation](#-接口文档-api-documentation)
- [贡献 | Contribution](#-贡献--contribution)

## ✨ 核心特性 | Key Features

### 🤖 1. AI 智能辅助
- **Text-to-Diagram**: 通过自然语言描述直接生成流程图。
- **AI 编辑**: 智能修改现有图表结构和内容。
- **智能续写**: AI 自动补充流程图分支和节点。
- **流式响应**: 类似 ChatGPT 的打字机效果。

### 🤝 2. 实时多人协作 (Node.js)
- **高性能同步**: 定制的 Hocuspocus (Node.js) 服务处理高并发 WebSocket 连接。
- **CRDT 算法**: 使用 Yjs 确保多人编辑时的数据最终一致性。
- **增量更新**: 高效的二进制差异同步。
- **分布式锁**: 结合 Redisson 保证业务逻辑原子性。

### 🛡️ 3. 完善的架构
- **双端鉴权**: Node.js 服务通过内部接口与 Spring Boot 验证用户身份。
- **数据回写**: 协作产生的内容会自动生成快照并保存至 MySQL。
- **对象存储**: 集成 MinIO/S3 存储图表文件。

## � 演示截图 | Demo & Verify


| AI Generation (Stream) | Real-time Collaboration |
| :---: | :---: |
| ![Real-time Collaboration](http://47.95.35.178:9001/drawio/2026-01-28%2015-36-48.gif?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Credential=HF9N36XIGIIENR3ZAW3Z%2F20260128%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260128T075427Z&X-Amz-Expires=604800&X-Amz-Security-Token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE3Njk2Mjk5NjMsInBhcmVudCI6InJ1c3Rmc2FkbWluIn0.v1dLKVxg0jlMfn1oeGiQvKVbVCOsWkU1AapHaufQwbZZvqrCUgF9WOOBYVJUbq6kmANLuwTrc04dPqSswchMEw&X-Amz-Signature=fd351b3fd813c44997a277691debbe31d340f4bee48d1ebc036c5b7ffe53b833&X-Amz-SignedHeaders=host&x-amz-checksum-mode=ENABLED&x-id=GetObject) | ![AI Generation ](http://47.95.35.178:9001/drawio/2026-01-28%2015-34-32.gif?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Credential=HF9N36XIGIIENR3ZAW3Z%2F20260128%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260128T075510Z&X-Amz-Expires=604800&X-Amz-Security-Token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE3Njk2Mjk5NjMsInBhcmVudCI6InJ1c3Rmc2FkbWluIn0.v1dLKVxg0jlMfn1oeGiQvKVbVCOsWkU1AapHaufQwbZZvqrCUgF9WOOBYVJUbq6kmANLuwTrc04dPqSswchMEw&X-Amz-Signature=32f5845c1968f410bc4c2e9ddcd568161bca3440cd1ce40489aa5ba5d660862e&X-Amz-SignedHeaders=host&x-amz-checksum-mode=ENABLED&x-id=GetObject) |

## �🛠️ 技术栈 | Tech Stack

| 类别 | 技术 | 说明 |
| --- | --- | --- |
| **Core (Java)** | Java 21, Spring Boot 3.5.9 | 核心业务后端 |
| **Collab (Node)**| **Node.js, Hocuspocus, Yjs** | **实时协作微服务** |
| **AI** | Spring AI, OpenAI API | AI 能力接入 |
| **Database** | MySQL 8.0, MyBatis-Plus | 关系型数据库 |
| **Cache & Msg** | Redis, Redisson | 缓存、分布式锁 |
| **Storage** | MinIO | 对象存储 |
| **Security** | Spring Security | 安全认证 |

## 🚀 快速开始 | Quick Start

### 1. 环境准备
- **JDK**: 21+
- **Node.js**: 18+
- **Database**: MySQL 8.0+, Redis 6.0+
- **Storage**: MinIO

### 2. 启动 Spring Boot 后端
修改 `src/main/resources/application.yml` 配置数据库和 Key，然后运行：

```bash
# 根目录下
mvn clean package -DskipTests
java -jar target/drawio-backend-0.0.1-SNAPSHOT.jar
# 服务运行在: http://localhost:8081
```

### 3. 启动 Node.js 协作服务
该服务用于 WebSocket 连接，在此目录下单独运行：

```bash
cd node

# 安装依赖
npm install

# 启动服务
npm start
# 服务运行在: http://localhost:1234
```

> **注意**: 确保 `node/utils/api.js` 或 `.env` 中的 `SPRING_BOOT_URL` 指向正确的 Spring Boot 地址。

## 📂 项目结构 | Project Structure

```text
drawio-backend/
├── node/                # [NEW] Node.js 实时协作微服务
│   ├── utils/           # 工具类 (API调用)
│   ├── server.js        # Hocuspocus 服务器入口
│   └── package.json     # Node 依赖配置
├── src/main/java/       # Spring Boot 核心代码
│   ├── controller/      # API 接口
│   ├── service/         # 业务逻辑
│   ├── model/           # 数据模型
│   ├── ai/              # Spring AI 模块
│   └── ws/              # (可选) Java端 WebSocket 逻辑
└── src/main/resources/  # 配置文件
```

## 📚 接口文档
- **API Docs**: [http://localhost:8081/api/doc.html](http://localhost:8081/api/doc.html)
- **WebSocket**: `ws://localhost:1234` (由 Node.js 服务提供)

## 📈 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=wangfenghuan/drawio-backend&type=Date)](https://star-history.com/#wangfenghuan/drawio-backend&Date)

## 🤝 贡献 | Contribution
欢迎提交 Pull Request！由于包含多语言服务，提交时请注明修改的是 Java 还是 Node.js 部分。

## 📄 许可证 | License
[MIT License](LICENSE)

---
**Author**: fenghuanwang
