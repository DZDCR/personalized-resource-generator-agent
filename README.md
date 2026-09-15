# 个性化资源生成与学习多智能体系统

基于 **多智能体 + 大模型** 的个性化学习平台。通过对话式画像、多智能体资源生成、个性化学习路径规划、智能辅导与效果评估，为学生提供全方位的学习支持。

## 功能模块

| 模块 | 说明 | 状态 |
|------|------|------|
| 对话式学习画像 | 自然语言抽取 ≥6 个维度的动态学生画像，随学随新 | 骨架 |
| 多智能体资源生成 | 文档 / 思维导图 / 题库 / 拓展阅读 / 视频脚本 / 代码案例 6 类 Agent 协作生成 | 骨架 |
| 个性化学习路径 | 基于知识图谱拓扑排序 + 画像匹配资源推荐 | 骨架 |
| 智能辅导 | 概念 / 计算 / 代码三类问题的多模态解答 | 骨架 |
| 学习效果评估 | 周评估 + 画像回写 + 路径动态调整 | 骨架 |

## 技术栈

- 后端：Java 21 + Spring Boot 3.3 + Spring AI + Spring Data JPA + Qdrant + Neo4j + Redis + RabbitMQ
- 前端：Vue3 + Vite + Pinia + Element Plus + WebSocket 进度推送
- 基础设施：PostgreSQL / Redis / RabbitMQ / Neo4j / Qdrant（docker-compose 一键启动）

## 快速开始

### 方式一：一键启动脚本

```bash
# Windows
run.bat

# Linux/Mac
chmod +x run.sh && ./run.sh
```

### 方式二：手动启动

```bash
# 1. 启动基础设施
docker compose -f docker/docker-compose.yml up -d postgres redis rabbitmq neo4j qdrant

# 2. 配置环境变量
export OPENAI_API_KEY=sk-xxxx   # 你的 OpenAI 或兼容 API Key

# 3. 启动后端（需 JDK 21 + Maven 3.9+）
cd backend
mvn spring-boot:run

# 4. 启动前端（需 Node 20+）
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。

### Docker 完整部署

```bash
docker compose -f docker/docker-compose.yml up -d
# 前端: http://localhost:80
# 后端: http://localhost:8080
```

## 文档索引

| 文档 | 说明 |
|------|------|
| [架构设计](docs/architecture.md) | 系统架构、分层规则、多智能体编排协议、画像规范、路径算法、技术选型 |
| [开发规范](docs/dev-guide.md) | 工程环境、命名规范、编码约定、分层禁止项、Review 清单 |
| [API 接口规范](docs/api-spec.md) | 10 个接口定义、请求/响应示例、WebSocket 进度推送、状态码 |
| [数据字典](docs/data-dictionary.md) | 9 张数据表结构、列约束、索引设计、关联关系 |
| [Git 协作规范](docs/git-guide.md) | 分支模型、提交规范、PR 规范、工作流示例 |
| [成文标准](docs/成文标准.md) | 五类文档的质量要求与评分对照表 |

## 目录结构

```
learning-agent-system/
├── backend/                             # Spring Boot 后端
│   ├── pom.xml                          # Maven 依赖（Spring AI + JPA + Redis + ...）
│   └── src/main/
│       ├── java/.../
│       │   ├── agent/                   # ★ 多智能体层（9 个 Agent + Orchestrator）
│       │   │   ├── BaseAgent.java       #   抽象基类：LLM 调用/重试/兜底
│       │   │   ├── Orchestrator.java    #   虚拟线程并行编排核心
│       │   │   ├── Designer/Doc/Mindmap/Quiz/Reading/Script/Coder Agent
│       │   │   ├── ProfileAgent.java    #   画像抽取
│       │   │   ├── TutorAgent.java      #   智能辅导（加分）
│       │   │   └── product/             #   8 个产物 Record（Agent 间传递）
│       │   ├── config/                  # 配置层
│       │   │   ├── LlmConfig.java       #   ChatClient 双模型配置
│       │   │   ├── AsyncConfig.java     #   虚拟线程执行器
│       │   │   ├── PromptLoader.java    #   Prompt 热更新加载器
│       │   │   ├── WebSocketConfig.java #   WebSocket 进度端点
│       │   │   └── TaskProgressBroadcaster.java  # 进度广播服务
│       │   ├── controller/              # 8 个 REST Controller
│       │   ├── dto/                     # 4 个请求 + 3 个响应 Record
│       │   ├── entity/                  # 9 个 JPA Entity
│       │   ├── repository/              # 8 个 Spring Data 接口
│       │   ├── service/                 # 8 个 Service（Profile/Path/Resource/Tutor/...）
│       │   ├── job/                     # 3 个定时任务骨架
│       │   └── common/                  # ApiResult / BizException / 全局异常处理
│       └── resources/
│           ├── application.yml          # 数据库/Redis/Neo4j/Qdrant/Spring AI 配置
│           └── prompt/                  # 9 个 Prompt 模板（.md，支持热更新）
│
├── frontend/                            # Vue3 + Vite 前端
│   ├── package.json / vite.config.js / index.html
│   └── src/
│       ├── views/                       # Chat / Resources / PathMap / Dashboard
│       ├── components/                  # ProgressBar / MindmapViewer / CodeBlock / QuizCard / PathTimeline / WsProgress
│       ├── stores/                      # Pinia: chat / resource / path / assessment
│       ├── api/                         # Axios 封装 + 8 个 API 函数
│       ├── router/                      # Vue Router 路由配置
│       └── styles/                      # 全局样式
│
├── docker/                              # 基础设施 + 应用部署
│   ├── docker-compose.yml               # 7 个服务（PG/Redis/RabbitMQ/Neo4j/Qdrant + 后端/前端）
│   ├── Dockerfile.backend
│   └── Dockerfile.frontend + nginx.conf
│
├── docs/                                # 标准规范文档（6 份）
│   ├── architecture.md                  # 架构设计
│   ├── dev-guide.md                     # 开发规范
│   ├── api-spec.md                      # API 接口规范
│   ├── data-dictionary.md               # 数据字典
│   ├── git-guide.md                     # Git 协作规范
│   └── 成文标准.md                       # 文档质量标准
│
├── run.bat / run.sh                     # 一键启动脚本
├── README.md
└── .gitignore
```

## 开发验证

```bash
# 后端编译检查（需 JDK 21）
cd backend && mvn -q compile

# 前端编译检查
cd frontend && npm run build

# 运行测试
cd backend && mvn test
```

## 已知待办

| 项目 | 说明 |
|------|------|
| Neo4j 真实图谱 | 替换 KnowledgeGraphService 中的内存示例图 |
| Qdrant 向量入库 | ResourceService.save() 中补充向量编码逻辑 |
| 画像路径写入 | ProfileService.setAtPath() 实现 |
| 数据库迁移 | 引入 Flyway/Liquibase 管理 DDL |
| Swagger | 集成 springdoc-openapi 自动生成接口文档 |