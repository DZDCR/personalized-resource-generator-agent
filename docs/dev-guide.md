# 开发规范

> 适用于本仓库全部成员。提交代码前必须通读并遵守。

## 1. 工程环境

| 项 | 要求 |
|----|------|
| JDK | 21（虚拟线程为正式特性，勿降级） |
| Maven | 3.9+ |
| Node | 20+ |
| 数据库 | PostgreSQL 16（docker-compose 提供） |
| 代码风格 | 后端 Checkstyle（Sun-style 基准），前端 ESLint + Prettier |

## 2. 目录职责

```
backend/src/main/java/com/example/learning/
├── config/     Spring 配置类（Bean 定义、线程池、Promodel 加载）
├── controller/ 接口层：校验 + ApiResult 包装，禁止业务
├── service/    业务层：事务、编排调用、领域逻辑
├── agent/      智能体层：BaseAgent + 各角色 + Orchestrator
│   └── product/ Agent 间传递的产物 Record（不可变）
├── entity/     JPA 实体，与表一一对应，禁止写计算方法
├── repository/ Spring Data 接口
├── dto/        request/response 分离，record 优先
├── job/        定时任务
├── common/     通用响应、异常、工具
└── event/      领域事件（Spring 事件驱动）
```

## 3. 命名规范

| 对象 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写，无下划线 | `com.example.learning.agent` |
| 类/接口 | UpperCamelCase | `DocWriterAgent` |
| 方法/变量 | lowerCamelCase | `applyUpdate` |
| 常量 | UPPER_SNAKE_CASE | `CHECKPOINT_PASS_RATE` |
| JPA 实体 | 后缀 Entity | `StudentProfileEntity` |
| Repository | 后缀 Repository | `StudentProfileRepository` |
| Service | 后缀 Service | `ProfileService` |
| DTO 请求 | 后缀 Request | `ResourceGenerateRequest` |
| DTO 响应 | 后缀 Response/Summary | `GenerateTaskResponse` |
| 数据库表 | 小写下划线复数 | `quiz_records` |
| 数据库列 | 小写下划线 | `student_id` |

## 4. Java 编码约定

1. **优先 record**：DTO、Agent 产物使用 `record`，替代手写 getter/setter。
2. **不可变优先**：产物对象全部不可变，减少跨 Agent 的副作用。
3. **禁止空返回 null 语义不明**：Agent 方法返回 `Optional`，失败不抛异常。
4. **事务边界**：`@Transactional` 只加在有写操作的 Service 方法上；Agent 不参与事务。
5. **日志**：统一 SLF4J，占位符 `{}`，禁止字符串拼接。
6. **异常**：业务异常抛 `BizException`；禁止 `printStackTrace()`、禁止吞异常。
7. **Stream/Optional**：允许使用，但禁止超过 3 层嵌套链。
8. **依赖注入**：构造器注入（`@RequiredArgsConstructor`），禁止字段注入。

## 5. 分层禁止项

- Controller 不得调用 Repository / Agent。
- Entity 不得承载业务方法（保留 `@PrePersist` 等生命周期钩子除外）。
- Service 不得出现 `ApiResult` 封装逻辑。
- Agent 不得持有 DAO；如需画像上下文，经 Service 传入。

## 6. 前端规范

- Vue3 组合式 API（`<script setup>`）+ Pinia。
- 页面路由均在 `src/router/index.js` 注册；组件放 `src/components`。
- API 全部收敛到 `src/api/`，禁止在页面内直接 `axios`。
- 样式 scoped；全局主题在 `src/assets`。

## 7. Prompt 模板规范（`backend/src/main/resources/prompt/`）

- 命名与 Agent 对应：`designer.md / doc_writer.md / ...`
- **禁止将 Prompt 写在 Java 代码里**；模板变更发布后执行
  `POST /api/ops/prompts/reload` 热更新。
- 占位符统一 `{{变量名}}`，Java 侧用 `replace` 填充。
- 每个模板必须声明「输出格式：严格 JSON + 示例」。

## 8. Code Review 清单

- [ ] 无魔法值（硬编码常量，应抽配置或枚举）
- [ ] 无大方法（>60 行拆分）
- [ ] LLM 调用均经过 BaseAgent，且处理空 Optional 分支
- [ ] 新增表有对应 Entity + Repository + 数据字典更新
- [ ] API 响应使用 ApiResult，且更新 docs/api-spec.md
- [ ] 敏感信息（API Key）不在代码中出现，走环境变量

## 9. 验收标准（每人/每个里程碑）

- `mvn -q compile` 通过。
- 新增接口带冒烟测试（controller 层）。
- Prompt 改动触发热更新通过 `ops/prompts/reload`。
- 提交信息遵循 [Git 规范](git-guide.md)。