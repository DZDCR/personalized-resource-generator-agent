# 文件上传系统设计（tus + tusd + RustFS）

> 版本：v1.0 | 状态：已实现 | 适用：个性化资源生成与学习多智能体系统
>
> 本文偏「设计与运维」。需要**讲给别人听**的版本（协议原理、代码逐段拆解、教学节奏、课堂演示脚本）见 [upload-tutorial.md](upload-tutorial.md)。

## 1. 背景与目标

学习平台需要接收课件、文档、视频、数据集等资料，单文件从几十 MB 到数 GB，
网络环境以校园网/移动网络为主，必须能中断后继续。设计目标：

| 目标 | 实现方式 |
|------|---------|
| 批量选择、小文件快速上传 | Uppy 多选 + tus 单次 PATCH 完成 |
| 大文件流式分片 | 前端按 8MB 切片发送，浏览器不做任何拼接 |
| 服务端重组 | tusd 写入 S3 Multipart，RustFS 内部调用 `CompleteMultipartUpload` 合并 |
| 断点续传 | tus `HEAD` 查询 `Upload-Offset`，前端从偏移量继续 |
| 业务鉴权与入库 | tusd Hook 回调 SpringBoot，`pre-create` 校验、`post-finish` 落库 |
| 同域名、无 CORS | Nginx 将 `/files/` 与 `/api/` 收敛到同一域名 |

**核心约束：前端永远不持有完整文件副本的第二次拷贝，也不做 `Blob` 合并。**

## 2. 总体架构

```
┌───────────────────────────────────────────────────────────────┐
│ 浏览器  Vue3 + Uppy(@uppy/tus)                                  │
│  · 批量多选 / 拖拽                                              │
│  · 按 chunkSize=8MB 切片，逐片 PATCH                             │
│  · 只显示进度、暂停/继续/重试，不合并文件                         │
└───────────────┬───────────────────────────────┬───────────────┘
                │ GET/POST /api/**              │ POST/HEAD/PATCH/DELETE /files/**
                │ (配置、凭证、列表、下载)        │ (tus 1.0.0 协议，字节流)
┌───────────────▼───────────────┐   ┌───────────▼───────────────┐
│ Nginx（同域名前置代理）         │   │ 同域名 /files/ → tusd:1080 │
│  /api → backend:8080          │   │ client_max_body_size 0     │
│  /files/ → tusd:1080          │   │ proxy_request_buffering off│
└───────────────┬───────────────┘   └───────────┬───────────────┘
                │                               │
┌───────────────▼───────────────┐   ┌───────────▼───────────────┐
│ SpringBoot 业务服务            │   │ tusd（Go，独立上传服务）    │
│  · 签发上传凭证（HMAC）         │◄──┤  · 接收分片、维护 offset    │
│  · Hook：校验/落库/置状态       │Hook│  · s3store 写 S3 Multipart │
│  · 文件列表/流式下载/删除       │   │  · 无状态，可水平扩展       │
│  · PostgreSQL: upload_files    │   └───────────┬───────────────┘
└───────────────────────────────┘               │ PutObject / UploadPart
                                                │ CompleteMultipartUpload
                                    ┌───────────▼───────────────┐
                                    │ RustFS（S3 兼容对象存储）   │
                                    │  · 分片在服务端合并成对象    │
                                    │  · 桶私有，仅内网访问        │
                                    └───────────────────────────┘
```

### 2.1 组件职责边界

| 组件 | 负责 | 不负责 |
|------|------|--------|
| Uppy / @uppy/tus | 选文件、切片、并发控制、重试、进度 | 合并、鉴权判定、落库 |
| Nginx | 同源入口、透传 tus 请求、不限请求体、不缓冲 | 业务逻辑、分片存储 |
| tusd | tus 协议、offset 管理、Multipart 组装 | 权限判定、业务元数据 |
| SpringBoot | 配置下发、凭证签发、Hook 校验、记录与下载 | 接收文件字节 |
| RustFS | 对象存储、Multipart 合并、持久化 | 业务语义 |

## 3. 上传链路：字节到底怎么走

### 3.1 小文件（≤ 一个 S3 part）

1. 前端发 `POST /files/`，带 `Upload-Length` 与 `Upload-Metadata`；
2. tusd `pre-create` 回调后端校验凭证/类型/大小，通过后返回 `201` + `Location`；
3. 前端发一次 `PATCH /files/{id}`，请求体就是整个文件（≤8MB 分片时通常一片发完）；
4. tusd 缓冲后以单次 `PutObject` 写入 RustFS；
5. tusd 触发 `post-finish`，后端把记录置为 `COMPLETED`。

小文件没有"分片合并"这一步，只有一次对象写入，延迟最低。

### 3.2 大文件（多个分片）

1. 前端把文件切成 8MB 的 `Blob` 片段，按顺序 `PATCH`；
2. tusd 的 s3store 在内存中缓冲，累计到 `-s3-part-size=16MB` 后调用 `UploadPart`；
   （8MB 分片 × 2 = 一个 part；未满 16MB 的尾部在结束时补一个 part）
3. 第一个 part 上传时，tusd 已通过 `CreateMultipartUpload` 创建了 multipart upload；
4. 最后一个 `PATCH` 完成后，tusd 调用 `CompleteMultipartUpload`；
5. **RustFS 在存储侧把 part 顺序拼接成最终对象**，整个过程没有文件流回浏览器，
   也没有"下载所有分片再合并"的额外 IO。

```
浏览器            Nginx             tusd                 RustFS
  │ PATCH 8MB #1    │                 │                     │
  ├────────────────►│────────────────►│ buffer 8MB          │
  │ 204 offset=8MB  │                 │                     │
  │◄────────────────┼─────────────────┤                     │
  │ PATCH 8MB #2    │                 │ buffer 16MB         │
  ├────────────────►│────────────────►├─ UploadPart #1 ────►│
  │                 │                 │   ETag              │
  │ PATCH 8MB #3    │                 │ buffer 8MB          │
  ├────────────────►│────────────────►│                     │
  │ PATCH 8MB #4    │                 │ buffer 16MB         │
  ├────────────────►│────────────────►├─ UploadPart #2 ────►│
  │                 │                 │                     │
  │                 │                 ├─ CompleteMultipart ►│ 合并完成
  │                 │                 │◄──── 200 ───────────┤
  │ 204 完成        │◄────────────────┤                     │
  │◄────────────────┤                 │                     │
```

### 3.3 为什么不在前端合并

| 方案 | 问题 |
|------|------|
| 前端切分后在内存 `new Blob([...])` 合并再上传 | 需要完整文件驻留内存，2GB 文件在移动端直接崩溃；等于上传两次 |
| 前端把分片传到业务后端，后端合并 | 业务服务被大文件 IO 打满，无法水平扩展，且要自己实现断点续传 |
| **本方案：tus 分片 + S3 Multipart** | 内存只占一个分片；合并在对象存储内部完成；断点是协议原生能力 |

## 4. 交互时序

### 4.1 初始化与凭证

```
浏览器                        SpringBoot                     tusd
  │ GET /api/upload/config       │                            │
  ├─────────────────────────────►│ 返回 tusEndpoint/chunkSize  │
  │◄─────────────────────────────┤ maxFileSize/扩展名/并发数    │
  │ POST /api/upload/token       │                            │
  ├─────────────────────────────►│ HMAC 签发 v1.1.<exp>.<sig>  │
  │◄─────────────────────────────┤                            │
  │ uppy.setMeta({studentId, uploadToken})                    │
```

凭证格式 `v1.{studentId}.{expEpochSecond}.{base64url(HMAC-SHA256)}`，
无状态、可校验过期、不可伪造；生产可平滑替换为 JWT，Hook 侧只需改 `verify`。

### 4.2 上传与 Hook

```
浏览器                 tusd                     SpringBoot
  │ POST /files/         │                          │
  │ Upload-Length        │                          │
  │ Upload-Metadata      ├─ pre-create ────────────►│ 校验凭证/大小/扩展名
  │ (filename,filetype,  │                          │ 返回 ChangeFileInfo（剔除 token）
  │  studentId,token)    │◄──── 200 + 元数据 ───────┤
  │◄── 201 Location ─────┤                          │
  │                      ├─ post-create ───────────►│ 插入 upload_files(UPLOADING)
  │ PATCH × N（8MB）     │                          │
  │◄── 204 Offset ───────┤                          │
  │                      │  UploadPart → RustFS     │
  │                      ├─ post-finish ───────────►│ 更新 COMPLETED + objectKey
  │◄── 204 完成 ─────────┤                          │
```

### 4.3 断点续传

**同一会话 / 刷新页面：**

1. `@uppy/golden-retriever` 把文件 `Blob`（IndexedDB）与 Uppy 状态（含 `file.tus.uploadUrl`）持久化；
2. 重新进入页面，`restored` 事件恢复文件，tus 直接用已有 `uploadUrl`；
3. `HEAD /files/{id}` 拿到 `Upload-Offset`，从该偏移继续 `PATCH`，已上传分片不重传。

**换标签页/换设备：** tus-js-client 的 fingerprint（文件名+大小+类型）保存在 localStorage，
同一浏览器内可通过 `findPreviousUploads()` 恢复；跨设备不支持（需业务侧记录 uploadId，属演进项）。

**服务端重启：** tusd s3store 无状态，`Upload-Offset` 来自 RustFS 中已提交的 part，
重启后 `HEAD` 仍能返回正确偏移，可继续上传。

### 4.4 删除

`DELETE /api/upload/files/{id}` → 后端调 tusd `DELETE /files/{uploadId}` → tusd 清理
`.info` 与未完成分片 → 触发 `post-terminate` 置 `TERMINATED` → 后端再标记 `DELETED`。
tusd 不可用时降级为直接删除 S3 对象。

## 5. Hook 契约

tusd 通过 `-hooks-http` 把事件 POST 到 `/api/upload/hooks`，字段为 PascalCase。

| 事件 | 时机 | 阻塞客户端 | 本系统用途 |
|------|------|-----------|-----------|
| `pre-create` | 创建上传前 | 是 | 凭证/大小/扩展名校验，拒绝非法上传 |
| `post-create` | 上传创建后 | 否 | 插入 `UPLOADING` 记录 |
| `post-finish` | 分片合并完成 | 否 | 写 `objectKey`，置 `COMPLETED` |
| `post-terminate` | 客户端终止 | 否 | 置 `TERMINATED` |

### 5.1 pre-create 请求（节选）

```json
{
  "Type": "pre-create",
  "Event": {
    "Upload": {
      "ID": null,
      "Size": 104857600,
      "SizeIsDeferred": false,
      "Offset": 0,
      "MetaData": {
        "filename": "课件.pdf",
        "filetype": "application/pdf",
        "studentId": "1",
        "uploadToken": "v1.1.1763...sig"
      },
      "Storage": null
    },
    "HTTPRequest": { "Method": "POST", "URI": "/files/", "Header": { "Host": ["localhost"] } }
  }
}
```

### 5.2 pre-create 响应

通过（并剔除凭证）：

```json
{
  "ChangeFileInfo": {
    "MetaData": {
      "filename": "课件.pdf",
      "filetype": "application/pdf",
      "studentId": "1"
    }
  }
}
```

拒绝：

```json
{
  "RejectUpload": true,
  "HTTPResponse": {
    "StatusCode": 413,
    "Header": { "Content-Type": "application/json; charset=utf-8" },
    "Body": "{\"code\":413,\"message\":\"文件超过上限 5.0 GB\"}"
  }
}
```

### 5.3 post-finish 请求（节选）

```json
{
  "Type": "post-finish",
  "Event": {
    "Upload": {
      "ID": "5d892c228ec8d0451dfec588697e8930",
      "Size": 104857600,
      "Offset": 104857600,
      "MetaData": { "filename": "课件.pdf", "studentId": "1" },
      "Storage": {
        "Type": "s3store",
        "Bucket": "learning-uploads",
        "Key": "uploads/5d892c228ec8d0451dfec588697e8930"
      }
    }
  }
}
```

### 5.4 幂等与失败策略

- `post-create` / `post-finish` 先按 `upload_id` 查重，tusd 重试不会产生重复行；
- `post-*` 处理异常返回 `5xx`，tusd 默认重试 3 次（1s 退避），避免瞬时 DB 故障丢记录；
- `pre-create` 异常一律拒绝，绝不"放行后补校验"；
- Hook 内部只做内存校验与单表写入；耗时操作（文本抽取、向量化）留给异步任务，
  避免 tusd 回调超时后重试放大压力。

## 6. 数据模型

`upload_files`（详见 [数据字典](./data-dictionary.md)）：

| 列 | 说明 |
|----|------|
| `upload_id` | tusd 的 Upload-ID，唯一 |
| `student_id` | 归属学生（来自元数据，已在 Hook 校验） |
| `file_name` / `content_type` / `size_bytes` | 展示与下载头 |
| `bucket` / `object_key` / `storage_type` | RustFS 位置，来自 `Upload.Storage` |
| `status` | `UPLOADING / COMPLETED / TERMINATED / DELETED` |
| `offset_bytes` | 服务端已接收字节数 |
| `meta_json` | 元数据快照（不含凭证） |

对象 key 由 tusd 生成（`uploads/{uploadID}`），**不含用户文件名**，
因此不存在路径穿越与重名覆盖；原始文件名只存 DB，下载时写入 `Content-Disposition`。

## 7. 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/upload/config` | tus 端点、分片大小、大小/类型限制、并发数 |
| POST | `/api/upload/token` | 签发上传凭证（无请求体，身份来自会话 Cookie） |
| GET | `/api/upload/files` | 已上传文件列表（归属由会话决定） |
| GET | `/api/upload/files/{id}/download` | 流式下载（支持 Range；`Content-Disposition` 还原文件名） |
| DELETE | `/api/upload/files/{id}` | 终止上传并逻辑删除（校验归属） |
| POST | `/api/upload/hooks` | tusd Hook 回调（仅内网） |

详细字段见 [API 接口规范](./api-spec.md#13-文件上传)。

## 8. 关键配置

### 8.1 tusd

| 参数 | 取值 | 原因 |
|------|------|------|
| `-port` | `1080` | v2.4.0 默认 8080，显式固定避免版本差异 |
| `-s3-bucket` | `learning-uploads` | 桶由后端启动时自动创建（`RustFsBucketInitializer`），tusd 不会建桶 |
| `-s3-endpoint` | `http://rustfs:9000` | 自定义端点会自动启用 path-style（源码 `o.UsePathStyle = true`） |
| `-s3-part-size` | `16MB` | 与前端 8MB 分片成 2:1，减少 part 数量与内存碎片 |
| `-s3-max-buffered-parts` | `4` | 单上传最多缓冲 4 个 part，约 64MB 内存 |
| `-s3-concurrent-part-uploads` | `4` | 单上传的 part 并发写，兼顾吞吐与内存 |
| `-s3-disable-content-hashes` | 开启 | 规避非 AWS 实现对 SDK CRC 头兼容问题 |
| `-behind-proxy` | 开启 | 信任 `X-Forwarded-*`，生成正确的 uploadUrl |
| `-base-path` | `/files/` | 与 Nginx 转发路径一致 |
| `-max-size` | `5GB` | 与后端 `max-file-size` 对齐（双保险） |
| `-hooks-http` | `http://backend:8080/api/upload/hooks` | 内网回调 |
| `-hooks-enabled-events` | `pre-create,post-create,post-finish,post-terminate` | 不需要 `post-receive`（每 1s 回调一次，纯浪费） |

> v2.4.0 的 S3 最小 part 大小是内置常量（5MiB），没有 `-s3-min-part-size` 参数；
> 该参数在较新版本才出现，混用会导致容器直接启动失败（`flag provided but not defined`）。

> 密钥不走命令行：tusd 使用 AWS 默认凭证链，通过 `AWS_ACCESS_KEY_ID` /
> `AWS_SECRET_ACCESS_KEY` / `AWS_REGION` 环境变量注入，避免出现在 `ps` 与镜像层里。

### 8.2 RustFS

| 环境变量 | 值 | 说明 |
|---------|-----|------|
| `RUSTFS_VOLUMES` | `/data/rustfs{0...3}` | 单节点 4 盘拓扑 |
| `RUSTFS_ADDRESS` | `0.0.0.0:9000` | S3 API |
| `RUSTFS_CONSOLE_ADDRESS` | `0.0.0.0:9001` | 控制台 |
| `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY` | 默认 `rustfsadmin` | 生产必须覆盖 |
| `RUSTFS_UNSAFE_BYPASS_DISK_CHECK` | 默认 `false` | 保持严格拓扑校验 |

命名卷首次挂载需要把属主改为 `10001:10001`，compose 中用
`rustfs-permission-helper` 一次性处理。

### 8.3 Nginx

| 指令 | 值 | 原因 |
|------|-----|------|
| `client_max_body_size` | `0` | **默认 1m 会让 8MB 分片直接 413** |
| `proxy_request_buffering` | `off` | 边收边转发，不在 Nginx 落盘整个分片 |
| `proxy_buffering` | `off` | `HEAD`/`PATCH` 响应即时回传 |
| `proxy_http_version` | `1.1` | 支持长连接与分块传输 |
| `proxy_read_timeout` / `proxy_send_timeout` | `3600s` | 慢网络下单个分片也可能很久 |
| `proxy_pass` | `http://tusd:1080`（不带 URI） | 保留 `/files/` 前缀，与 `-base-path` 对齐 |

### 8.4 Spring

```yaml
app:
  upload:
    chunk-size: 8388608          # 下发前端
    max-concurrent-uploads: 3
    tusd:
      base-url: http://tusd:1080 # 服务端终止上传用
      public-path: /files/
    storage:
      endpoint: http://rustfs:9000
      bucket: learning-uploads
    security:
      signing-key: ${UPLOAD_SIGNING_KEY}
      max-file-size: 5368709120
      allowed-extensions: [pdf, docx, pptx, ...]
```

### 8.5 残留上传清理

学生上传到一半离开，记录会停在 `UPLOADING`，而它在 RustFS 里对应一次**未完成的
S3 Multipart Upload**——已写入的 part 不出现在对象列表里，不走任何业务逻辑，会一直占空间。
因此设两道网：

| 层 | 机制 | 生效条件 |
|----|------|---------|
| 业务侧 | `UploadCleanupJob`，每天 03:17 扫描 | 应用正常运行 |
| 存储侧 | 桶生命周期规则 `AbortIncompleteMultipartUpload` | RustFS 正常运行（应用停摆也生效） |

配置项（`app.upload`）：

| 键 | 默认 | 说明 |
|----|------|------|
| `cleanup.enabled` | `true` | 总开关 |
| `cleanup.uploading-ttl-hours` | `168` | `UPLOADING` 存活时长，默认 7 天 |
| `cleanup.batch-size` | `100` | 单批条数，单次最多 5 批 |
| `cleanup.cron` | `0 17 3 * * ?` | 执行时间 |
| `storage.lifecycle-enabled` | `true` | 启动时是否下发桶生命周期规则 |
| `storage.abort-incomplete-multipart-days` | `7` | 存储侧分片回收天数 |
| `storage.object-prefix` | `uploads/` | 规则生效前缀，须与 tusd `-s3-object-prefix` 一致 |

TTL 取 7 天而不是几小时，是刻意的取舍：**宁可少清，也不误伤放假回来续传的大文件**。

状态推进用 `where status = 'UPLOADING'` 的条件更新（`updateStatusIfCurrent`），由此得到两个性质：

- 与 `post-finish` 的竞态不会误伤刚落库的文件——状态已不是 `UPLOADING`，更新影响 0 行；
- 多实例同时执行时后到者影响 0 行，天然幂等，不依赖分布式锁。

## 9. 安全设计

| 风险 | 对策 |
|------|------|
| 身份自签（前端声明 studentId） | 身份改由服务端决定：`POST /token` 下发 HMAC 签名的匿名身份 Cookie（HttpOnly + SameSite=Lax），服务端只验签不存储；前端传什么 studentId 都不影响归属 |
| 匿名/越权上传 | `pre-create` 强校验 HMAC 凭证，且 `studentId` 必须与凭证一致 |
| 凭证泄漏 | 只放 `Upload-Metadata`，`pre-create` 后从元数据中剔除，不写入 `.info` |
| 超大文件打满磁盘 | 前端 `restrictions.maxFileSize` + `pre-create` 校验 + tusd `-max-size` 三层 |
| 可执行文件/脚本上传 | 扩展名白名单（服务端判定，前端限制仅为体验） |
| 路径穿越 / 重名覆盖 | 对象 key 用 tusd 生成的 uploadID，用户文件名不参与路径 |
| 对象存储被直连 | 桶私有（`mc anonymous set none`），RustFS 端口仅内网/本机调试暴露 |
| 下载/删除越权（IDOR） | 按 `id + 会话 studentId` 联合查询，非本人一律 404，不区分“不存在”与“非你所有” |
| tus 续传被截获 | **协议级限制，无法在代码中根治**（tusd 官方无逐请求鉴权）。缓解：`uploadUrl` 视同密码，不入日志；会话 Cookie 为 HttpOnly；超时未完成上传由清理任务回收，缩短 URL 有效窗口 |
| 跨域与预检 | 全站同源（Nginx 统一 `/api` 与 `/files`），开发期 Vite `xfwd` 代理 |
| 代理绕过限流 | tusd 不对公网暴露，仅 Nginx 可达 |

**关于预签名下载**：S3 SigV4 把 Host 纳入签名，若用内网 endpoint 生成预签名 URL，
浏览器访问公网域名会 `SignatureDoesNotMatch`。因此默认采用**后端流式透传**，
部署简单且无 Host 绑定问题；确有 CDN 需求时再引入独立公网 endpoint 生成预签名。

## 10. 容量与性能

| 项 | 计算 | 结论 |
|----|------|------|
| 前端内存占用 | 单分片 8MB × 并发 3 | 约 24MB 峰值，移动端可接受 |
| tusd 内存占用 | part 16MB × 缓冲 4 × 并发上传数 | 单上传 ≤64MB，10 并发 ≤640MB，需按实例规格调小 |
| 单对象上限 | 16MB × 10000 parts | 约 160GB（S3 Multipart 上限） |
| 单文件上限 | 业务限制 5GB | 远低于协议上限，留足余量 |
| 下载 | `InputStreamResource` 流式转发 | 不整块读入堆，内存与文件大小无关 |

调优建议：

- 千兆内网、大文件为主：`chunkSize=16MB`、`s3-part-size=32MB`，减少请求数；
- 移动网络、丢包多：保持 `chunkSize=8MB`，缩短单片重传代价；
- 并发上传数由 `app.upload.max-concurrent-uploads` 与 tusd 内存共同决定，不要盲目调大。

## 11. 可靠性

| 场景 | 行为 |
|------|------|
| 单片网络失败 | tus-js 按 `[0,1s,3s,5s,10s]` 重试，仍失败则前端显示"失败"可手动重试 |
| 浏览器刷新 | GoldenRetriever 恢复 `uploadUrl`：≤200MB 直接点「开始上传」从 offset 续传；更大的显示「需重选」，重新选择同一文件后按指纹续传 |
| 关闭标签页 | 同上（状态保留 24 小时，`expires` 可调） |
| tusd 重启 | S3 store 无状态，`HEAD` 从 RustFS 读取 offset，继续上传 |
| 后端短暂不可用 | `pre-create` 拒绝新上传；`post-*` 由 tusd 重试 3 次 |
| 用户取消 | `DELETE` 清理分片；若前端直接关闭，未完成 upload 的分片成为孤儿 |

**已知待办**：孤儿分片清理。S3 未完成的 multipart upload 不会自动消失，
建议增加定时任务：按 `upload_files.status=UPLOADING AND created_at < now()-24h`
调用 tusd `DELETE`，或在 RustFS 配置生命周期规则 `AbortIncompleteMultipartUpload`。

## 12. 部署与验收

```bash
# 1. 启动全套（含 RustFS / tusd）
docker compose -f docker/docker-compose.yml up -d

# 2. 确认组件
curl -s http://localhost:9000/health              # RustFS
curl -sI http://localhost:1080/files/ | head -5    # tusd（应返回 404/405，但带 Tus-Resumable）
curl -s http://localhost:8080/api/upload/config    # 业务配置
```

本地开发（后端跑在宿主机、只有 RustFS/tusd 在容器里）时，tusd 的回调地址要指向宿主机：

```bash
TUSD_HOOKS_URL=http://host.docker.internal:8080/api/upload/hooks \
  docker compose -f docker/docker-compose.yml up -d rustfs tusd
```

`run.sh` / `run.bat` 已按这种方式设置，无需手工处理。

手工验证 tus 流程（不依赖前端）：

```bash
# 取凭证：无请求体。服务端建立匿名会话并下发 HttpOnly Cookie，需带 Cookie 才能查列表
RESP=$(curl -s -c /tmp/upload-cookie.txt -X POST http://localhost:8080/api/upload/token)
TOKEN=$(echo "$RESP" | jq -r .data.token)
SID=$(echo "$RESP" | jq -r .data.studentId)      # 匿名会话 ID（100000 起，非 1）

# 创建上传（100 字节）
META="filename $(printf 'demo.txt' | base64),filetype $(printf 'text/plain' | base64),studentId $(printf %s "$SID" | base64),uploadToken $(printf %s "$TOKEN" | base64)"
LOC=$(curl -s -D - -o /dev/null -X POST http://localhost/files/ \
  -H 'Tus-Resumable: 1.0.0' -H 'Upload-Length: 100' -H "Upload-Metadata: $META" \
  | grep -i '^location:' | tr -d '\r' | awk '{print $2}')
echo "$LOC"

# 发送数据
head -c 100 /dev/zero > /tmp/demo.bin
curl -s -X PATCH "$LOC" -H 'Tus-Resumable: 1.0.0' \
  -H 'Upload-Offset: 0' -H 'Content-Type: application/offset+octet-stream' \
  --data-binary @/tmp/demo.bin -D -

# 查询列表（必须带会话 Cookie；不带会得到 401）
curl -s -b /tmp/upload-cookie.txt 'http://localhost:8080/api/upload/files'

# 范围下载：验证 Range 是否生效，应返回 206 + Content-Range
curl -s -b /tmp/upload-cookie.txt -D - -o /dev/null \
  -H 'Range: bytes=0-9' 'http://localhost:8080/api/upload/files/1/download'
```

### 排错对照表

| 现象 | 原因 | 处理 |
|------|------|------|
| 413 Request Entity Too Large | Nginx 默认 1m | `client_max_body_size 0` |
| 401 + "上传凭证已过期" | token 超过 120 分钟 | 重新获取 token 再上传 |
| `SignatureDoesNotMatch` | 预签名 Host 与访问 Host 不一致 | 改用流式下载，或为公网域名单独签名 |
| `Upload-Offset` 409 | 并发 PATCH 同一 upload | 前端保证单文件串行（Uppy 默认） |
| Hook 超时 | 回调里做了慢操作 | 只做校验+单表写入，重活异步 |
| 对象存在但列表没有 | `post-finish` 回调失败 | 查后端日志；tusd 重试 3 次后需人工补录 |
| 大文件卡在 99% | 末位 part 未完成/Complete 失败 | 看 tusd 日志的 `CompleteMultipartUpload` 错误 |

### 联调实测踩过的坑（勿回退）

| 现象 | 根因 | 现有实现 |
|------|------|----------|
| `column "meta_json" is of type jsonb but expression is of type character varying`（SQLState 42804） | Hibernate 把 `String` 字段当 varchar 绑定，PostgreSQL 不做隐式转换 | 实体改用 `Map<String,String>` + `@JdbcTypeCode(SqlTypes.JSON)`，直接以 jsonb 参数写入 |
| `value too long for type character varying(64)` | tusd 的 S3 store 会给上传 ID 加版本后缀（`<uuid>+<base64>`），实测 150+ 字符 | `upload_id` 设为 `varchar(255)`；已有库需手工 `ALTER TABLE upload_files ALTER COLUMN upload_id TYPE varchar(255)` |
| 删除成功但文件仍在列表里 | `post-terminate` 是 **DELETE 返回 204 之后**才发的异步回调，把 `DELETED` 覆盖回 `TERMINATED` | `postTerminate` 改为 SQL 条件更新（`... where status <> DELETED`），由数据库决定先后 |
| `rustfs-init` 容器起不来，`minio/mc` 拉取 denied | MinIO 已从 Docker Hub 下架该镜像 | 删除该一次性容器，改由 `RustFsBucketInitializer` 在启动时自建私有桶（可用 `auto-create-bucket: false` 关闭） |
| RustFS 启动即退出：`local erasure endpoints must use distinct physical disks` | 单机 4 个卷落在同一物理盘，擦除模式的安全校验拒绝启动 | compose 里设 `RUSTFS_UNSAFE_BYPASS_DISK_CHECK=true`（仅限本地/CI；生产用真实多盘） |
| Windows 下 `mvn spring-boot:run` 报 `ClassNotFoundException: LearningApplication` | 插件把 classpath 写入 UTF-8 的 `.argfile`，JDK 按 GBK 解析，中文路径变乱码 | 用 `backend/run.bat`（打包后 `java -jar`），详见 [dev-guide.md](dev-guide.md) 10.1 |
| 上传一直「等待中」，或 `POST /files/` 返回 400「缺少文件名」 | `@uppy/tus` 是从**过滤后的 meta** 里取 `name→filename` 的；`allowedMetaFields` 只写 `['studentId','uploadToken']` 时 `name` 被过滤掉，`filename` 就不会被发出 | `allowedMetaFields` 必须含 `name`、`type`：`['name','type','studentId','uploadToken']` |
| 刷新后点「开始上传」毫无反应（一直停在原进度/等待中） | GoldenRetriever 会把 `progress.uploadStarted` 一起恢复，而 `upload()` 只调度「未开始」的文件 | `restored` 事件里调 `requeueStuckFiles()` 把未完成文件的 `uploadStarted/bytesUploaded/percentage` 归零并清空残留批次，由 tus 用 `file.tus.uploadUrl` 去 HEAD 真实偏移 |
| 点「开始上传」零反应，一直「等待中」，**tusd 侧没有任何请求**，且 `upload()` 返回 `successful:0, failed:0` | `upload()` 只调度 `!progress.uploadStarted && !currentUploads[*].fileIDs.includes(id)` 的文件。上次上传异常中断（刷新 / HMR / 请求被取消）会残留 `currentUploads` 批次或僵尸 `uploadStarted`，导致 `waitingFileIDs` 为空 —— **Uppy 对此不报错**，而是建一个空批次静默跑完并 `emit complete`，前端表现为「点了没反应、进度永远 0%」 | `startUpload` 先调 `requeueStuckFiles()`：清空 `currentUploads`、把 `allowNewUpload` 置回 true、归一化未完成文件的 `uploadStarted`。`upload()` 返回后再用 `rescueSkippedFiles()` 检查仍未完成的文件，逐个 `retryUpload()`（内部 `forceAllowNewUpload`，直接按 fileID 建批次，绕开所有残留判断）兜底 |
| 刷新后大文件续传报 `File data is empty` | GoldenRetriever 的 IndexedDB 默认只缓存 **≤10MB** 的 blob，超限的文件恢复后没有内容 | 放宽 `indexedDB.maxFileSize`（本项目 200MB）；仍超限的会被标记 `isGhost`，界面显示「需重选」 |
| 「需重选」的文件怎么续传 | 浏览器已丢弃文件内容，无法凭空续传 | 重新选择同一个文件（同名同大小同 mtime），`@uppy/tus` 会用指纹 `findPreviousUploads()` 命中上次 uploadUrl，从断点继续 |
| 已上传列表出现 `UPLOADING` 记录但没文件 | 中断的上传在 `post-create` 就入库了 | 列表只返回 `COMPLETED`；残留记录可忽略，或在清理任务里按 `updated_at` 归档 |

## 13. 演进方向

1. **鉴权接入**：把 HMAC 凭证换成项目统一 JWT，`pre-create` 解析 `Authorization`（tusd 已透传该头）；
2. **病毒扫描/内容审核**：`post-finish` 后发消息到 RabbitMQ，异步扫描并回写状态；
3. **文本抽取与向量化**：上传完成后触发资源解析，写入 Qdrant，接入资源推荐；
4. **预签名直传**：需要绕开 tusd 带宽瓶颈时，可让 tusd 返回 S3 预签名 part URL（tusd 已支持 `-s3-*` 系列，但会失去 offset 协议统一管理，需权衡）；
5. **多实例**：tusd 用 Redis 或 S3 存储锁（`-backend` 组合），Nginx 按 uploadId 一致性哈希；
6. **生命周期治理**：RustFS 配置未完成分片 24h 自动清理，DB 增加孤儿记录巡检任务。
