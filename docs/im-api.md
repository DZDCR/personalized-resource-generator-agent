# 实时聊天接口与协议

> 版本：v1.0（MVP）  
> 范围：课程群 / 师生私聊、WebSocket 实时收发、REST 历史消息、已读游标、离线补拉、多设备会话。

## 1. 实现概览

| 层 | 实现 |
|----|------|
| 连接 | Spring WebSocket `/ws/im?studentId={id}` |
| 持久化 | PostgreSQL：`chat_conversations` / `chat_members` / `chat_messages` |
| 会话路由 | 单实例 `ImSessionRegistry`（userId → WebSocketSession） |
| 幂等 | `clientMsgId` + `(sender_id, client_msg_id)` 唯一约束 |
| 定序 | 发送时锁会话行，`last_seq + 1` 分配会话内 seq |
| 未读 | 成员 `last_read_seq` 与消息 seq 计算 |
| 离线 | 消息落库，上线后 REST 按 `afterSeq` 增量拉取 |

> 当前为课程项目 MVP：单实例内存路由、文本消息。生产可替换为 Redis 路由 + 网关集群、Kafka 消息日志、对象存储和厂商推送。

## 2. 身份与演示数据

项目暂无登录体系，接口用 `studentId` 标识身份。首次启动（`app.im.seed-demo=true`）会自动创建：

- 用户A（demo-001）
- 用户B（demo-002）
- 用户C（demo-003）
- 群聊“示例群聊”，并写入一条欢迎消息

前端 `/im` 左上角可切换身份，模拟多端联调；也可以直接打开 `/im?student=2` 指定身份，便于同时开多个标签页验证多端收发。

## 3. REST API

所有响应统一为 `ApiResult`：`{ "code": 0, "message": "success", "data": ... }`。

### 3.1 演示用户

```http
GET /api/im/demo/users
```

```json
{"code":0,"message":"success","data":[
  {"id":1,"studentNo":"demo-001","name":"用户A"},
  {"id":2,"studentNo":"demo-002","name":"用户B"}
]}
```

### 3.2 会话列表

```http
GET /api/im/conversations?studentId=2
```

```json
{"code":0,"message":"success","data":[{
  "id":1,
  "type":"GROUP",
  "title":"示例群聊",
  "lastSeq":4,
  "unreadCount":2,
  "lastMessagePreview":"大家好",
  "lastMessageAt":"2026-11-23T03:31:19Z",
  "members":[
    {"userId":1,"displayName":"用户A","role":"OWNER"},
    {"userId":2,"displayName":"用户B","role":"MEMBER"}
  ]
}]}
```

### 3.3 创建私聊 / 群聊

```http
POST /api/im/conversations/direct
Content-Type: application/json

{"studentId":2,"peerId":1}
```

```http
POST /api/im/conversations/group
Content-Type: application/json

{"studentId":1,"title":"示例群聊","memberIds":[1,2,3]}
```

重复创建同一对用户的私聊会复用已有会话。

### 3.4 历史消息 / 增量补拉

```http
GET /api/im/conversations/1/messages?studentId=2&afterSeq=0&limit=50
```

- `afterSeq=0`：返回最近 `limit` 条，按 seq 升序。
- `afterSeq>0`：返回 seq 大于该值的消息，用于断线重连补拉。
- `hasMore=true` 表示还有更早/更多消息。

```json
{"code":0,"message":"success","data":{
  "messages":[{
    "id":2,"conversationId":1,"seq":2,"senderId":2,
    "senderName":"用户B","type":"TEXT","content":"大家好",
    "clientMsgId":"...","createdAt":"2026-11-23T03:21:38Z"
  }],
  "lastSeq":2,
  "hasMore":false
}}
```

### 3.5 REST 发送（WS 不可用时的兜底）

```http
POST /api/im/conversations/1/messages
Content-Type: application/json

{"studentId":2,"clientMsgId":"uuid","content":"大家好"}
```

服务端同样执行幂等、定序、落库和广播。

### 3.6 已读

```http
POST /api/im/conversations/1/read
Content-Type: application/json

{"studentId":2,"seq":4}
```

返回 `{"unreadCount":0}`。

## 4. WebSocket 协议

连接：

```text
ws://localhost:8080/ws/im?studentId=2
```

统一信封：

```json
{"v":1,"type":"message.send","requestId":"r1","ts":1763865600000,"payload":{}}
```

### 4.1 客户端 → 服务端

| type | payload | 说明 |
|------|---------|------|
| `ping` | 无 | 心跳 |
| `message.send` | `conversationId, clientMsgId, content` | 发送文本消息 |
| `message.read` | `conversationId, seq` | 上报已读 |

### 4.2 服务端 → 客户端

| type | payload | 说明 |
|------|---------|------|
| `connected` | `userId, serverTime, heartbeatSeconds` | 连接成功 |
| `pong` | `serverTime` | 心跳响应 |
| `message.ack` | `MessageDto` | 发送成功（含 `id/seq`） |
| `message.new` | `MessageDto` | 新消息（发送方当前连接只收 ACK） |
| `message.read` | `conversationId, userId, seq, unreadCount` | 同账号其他设备已读同步 |
| `message.read.ok` | `conversationId, seq, unreadCount` | 已读上报结果 |
| `error` | `code, message` | 业务/协议错误 |

### 4.3 发送示例

```json
{"v":1,"type":"message.send","requestId":"r1","payload":{
  "conversationId":1,"clientMsgId":"2f1c9e0a","content":"这道题怎么做？"}}
```

```json
{"v":1,"type":"message.ack","requestId":"r1","payload":{
  "id":3,"conversationId":1,"seq":3,"senderId":2,"senderName":"用户B",
  "content":"这道题怎么做？","clientMsgId":"2f1c9e0a","createdAt":"..."}}
```

## 5. 可靠性约定

- **不丢**：先落库再 ACK；消息表持久化，重连后按 `afterSeq` 补拉。
- **不重**：`client_msg_id` 唯一；重试复用同一 ID，服务端返回已有消息。
- **有序**：同一会话发送时锁行分配 seq；客户端按 seq 排序，不依赖时间戳。
- **离线**：前端 localStorage outbox 暂存；重连后自动重发，收到 ACK 后移除。
- **多端**：同一账号多个 WebSocket 连接都会注册；发送方当前连接收 ACK，其他设备收 `message.new`。

## 6. 本地演示（无 Docker）

后端内置 `demo` profile（内存 H2 + 演示数据），不需要 PostgreSQL/Redis：

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

启动后访问 `http://localhost:8080/api/ops/health` 应返回 `UP`。再启动前端：

```bash
cd frontend
npm install
npm run dev
# 打开 http://localhost:5173/im
```

> 不要使用 `mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true`。当前项目路径包含中文时，Spring Boot Maven Plugin 的 `@argfile` 可能丢失 `target/classes`，表现为 `ClassNotFoundException: com.example.learning.LearningApplication`。`run-demo` 脚本改为直接读取 UTF-8 classpath 后用 `java -cp` 启动，已规避该问题。

演示库为内存 H2，重启即重置；生产使用 `application.yml` 中的 PostgreSQL 配置。

## 7. 后续演进

- 网关集群 + Redis 路由，替换单实例 `ImSessionRegistry`
- Kafka 消息日志与按会话分区，支持大群 fanout
- 大群读扩散 / 在线信号推送
- 图片、文件、代码块、公式消息
- 厂商离线推送、消息搜索、举报与内容审核
- 登录鉴权替换 `studentId` 参数
