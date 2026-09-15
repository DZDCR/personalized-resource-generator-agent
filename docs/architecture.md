# 系统架构设计文档

> 版本：v0.1 | 状态：草案 | 适用：个性化资源生成与学习多智能体系统

## 1. 架构目标

| 非功能需求 | 指标 |
|-----------|------|
| 可扩展 | 智能体可插拔，新增 Agent 不修改编排核心 |
| 并发 | 多智能体并行生成，虚拟线程压榨单机吞吐 |
| 可靠性 | LLM 调用失败自动重试 + 模板兜底，接口永不 5xx |
| 热更新 | Prompt 模板与资源配置可运行时调整，无需重启 |

## 2. 总体架构

```
┌────────────────────────────────────────────────────────────┐
│                   前端 Vue3 (Vite + Element Plus)           │
│    Chat / Resources / PathMap / Dashboard + WebSocket进度   │
└──────────────┬──────────────────────────────┬──────────────┘
               │ REST (统一 ApiResult)          │ WebSocket(/ws/progress)
┌──────────────▼──────────────┐  ┌────────────▼─────────────┐
│       Controller 层          │  │  Redis: 任务进度/会话状态  │
└──────────────┬──────────────┘  └──────────────────────────┘
┌──────────────▼────────────────────────────────────────────┐
│                       Service 业务层                       │
│   ChatService(路由) ProfileService PathService TutorService │
│   AssessmentService TelemetryService ResourceService        │
└──────────────┬──────────────────────────────────────────────┘
┌──────────────▼──────────────────────────────────────────────┐
│                   Agent 多智能体层（编排核心）                │
│                          Orchestrator                       │
│    Designer → DocWriter/Mindmap/Quiz/Reading (并行)          │
│             → Script(依赖Doc) / Coder(依赖蓝图)              │
│    ProfileAgent / TutorAgent（对话型）                        │
└──────────────┬──────────────────────────────────────────────┘
┌──────────────▼────────────────┬─────────────────────────────┐
│  Spring AI (ChatClient)       │  Qdrant (向量)  Neo4j (图谱)  │
│  OpenAI 兼容 API，双模型路由     │                             │
└───────────────────────────────┴─────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│  PostgreSQL (JPA)   Redis    RabbitMQ(异步埋点)  定时任务      │
└──────────────────────────────────────────────────────────────┘
```

## 3. 分层与依赖规则

- **分层**：`controller → service → repository / agent`
- **规则1**：Controller 只做参数校验与响应包装，禁止写业务逻辑。
- **规则2**：Service 依赖 Repository 与 Agent；Service 之间允许横向调用。
- **规则3**：Agent 之间**不直接调用**，仅通过 Orchestrator 按依赖关系编排，
  通信对象为 `agent.product` 包内的产物 Record。
- **规则4**：所有对外响应使用 `ApiResult<T>` 包装。

## 4. 多智能体编排协议

### 4.1 智能体角色

| Agent | 输入 | 输出 | 归属阶段 |
|-------|------|------|---------|
| Designer | 需求+画像 | ResourceBlueprint | 串行（头部） |
| DocWriter | 蓝图 | CourseDoc | 并行 |
| Mindmap | 蓝图 | MindmapData | 并行 |
| Quiz | 蓝图 | QuizSet | 并行 |
| Reading | 蓝图 | ReadingMaterial | 并行 |
| Script | 蓝图+Doc大纲 | VideoScript | 串行（依赖） |
| Coder | 蓝图 | CodeCaseSet | 并行/串行 |
| Profile | 画像+对话 | ProfileUpdate | 对话型 |
| Tutor | 问题+画像 | TutorAnswer | 对话型 |

### 4.2 编排流程

```
start(taskId, req)
  └─ Designer.parse ──失败→ FAILED 结束
      ├─ fork DocWriter / Mindmap / Quiz / Reading（虚拟线程并行）
      ├─ join 全部完成
      ├─ Script.generate(Doc.outline)          // 串行依赖
      ├─ Coder.generate(Blueprint)
      └─ ResourceService.save(bundle, taskId)  // 落库 + 向量入库
```

失败策略：单 Agent 失败不影响整体，缺失类型记录 WARN 日志并跳过入库；
如需强一致可配置 `strict: true` 则任一失败整体失败。

### 4.3 任务进度推送

- 每个 Agent 完成时向 Redis 发布 progress 事件（`taskId → List<AgentMessage>`）。
- WebSocket 端点 `/ws/progress?taskId=xxx` 实时推送到前端。
- 前端展示：`文档✓ → 导图✓ → 题库✓ → …` 逐步点亮。

## 5. 学生画像规范

### 5.1 画像 JSON Schema（≥6 维度）

```jsonc
{
  "basic_info": { "major": "", "grade": "", "goal": "", "hours_per_week": 15 },
  "knowledge_base": { "level": "", "prerequisites": [], "weakness": [], "score": 0 },
  "cognitive_style": { "primary": "visual", "secondary": "", "preferred": [] },
  "learning_pace": { "speed": "", "session_min": 45, "best_time": "" },
  "error_preferences": { "types": [], "topics": [] },
  "motivation": { "type": "", "target": "" },
  "collaboration": { "mode": "" },
  "meta": { "confidence": 0.0, "version": 1, "updatedAt": "" }
}
```

### 5.2 更新协议

- 唯一入口 `ProfileService.applyUpdate(studentId, updatePath, updateValue)`。
- `updatePath` 为点路径，如 `dimensions.knowledge_base.level`，禁止直接整体替换画像（除首次创建）。
- 加权合并：`新值 = 历史*0.6 + 新增*0.4`；`confidence +Δ`，上限 1.0。
- 版本号单调递增，写入 `student_profiles` 新行（版本化，可回溯）。

### 5.3 随学随新触发源

| 触发源 | 更新动作 |
|--------|---------|
| 对话回复 | ProfileAgent 抽取 → applyUpdate |
| 测试提交（quiz_submit） | 更新 knowledge_base.score / error_preferences |
| 资源完成（resource_complete） | 更新 learning_pace |
| 每周定时（周一 02:00） | 全量重估，复用近 7 天事件 |

### 5.4 冲突检测

当学习行为与画像声明明显冲突（如画像为 visual 但文档停留时长异常高），
触发一次澄清对话，修改值取学生确认结果。

## 6. 学习路径规划算法

```
generate(studentId, targetTopic)
  1. prerequisiteClosure(target)      // Neo4j BFS 求先修闭包，防环
  2. 过滤已掌握节点（来自画像 score ≥ 80）
  3. topologicalSort(未掌握节点)       // 保证先修在前
  4. 每节点匹配资源：
       visual → VIDEO优先 | reading → DOC优先 | kinesthetic → CODE优先
  5. 输出 PathNode 序列（含 expectedMinutes、checkpoint）

动态调整触发：
  - 节点测试 < 50 分 → 插入复习节点，降资源难度
  - 完成率 < 60%  → 插入轻量短任务
  - 超前完成       → 解锁进阶内容（planVersion+1）
```

## 7. 评估指标体系（加分项）

```
scoreTotal = knowledge*0.4 + effort*0.2 + efficiency*0.2 + growth*0.2

knowledge = 最近10次测试得分加权平均（越新权重越高）
effort    = 学习时长达标率*0.5 + 资源完成率*0.5
efficiency = max(0, Δscore / 学习时长)
growth    = 画像 score 周环比增长率（归一化）

报告：LLM 基于聚合数据输出自然语言结论（优点 + 3 条改进 + 建议），
并返回画像/路径调整指令。
```

## 8. 技术选型与取舍

| 能力 | 选型 | 备注 |
|------|------|------|
| LLM 调用 | Spring AI ChatClient | 统一多模型、结构化输出 |
| 智能体编排 | 自建 Orchestrator | Java 生态无成熟 LangGraph 对等件 |
| 并发 | 虚拟线程 + StructuredTaskScope | Java 21 正式特性 |
| 异步任务 | @Async + 虚拟线程执行器 | 骨架；量大可迁 RabbitMQ |
| 向量库 | Qdrant | 画像/资源语义检索 |
| 图谱 | Neo4j（骨架用内存 Map 替代） | 先修关系 |
| 埋点 | 同步落库 → RabbitMQ 异步 | 骨架为同步写入 |

## 9. 关键风险与对策

| 风险 | 对策 |
|------|------|
| LLM 输出不稳定 | JSON Schema 校验 + 3 次重试 + 模板兜底 |
| Prompt 频繁调整 | 模板外置 `resources/prompt/*.md`，支持热更新 |
| 长任务阻塞 | 异步提交 + 任务号轮询/WebSocket 进度 |
| 模型费用 | heavy/light 双模型路由，短对话走小模型 |
| 数据量增长 | 画像/资源向量化，TopK 检索优于全量扫描 |

## 10. 部署拓扑

```
docker-compose（docker/docker-compose.yml）：
  PostgreSQL / Redis / RabbitMQ / Neo4j / Qdrant
  └─ backend: mvn spring-boot:run（或容器化）

生产建议：
  - frontend 使用 Nginx 托管并反代 /api
  - 后端多实例时任务进度改用 Redis pub/sub + 服务端会话
  - 定时任务加分布式锁（ShedLock）
```