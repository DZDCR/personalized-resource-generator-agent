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
| **RBAC3 身份认证与权限管理** | 4 种登录方式、图形验证码、注册/找回密码、角色继承、SSD/DSD 约束、审计 | 可用 |
| 实时聊天 | 课程群 / 私聊、WebSocket 实时收发、离线补拉、未读与多端同步 | MVP 可用 |

## RBAC3 权限模块预览

登录页支持账号密码 / 邮箱验证码 / 短信验证码 / 统一身份认证四种方式；权限中心覆盖用户、角色继承、权限点、SSD/DSD 约束与审计日志。

![登录页](docs/screenshots/rbac/01-login.png)

![权限中心](docs/screenshots/rbac/02-users.png)

详细设计与演示账号见 [RBAC3 设计](docs/rbac3-design.md)。

## 技术栈

- 后端：Java 21 + Spring Boot 3.3 + Spring Security（JWT / Method Security）+ Spring AI + Spring Data JPA + Qdrant + Neo4j + Redis + RabbitMQ
- 前端：Vue3 + Vite + Pinia + Element Plus + WebSocket（进度推送 + 实时聊天）
- 基础设施：PostgreSQL / Redis / RabbitMQ / Neo4j / Qdrant（docker-compose 一键启动）

## 快速开始

### 一键启动聊天演示（无需 Docker）

双击项目根目录的 `start-chat.bat`。脚本会：

1. 启动后端 `demo` profile（H2 内存库，端口 8080）
2. 启动 Vite 前端（端口 5173）
3. 自动打开 `http://localhost:5173/im`

> `http://localhost:8080` 是 API 服务，不是聊天页面；聊天页面必须访问 **5173** 端口。首次启动等待约 15 秒，若浏览器打开过早刷新即可。

如果已经用 `run.bat` 启动了后端和 Docker，但日志提示 `node not found - skip frontend`，单独运行：

```bat
cd E:\软件工程搭建\软件工程
start-frontend.bat
```

然后打开 `http://localhost:5173/im`。

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
# Windows
run.bat
# macOS / Linux
# chmod +x run.sh && ./run.sh

# 4. 启动前端（需 Node 20+）
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`，左侧进入 **班级聊天**（`/im`）。首次启动会自动创建用户A / 用户B / 用户C 和“示例群聊”，可通过左上角身份下拉切换学生，验证多端实时收发；也可以用 `/im?student=2` 直接指定身份，同时开多个标签页联调。

### 无 Docker 演示聊天（H2）

只想先看聊天功能时，不需要启动 PostgreSQL 等基础设施：

```bat
:: Windows
cd backend
run-demo.bat
```

```bash
# macOS / Linux
chmod +x backend/run-demo.sh
backend/run-demo.sh
```

再按上面的方式启动前端，打开 `http://localhost:5173/im`。演示数据保存在内存 H2 中，重启即重置。

> 如果 `mvn spring-boot:run` 报 `ClassNotFoundException: com.example.learning.LearningApplication`，请改用 `run-demo` 脚本。项目路径含中文时，Spring Boot Maven Plugin 的 `@argfile` 可能丢失 `target/classes`；脚本已改为直接读取 UTF-8 classpath 后用 `java -cp` 启动。

### 方式三：RBAC3 独立演示（HSQLDB 文件库，无需 PostgreSQL/Redis/Neo4j/RabbitMQ）

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=rbac-demo
```

该 profile 使用 **HSQLDB 文件库**（`backend/data/rbac.script` 等），并排除 Redis/Neo4j/RabbitMQ 自动配置；
自建角色、用户和约束在后端重启后仍会保留。删除 `backend/data` 目录即可重置演示数据。
登录与权限中心接口可直接使用（主应用其余接口仍可能需要完整基础设施）。

另开终端启动前端，并把代理指向 8081：

```bash
cd frontend
npm install
# Windows PowerShell
$env:VITE_API_TARGET="http://localhost:8081"
npm run dev
# Linux/macOS: VITE_API_TARGET=http://localhost:8081 npm run dev
```

首次启动自动建表并写入演示账号。内置角色只保留 **学生、系统管理员、安全审计员** 三个，
教师/审核员/发布员等业务角色请登录 `admin / Admin@12345` 后在权限中心自行创建。
完整模型见 [RBAC3 设计](docs/rbac3-design.md)。

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
| [数据字典](docs/data-dictionary.md) | 业务表 + `auth_*` RBAC 表结构、列约束、索引设计、关联关系 |
| [RBAC3 设计](docs/rbac3-design.md) | 认证流程、RBAC0/1/2 模型、SSD/DSD、API、演示账号、作业对照 |
| [Git 协作规范](docs/git-guide.md) | 分支模型、提交规范、PR 规范、工作流示例 |
| [实时聊天接口](docs/im-api.md) | 聊天数据模型、REST API、WebSocket 协议与演示方式 |
| [成文标准](docs/成文标准.md) | 五类文档的质量要求与评分对照表 |

## 目录结构

```
learning-agent-system/
├── backend/                             # Spring Boot 后端
│   ├── pom.xml                          # Maven 依赖（Spring AI + JPA + Redis + ...）
│   ├── run.bat / run.ps1 / run.sh       # 编译 + java -cp 启动（规避中文路径 argfile 问题）
│   ├── run-demo.bat / run-demo.ps1 / run-demo.sh  # 一键 H2 演示（demo profile）
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
│       │   │   ├── WebSocketConfig.java #   WebSocket 进度 + IM 端点
│       │   │   ├── ImWebSocketHandler.java # 聊天 WebSocket 协议
│       │   │   └── TaskProgressBroadcaster.java  # 进度广播服务
│       │   ├── controller/              # 9 个 REST Controller
│       │   ├── dto/                     # 请求/响应 Record（含 im 子包）
│       │   ├── entity/                  # 12 个 JPA Entity
│       │   ├── repository/              # 11 个 Spring Data 接口
│       │   ├── service/                 # 10 个 Service（Profile/Path/Resource/Im/...）
│       │   ├── job/                     # 3 个定时任务骨架
│       │   ├── auth/                    # ★ RBAC3 认证与授权模块
│       │   │   ├── config/              #   SecurityConfig / JWT / 初始化
│       │   │   ├── controller/          #   登录、注册、找回密码、管理端
│       │   │   ├── entity/repository/   #   用户/角色/权限/闭包/约束/审计
│       │   │   ├── service/             #   慢哈希、验证码、令牌、RBAC 查询
│       │   │   └── security/            #   PasswordEncoder / JWT / 401/403
│       │   └── common/                  # ApiResult / BizException / 全局异常处理
│       └── resources/
│           ├── application.yml          # 数据库/Redis/Neo4j/Qdrant/Spring AI 配置
│           ├── application-demo.yml     # 无 Docker 演示：H2 + 自动种子数据
│           ├── application-rbac-demo.yml# HSQLDB 独立演示配置
│           └── prompt/                  # 9 个 Prompt 模板（.md，支持热更新）
│
├── frontend/                            # Vue3 + Vite 前端
│   ├── package.json / vite.config.js / index.html
│   └── src/
│       ├── views/                       # Chat / RealtimeChat / Resources / PathMap / Dashboard
│       │   ├── auth/                    # 登录 / 注册 / 找回密码 / SSO 回调
│       │   └── admin/                   # 用户 / 角色 / 权限 / 约束 / 审计
│       ├── components/                  # ProgressBar / MindmapViewer / CodeBlock / QuizCard / PathTimeline / WsProgress
│       ├── stores/                      # Pinia: chat / im / resource / path / assessment / auth
│       ├── api/                         # Axios 封装 + 聊天/认证/RBAC API
│       ├── router/                      # Vue Router 路由 + 登录/权限守卫
│       └── styles/                      # 全局样式
│
├── docker/                              # 基础设施 + 应用部署
│   ├── docker-compose.yml               # 7 个服务（PG/Redis/RabbitMQ/Neo4j/Qdrant + 后端/前端）
│   ├── Dockerfile.backend
│   └── Dockerfile.frontend + nginx.conf
│
├── docs/                                # 标准规范与聊天实现文档（9 份）
│   ├── architecture.md                  # 架构设计
│   ├── dev-guide.md                     # 开发规范
│   ├── api-spec.md                      # API 接口规范
│   ├── data-dictionary.md               # 数据字典
│   ├── rbac3-design.md                  # RBAC3 身份认证与权限设计
│   ├── rbac3-branch-push.md             # 分支推送指南
│   ├── git-guide.md                     # Git 协作规范
│   ├── im-api.md                        # 实时聊天接口与协议
│   ├── screenshots/rbac/                # 登录/权限中心截图
│   └── 成文标准.md                       # 文档质量标准
│
├── start-chat.bat                       # 一键启动聊天演示（后端 H2 + 前端 + 浏览器）
├── start-frontend.bat                   # 只启动前端（自动补 Node.js PATH）
├── run.bat / run.sh                     # 一键启动完整系统
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
| 聊天大群扩散 | 当前为单实例 MVP；大群可演进为读扩散 + 在线信号推送 |
| 聊天推送 | 离线厂商推送、图片/文件消息、消息搜索尚未接入 |