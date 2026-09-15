# API 接口规范

## 1. 通用约定

- Base URL：`http://localhost:8080`（前端经 Vite proxy → `/api`）
- 传输格式：JSON，UTF-8
- 统一响应包裹：

```jsonc
{
  "code": 0,          // 0 成功；400 参数错误；404 不存在；500 内部错误；其他为业务错误码
  "message": "success",
  "data": { ... }
}
```

- 错误语义见 `common/GlobalExceptionHandler` 与 `common/BizException`。
- 所有写操作均为同步接口，除 `POST /api/resources/generate`（异步任务，立即返回任务号）。

## 2. 对话

### `POST /api/chat`
对话统一入口，后端根据画像置信度路由到「画像构建」或「辅导」。

请求：
```jsonc
{ "studentId": "1", "message": "我是计算机专业大二学生" }
```
响应 data：
```jsonc
{ "mode": "PROFILE_BUILDING | TUTORING", "reply": "智能体回复文本" }
```

## 3. 画像

### `GET /api/profile/{studentId}`
返回最近一版画像 JSON。

响应 data：画像 JSON（见 [架构-5](./architecture.md)）。

## 4. 资源

### `POST /api/resources/generate`
提交多智能体资源生成（异步）。

请求：
```jsonc
{
  "studentId": "1",
  "topic": "递归算法",
  "resourceTypes": [],                       // 空=全量六类
  "constraints": { "language": "python" }    // 可选
}
```
响应 data：
```jsonc
{ "taskId": "uuid", "status": "RUNNING", "message": "资源生成已提交" }
```

### `GET /api/resources/{studentId}`
资源列表。

响应 data：
```jsonc
[
  { "id": 1, "type": "DOC", "topic": "递归", "difficulty": "medium",
    "metaJson": "{}", "createdAt": "2026-09-11T08:00:00Z" }
]
```

### `GET /api/resources/detail/{id}`
资源详情（`contentJson` 依类型结构不同：DOC=Markdown；MINDMAP=markmap 树；
QUIZ=QuizSet；READING=文本；VIDEO_SCRIPT=分镜；CODE=代码案例）。

## 5. 学习路径

### `POST /api/path/generate`
请求：
```jsonc
{ "studentId": "1", "targetTopic": "递归" }
```
响应 data：
```jsonc
{
  "studentId": 1,
  "target": "递归",
  "stepOrder": ["函数", "栈", "递归"],
  "estimatedMinutes": { "函数": 45, "栈": 45, "递归": 45 },
  "checkpointPassRate": 0.8
}
```

## 6. 练习

### `POST /api/quiz/submit`
请求：
```jsonc
{
  "studentId": "1",
  "resourceId": 10,
  "answers": ["A", "B", "C"],
  "durationSec": 120
}
```
响应 data：`{ "score": 0, "message": "..." }`

## 7. 智能辅导

### `POST /api/tutor/ask?studentId=1&question=什么是递归`
响应 data：
```jsonc
{
  "type": "concept | calculation | code",
  "text": "解答正文",
  "visualBlock": "图解/流程图/代码差异（前端渲染）"
}
```

## 8. 评估

### `GET /api/assessment/{studentId}/weekly`
响应 data：
```jsonc
{
  "studentId": 1,
  "period": "2026-W37",
  "scoreTotal": 82,
  "dimensions": { "knowledge": 80, "effort": 85, "efficiency": 78, "growth": 86 },
  "report": "LLM 生成的周报文本"
}
```

## 9. 埋点

### `POST /api/telemetry/{studentId}/{eventType}`
`eventType ∈ study_start | study_pause | resource_open | resource_complete | quiz_submit | video_play | question_submit`

可携带 payload body（可选）。

## 10. 运维

### `GET /api/ops/health`
`{ "status": "UP" }`

### `POST /api/ops/prompts/reload`
清空 Prompt 模板缓存，实现热更新。

## 11. WebSocket

- 端点：`/ws/progress?taskId=xxx`
- 消息格式：`AgentMessage{ taskId, agentName, status, topic, message, durationMs }`
- 前端收到全部 `DONE`（orchestrator）后刷新资源列表。

## 12. 状态码汇总

| code | 含义 |
|------|------|
| 0 | 成功 |
| 400 | 参数校验失败 |
| 404 | 资源/学生不存在 |
| 500 | 内部错误（含 LLM 兜底后仍失败） |
| 50401+ | 预留业务错误（模型不可用、生成超时等） |