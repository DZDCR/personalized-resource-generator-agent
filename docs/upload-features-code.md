# 文件系统主要功能与代码对照

| 项 | 内容 |
|---|---|
| 文档名 | 文件系统主要功能与代码对照 |
| 版本 | v1.0（对应 2026-11-20 22:43 打包的代码） |
| 范围 | 上传、下载、删除、清理四类能力，共 9 个功能点 |
| 用法 | 按「功能」查「代码在哪、这段代码在做什么」 |
| 配套 | `upload-tutorial.md`（原理讲解）、`upload-design.md`（配置与排错） |

## 目录

1. 文档说明
2. 功能全景
3. 功能一：配置下发
4. 功能二：身份识别
5. 功能三：上传凭证
6. 功能四：上传准入校验
7. 功能五：上传生命周期落库
8. 功能六：文件列表
9. 功能七：文件下载与 Range
10. 功能八：文件删除
11. 功能九：残留清理
12. 前端配合要点
13. 三条端到端链路
14. 代码索引总表
15. 改动前必看的三条约定

## 1. 文档说明

本文回答一个具体问题：**某个功能，代码到底在哪，那几行在干什么。**

与另外两份文档的分工是清楚的。`upload-tutorial.md` 讲"为什么这么设计"，适合教学；`upload-design.md` 讲"怎么配、出错怎么查"，适合运维。本文只做一件事——把功能拆成可定位的代码片段，逐段说明它的职责与边界。

行号一律写成 `文件:行号` 的形式，并给出方法名。行号基于 2026-11-20 22:43 的代码版本；只要方法名没改，即使行号漂移也能靠搜索方法名定位。

阅读建议：先看第 2 节的功能全景表，找到你要的功能，再跳到对应小节；第 14 节是全量反查表，适合"我知道代码在哪，想知道它属于哪个功能"的场景。

> 所有行号均已对照当前源码逐条核验。若你改了代码导致行号偏移，以方法名为准。

## 2. 功能全景

系统对外只有 5 个 HTTP 接口加 1 个 Hook 回调入口，但内部拆成了 9 个功能点。下表是功能到代码的第一层映射。

| 功能 | 对外入口 | 核心实现 | 一句话职责 |
|---|---|---|---|
| 配置下发 | `GET /api/upload/config` | `UploadFileController:50` | 把分片大小、大小上限、扩展名白名单交给前端 |
| 身份识别 | 所有接口的前置步骤 | `CurrentUserService:63` | 从签名 Cookie 解出当前 studentId |
| 上传凭证 | `POST /api/upload/token` | `UploadTokenService:35` | 签发 2 小时有效的 HMAC 凭证 |
| 上传准入校验 | `POST /api/upload/hooks`（pre-create） | `UploadFileService:52` | 验凭证、验大小、验扩展名，不合格直接拒 |
| 生命周期落库 | 同上（post-create / finish / terminate） | `UploadFileService:80` 起 | 把上传的三个阶段写成数据库状态 |
| 文件列表 | `GET /api/upload/files` | `UploadFileService:151` | 只返回本人已完成的文件 |
| 文件下载 | `GET /api/upload/files/{id}/download` | `UploadFileController:86` | 流式透传，支持 Range 与缓存协商 |
| 文件删除 | `DELETE /api/upload/files/{id}` | `UploadFileService:196` | 让 tusd 清分片，记录置 DELETED |
| 残留清理 | 定时任务（每日 03:17） | `UploadCleanupJob:49` | 回收超时未续传的上传与分片 |

这里有一条贯穿全局的线索：**身份只在服务端产生**。第 3 节的配置接口不涉及身份，第 4 节之后的每个接口都要先过 `CurrentUserService`，第 6 节的准入校验再验一次 HMAC 凭证。理解这一点，后面九个小节会顺很多。

## 3. 功能一：配置下发

### 3.1 这个功能解决什么

前端不能把"分片 8MB""单文件 5GB""允许哪些扩展名"写死在代码里，否则改一条限制就得重新发版。配置下发把这三个值连同 tus 端点一起交给前端，前端据此设置 Uppy 的限制器。

### 3.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `UploadFileController.java:50` | `config()` | 组装响应 |
| `UploadProperties.java:19` | `chunkSize` | 分片大小，默认 8MB |
| `UploadProperties.java:22` | `maxConcurrentUploads` | 并发数，默认 3 |
| `UploadProperties.java:67` | `maxFileSize` | 单文件上限，默认 5GB |
| `UploadProperties.java:69` | `allowedExtensions` | 扩展名白名单 |

### 3.3 关键代码

```java
// UploadFileController.java:50
@GetMapping("/config")
public ApiResult<UploadConfigResponse> config() {
    return ApiResult.success(new UploadConfigResponse(
            properties.getTusd().getPublicPath(),      // /files/
            properties.getChunkSize(),                  // 8388608
            properties.getSecurity().getMaxFileSize(),  // 5GB
            properties.getSecurity().getAllowedExtensions(),
            properties.getMaxConcurrentUploads()));     // 3
}
```

### 3.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| `getPublicPath()` | 返回 `/files/`，这是浏览器访问 tusd 的**同源**路径，不是 tusd 的真实地址 |
| `getChunkSize()` | 8MB。选择依据见第 13 节：它是 16MB 的 S3 part 的整数分之一 |
| `getMaxFileSize()` | 下发到前端只是**体验层**限制，服务端在 pre-create 还会再验一次 |
| `getAllowedExtensions()` | 同样，前端限制能被绕过，服务端才是判定方 |

### 3.5 注意

前端拿到的 `maxFileSize` 与 `allowedExtensions` 只起"提前告知"作用。真正拦截发生在第 6 节的 `preCreate`，两处判断条件来自同一个 `UploadProperties`，所以不会出现"前端允许、后端拒绝"的不一致。

## 4. 功能二：身份识别

### 4.1 这个功能解决什么

早期版本里 `studentId` 是前端请求体传进来的，服务端照单全收——等于自己填身份证号、服务端直接盖章。所有归属校验因此形同虚设。现在改为：studentId 只存在于服务端签名的 Cookie 里，前端既无法声明也无法篡改。

### 4.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `CurrentUserService.java:63` | `resolveOrCreate()` | 有会话则复用，没有则新建并下发 Cookie |
| `CurrentUserService.java:87` | `require()` | 列表、下载、删除用；无会话直接 401 |
| `CurrentUserService.java:96` | `find()` | 从 Cookie 解析，不创建 |
| `CurrentUserService.java:110` | `encode()` | 拼 `v1.sid.exp.sig` |
| `CurrentUserService.java:116` | `decode()` | 验签 + 验过期 + 取 sid |
| `CurrentUserService.java:143` | `sign()` | HMAC-SHA256 |

### 4.3 关键代码

```java
// CurrentUserService.java:110
private String encode(long studentId, long expiresAt) {
    String payload = VERSION + "." + studentId + "." + expiresAt;
    return payload + "." + sign(payload);
}

// CurrentUserService.java:116
private Long decode(String value) {
    String[] parts = value.split("\\.");
    if (parts.length != 4 || !VERSION.equals(parts[0])) return null;
    String payload = parts[0] + "." + parts[1] + "." + parts[2];
    // 常量时间比对，避免用响应时间差逐字节猜签名
    if (!MessageDigest.isEqual(sign(payload).getBytes(UTF_8), parts[3].getBytes(UTF_8))) return null;
    long expiresAt = Long.parseLong(parts[2]);
    if (expiresAt <= Instant.now().getEpochSecond()) return null;
    return Long.parseLong(parts[1]);
}
```

### 4.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| `VERSION` 段 | 格式版本号。将来换签名算法时可同时接受 v1/v2，不必让所有人重新登录 |
| `sign(payload)` | 对前三段做 HMAC-SHA256，密钥取 `app.upload.security.signing-key` |
| `MessageDigest.isEqual` | 常量时间比较。普通 `String.equals` 会在首个不同字符处返回，攻击者可据此逐字节猜签名 |
| 过期判断放在验签之后 | 先确认这段数据是服务端签发的，再谈它是否过期；顺序反了会泄露"签名对不对"的信息 |
| `ANONYMOUS_BASE` 与负数 | 匿名 ID 取负数（约 -1e6 到 -1e10），与真实用户 ID（正数）天然隔离 |

### 4.5 为什么不存 Redis

会话内容只有「一个 ID + 一个过期时间」，签名后放进 Cookie 就够，服务端**不需要任何存储**就能验真伪。这省掉一个必须常驻的中间件，也省掉"Redis 挂了上传全挂"的故障面。代价是服务端无法主动踢人——等登录上线换成真实会话表即可，接口不用改。

## 5. 功能三：上传凭证

### 5.1 这个功能解决什么

tusd 是独立进程，它不认识 Spring 的会话。所以业务侧签发一张**短时凭证**，前端把它塞进 tus 的 `Upload-Metadata`，tusd 回调 pre-create 时由业务侧验这张票。

### 5.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `UploadTokenService.java:35` | `issue()` | 生成 `v1.sid.exp.sig`，有效期 120 分钟 |
| `UploadTokenService.java:46` | `verify()` | 校验格式、过期、签名，返回 studentId |
| `UploadTokenService.java:75` | `sign()` | HMAC-SHA256，Base64URL 无填充 |
| `UploadFileController.java:66` | `token()` | 接口入口，先建会话再签票 |

### 5.3 关键代码

```java
// UploadTokenService.java:35
public IssuedToken issue(Long studentId) {
    Instant expiresAt = Instant.now().plus(Duration.ofMinutes(
            properties.getSecurity().getTokenTtlMinutes()));
    String payload = VERSION + "." + studentId + "." + expiresAt.getEpochSecond();
    return new IssuedToken(payload + "." + sign(payload), expiresAt);
}

// UploadFileController.java:66
@PostMapping("/token")
public ApiResult<UploadTokenResponse> token(HttpServletRequest request, HttpServletResponse response) {
    long studentId = currentUser.resolveOrCreate(request, response);   // 身份由服务端定
    UploadTokenService.IssuedToken issued = uploadTokenService.issue(studentId);
    return ApiResult.success(new UploadTokenResponse(studentId, issued.token(), issued.expiresAt()));
}
```

### 5.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| 接口无请求体 | 前端不能再声明"我是几号学生"；身份只能从 Cookie 来 |
| `resolveOrCreate` 的副作用 | 首次访问会 `Set-Cookie`，后续请求复用同一个会话 |
| `tokenTtlMinutes = 120` | 凭证短于会话（30 天），因为凭证会进 tusd 的 `.info` 与网络请求，暴露面更大 |
| 返回体里的 `studentId` | 供前端写入 tus 元数据并回显，不是让前端"声明"——pre-create 会拿它和凭证比对 |

### 5.5 注意

凭证有效期与 2 小时绑定，但**会话有 30 天**。大文件跨夜续传时凭证会过期，前端 `ensureToken` 在每次点「开始上传」时强制刷新，所以不会卡住。

## 6. 功能四：上传准入校验

### 6.1 这个功能解决什么

这是整条链路上唯一的**准入闸门**。tusd 收到创建请求后同步回调这里，校验不通过就返回 RejectUpload，客户端拿到 4xx，分片一个字节都不会写。

### 6.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `UploadHookController.java:30` | `handle()` | 按 hook 类型分发 |
| `UploadFileService.java:52` | `preCreate()` | 校验编排，返回放行或拒绝 |
| `UploadFileService.java:242` | `resolveStudentId()` | 验凭证 + 比对 studentId |
| `UploadFileService.java:268` | `validateSize()` | 拒绝延迟长度、拒绝 0 字节与超限 |
| `UploadFileService.java:281` | `validateExtension()` | 扩展名白名单 |
| `UploadFileService.java:292` | `sanitizeFileName()` | 清洗文件名 |

### 6.3 关键代码

```java
// UploadFileService.java:52
public TusdHookResponse preCreate(TusdHookRequest request) {
    TusdHookRequest.Upload upload = requireUpload(request);
    Map<String, String> meta = safeMeta(upload);
    try {
        long studentId = resolveStudentId(meta, request);
        validateSize(upload);
        String fileName = sanitizeFileName(meta.get("filename"));
        validateExtension(fileName);

        Map<String, String> persisted = new LinkedHashMap<>();
        persisted.put("filename", fileName);
        persisted.put("filetype", Optional.ofNullable(meta.get("filetype"))
                .filter(v -> !v.isBlank()).orElse("application/octet-stream"));
        persisted.put("studentId", Long.toString(studentId));
        // 不把 uploadToken 回写：凭证只在本次请求里使用
        return TusdHookResponse.changeMeta(persisted);
    } catch (BizException ex) {
        return TusdHookResponse.reject(normalizeStatus(ex.getCode()), ex.getMessage());
    }
}
```

### 6.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| 第一步验身份 | 顺序刻意如此：身份不明就不必再谈大小与类型 |
| `validateSize` | `sizeIsDeferred` 一律拒——延迟长度无法在准入阶段判断是否超限 |
| `sanitizeFileName` | 去路径分隔符与控制字符，只留纯文件名 |
| `validateExtension` | 取最后一个点之后的部分做白名单比对，服务端判定 |
| `changeMeta(persisted)` | 用清洗后的元数据**覆盖**客户端上报值，后续 post-* 只信这一份 |
| 不回写 `uploadToken` | 凭证若留在 tusd 的 `.info` 里，等于把票长期存在磁盘上 |

### 6.5 拒绝时的返回

`reject(code, msg)` 会变成 tusd 的 4xx。业务码到 HTTP 码的换算在 `normalizeStatus()`：401/403 关系到"你需要重新取票"，400/413/415 关系到"这个文件本身不行"。前端据此提示不同文案。

## 7. 功能五：上传生命周期落库

### 7.1 这个功能解决什么

tusd 只关心分片，不关心业务。业务侧需要在三个时间点建记录、补对象位置、处理终止，使数据库状态与存储状态一致。

### 7.2 代码位置

| 位置 | 方法 | 触发时机 | 是否事务 |
|---|---|---|---|
| `UploadFileService.java:80` | `postCreate()` | 上传 ID 生成后 | 否 |
| `UploadFileService.java:94` | `postFinish()` | 分片合并完成 | 否 |
| `UploadFileService.java:130` | `postTerminate()` | 客户端取消或被终止 | 是 |
| `UploadFileService.java:332` | `newEntity()` | 三条路径共用的构造 | — |
| `UploadFileRepository.java:42` | `updateStatusIfCurrent()` | 条件更新，供清理任务用 | — |
| `UploadFileRepository.java:59` | `updateStatusUnless()` | 条件更新，供 terminate 用 | — |

### 7.3 关键代码

```java
// UploadFileService.java:94 —— post-finish 的核心几行
UploadFileEntity entity = repository.findByUploadId(upload.getId())
        .orElseGet(() -> newEntity(upload, UploadFileEntity.UploadStatus.COMPLETED));

Map<String, String> storage = upload.getStorage();
if (storage != null && storage.get("Key") != null) {
    entity.setStorageType(storage.get("Type"));
    entity.setBucket(storage.get("Bucket"));
    entity.setObjectKey(storage.get("Key"));
}
entity.setStatus(UploadFileEntity.UploadStatus.COMPLETED);
entity.setFinishedAt(Instant.now());
repository.save(entity);

// UploadFileService.java:130 —— post-terminate 的条件更新
int updated = repository.updateStatusUnless(upload.getId(),
        UploadFileEntity.UploadStatus.TERMINATED,
        UploadFileEntity.UploadStatus.DELETED,
        Instant.now());
```

### 7.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| `postCreate` 先查再存 | tusd 在网络抖动时会重发 hook，`findByUploadId` 存在就返回，避免重复插入 |
| `orElseGet(... COMPLETED)` | 兜底：post-create 那次没落库时补一条，否则上传成功的文件查不到 |
| `storage.get("Key")` | 对象在 RustFS 里的真实位置，只有 post-finish 才有，这是"能下载"的前提 |
| `offset != size` 告警 | 二者不等说明上传被截断，对象内容不完整，日志留痕便于排查 |
| `updateStatusUnless` | **不能"先查再改"**：业务删除会调 tusd DELETE，而 tusd 在 204 之后才异步回抛 post-terminate，先查再改会把刚写的 DELETED 覆盖回 TERMINATED，已删文件重新出现在列表里 |

### 7.5 状态机

五个状态由四条路径写入，互斥关系靠 SQL 条件更新保证。

| 状态 | 由谁写入 | 含义 | 是否可下载 |
|---|---|---|---|
| `UPLOADING` | post-create | 分片上传中 | 否 |
| `COMPLETED` | post-finish | 合并完成，有对象 | 是 |
| `TERMINATED` | post-terminate | 客户端取消 | 否 |
| `DELETED` | 业务删除接口 | 用户主动删 | 否 |
| `EXPIRED` | 清理任务 | 超时未续传 | 否 |

枚举定义在 `UploadFileEntity.java:27`，五个常量分别在 29、31、33、35、37 行。

## 8. 功能六：文件列表

### 8.1 这个功能解决什么

返回"我上传的、能下载的文件"。三个限定词缺一不可：只返回本人（归属校验）、只返回已完成（可下载）、按时间倒序。

### 8.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `UploadFileController.java:73` | `files()` | 入口，取会话里的 studentId |
| `UploadFileService.java:151` | `listByStudent()` | 查询并转 DTO |
| `UploadFileRepository.java:23` | `findByStudentIdAndStatusOrderByCreatedAtDesc` | 状态过滤下推到 SQL |
| `UploadFileService.java:352` | `toSummary()` | 实体转摘要，拼下载地址 |

### 8.3 关键代码

```java
// UploadFileController.java:73
@GetMapping("/files")
public ApiResult<List<UploadFileSummary>> files(HttpServletRequest request) {
    return ApiResult.success(uploadFileService.listByStudent(currentUser.require(request)));
}

// UploadFileService.java:151
public List<UploadFileSummary> listByStudent(Long studentId) {
    return repository
            .findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, UploadFileEntity.UploadStatus.COMPLETED)
            .stream()
            .map(this::toSummary)
            .toList();
}
```

### 8.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| `currentUser.require(request)` | 无会话抛 401，不再接受 `?studentId=` 参数 |
| `findByStudentIdAndStatus...` | 两个条件都在 SQL 里，不把该学生全部历史取回内存过滤 |
| 只取 `COMPLETED` | UPLOADING 是断点残留、TERMINATED 是取消、DELETED 已删、EXPIRED 已回收，都没有可用对象 |
| `toSummary` | 拼 `downloadUrl`、格式化大小与时间，不返回 bucket/objectKey 这类内部字段 |

### 8.5 与改进前的差别

改进前是 `findAll()` 后 `filter(r -> r.getStudentId() == null || r.getStudentId().equals(studentId))`——两个问题叠加：全表扫描，以及 `studentId == null` 的历史脏数据被算成"我的"。现在条件全在 SQL，且不再有 null 兜底。

## 9. 功能七：文件下载与 Range

### 9.1 这个功能解决什么

学习平台上的文件多是 pptx、mp4、大 PDF。没有 Range 支持时，视频不能拖进度条、大文件断点下载要重头来。此外下载链接是浏览器直接打开的（`window.open`），失败必须返回真实状态码，否则用户看到的是一段 JSON 或空白页。

### 9.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `UploadFileController.java:86` | `download()` | 组装 200/206/304，透传流 |
| `UploadFileController.java:117` | `ifNoneMatch` 判断 | 全量请求的缓存协商 |
| `UploadFileController.java:161` | `handleRangeNotSatisfiable()` | 416 响应 |
| `UploadFileService.java:169` | `openDownload()` | 归属校验、越界转换、组装载荷 |
| `RustFsStorageService.java:46` | `openObject(3 参)` | 把 Range 透传给 S3 |

### 9.3 关键代码

```java
// RustFsStorageService.java:46 —— Range 原样交给存储端
GetObjectRequest.Builder builder = GetObjectRequest.builder()
        .bucket(defaultBucket(bucket)).key(objectKey);
if (range != null && !range.isBlank()) {
    builder.range(range);
}
return s3Client.getObject(builder.build());

// UploadFileController.java:128 —— 按是否有 contentRange 决定 200 还是 206
ResponseEntity.BodyBuilder builder = ResponseEntity
        .status(payload.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .contentType(mediaType(file.getContentType()))
        .contentLength(payload.contentLength());
if (payload.contentRange() != null) {
    builder.header(HttpHeaders.CONTENT_RANGE, payload.contentRange());
}
```

### 9.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| `builder.range(range)` | 服务端不切分，只把 `bytes=0-1023` 转发给 S3，由存储端只回目标字节 |
| `payload.partial()` | 判断依据是响应里有没有 `Content-Range`，而不是"请求头里有没有 Range"——S3 若忽略了 Range 仍应回 200 |
| `contentLength(payload.contentLength())` | 取 S3 返回的实际长度，分段时是段长而非文件全长 |
| 304 分支 | 仅在**全量**请求时生效；带 Range 的请求必须回数据段 |
| `Cache-Control` 分流 | 分段响应 `no-store`，避免中间层把某一段当成完整对象缓存 |
| `inline` 参数 | 传 `?inline=true` 时用 `Content-Disposition: inline`，视频/PDF 可直接预览 |

### 9.5 416 的特殊处理

越界由 `openDownload` 捕获 S3 的 416 后抛 `RangeNotSatisfiableException`，再由 Controller 内的 `@ExceptionHandler` 转成真正的 416。**不能交给全局异常处理器**——它会把异常包成 HTTP 200 + 业务码，浏览器播放器只认真正的 416 + `Content-Range`，收到 200 会把 JSON 当视频数据渲染，表现就是画面卡死。

## 10. 功能八：文件删除

### 10.1 这个功能解决什么

一次删除要同时处理三个东西：tusd 的分片与 `.info`、RustFS 的对象、数据库记录。三者有依赖顺序，且任何一步失败都不能让用户看到"删了但还在"。

### 10.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `UploadFileController.java:149` | `delete()` | 入口，带归属校验 |
| `UploadFileService.java:196` | `delete()` | 编排：先 tusd，后对象，最后改状态 |
| `UploadFileService.java:219` | `get(id, studentId)` | 联合查询，非本人一律 404 |
| `RustFsStorageService.java:71` | `terminate()` | 调 tusd DELETE |
| `RustFsStorageService.java:57` | `deleteObject()` | 兜底直接删对象 |

### 10.3 关键代码

```java
// UploadFileService.java:196
public void delete(Long id, Long studentId) {
    UploadFileEntity entity = get(id, studentId);
    if (entity.getStatus() == UploadFileEntity.UploadStatus.DELETED) return;
    try {
        storageService.terminate(entity.getUploadId());
    } catch (Exception ex) {
        log.warn("tusd 终止失败，改为直接删除对象 uploadId={}: {}", entity.getUploadId(), ex.getMessage());
        storageService.deleteObject(entity.getBucket(), entity.getObjectKey());
    }
    entity.setStatus(UploadFileEntity.UploadStatus.DELETED);
    entity.setUpdatedAt(Instant.now());
    repository.save(entity);
}
```

### 10.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| `get(id, studentId)` | 一次联合查询。若先按 id 查再比对，会把"存在但不属于你"与"不存在"分成两种响应，等于给攻击者一个遍历 id 探测数据的信道 |
| 幂等短路 | 已 DELETED 直接返回，重复点击不报错 |
| 优先 `terminate` | tusd 会清分片、清 `.info`，比直接删对象更干净 |
| 降级 `deleteObject` | tusd 不可用或 upload 已不存在时，至少把对象删掉，不留占空间的孤儿 |
| 不加事务 | 内部有外部 HTTP 与 S3 调用，包进事务会长时间占用数据库连接 |
| 最后才改状态 | 先改状态再删存储，一旦存储删除失败，就变成"记录说删了、对象还在" |

### 10.5 注意

`postTerminate` 会在 tusd 删除成功后异步回调，可能与本方法并发。二者靠 `updateStatusUnless` 的条件更新避免互相覆盖，详见第 7.4 节。

## 11. 功能九：残留清理

### 11.1 这个功能解决什么

学生传到一半关页面，记录会永久停在 `UPLOADING`。记录本身不大，但它在 RustFS 里对应一次未完成的 Multipart 上传，已写入的分片会一直占空间，且不会被任何业务逻辑回收。这是按 GB 计费的真实成本。

### 11.2 代码位置

| 位置 | 方法 | 职责 |
|---|---|---|
| `UploadCleanupJob.java:48` | `@Scheduled` | 每日 03:17 触发 |
| `UploadCleanupJob.java:49` | `purgeStaleUploads()` | 分批扫描超时记录 |
| `UploadCleanupJob.java:87` | `purgeOne()` | 单条回收：清分片 → 改状态 |
| `UploadFileService.java:233` | `expireIfStillUploading()` | 条件更新，带事务边界 |
| `UploadFileRepository.java:29` | `findByStatusAndCreatedAtBefore...` | 按状态 + 时间取批 |
| `RustFsBucketInitializer.java:92` | `applyLifecycle()` | 下发桶生命周期规则 |
| `UploadProperties.java:87` | `uploadingTtlHours` | TTL，默认 168 小时（7 天） |

### 11.3 关键代码

```java
// UploadCleanupJob.java:49
Instant threshold = Instant.now().minus(Duration.ofHours(config.getUploadingTtlHours()));
for (int round = 0; round < MAX_ROUNDS; round++) {
    List<UploadFileEntity> batch = repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            UploadFileEntity.UploadStatus.UPLOADING, threshold, PageRequest.of(0, batchSize));
    if (batch.isEmpty()) break;
    for (UploadFileEntity entity : batch) { if (purgeOne(entity)) expired++; }
    if (batch.size() < batchSize) break;
}

// UploadCleanupJob.java:87 —— 单条回收
try {
    storageService.terminate(uploadId);
} catch (Exception ex) {
    storageService.deleteObject(entity.getBucket(), entity.getObjectKey());  // 兜底
}
boolean expired = uploadFileService.expireIfStillUploading(uploadId);
```

### 11.4 逐段说明

| 代码 | 在做什么 |
|---|---|
| `MAX_ROUNDS = 5` | 长时间停机后可能积压很多，分批跑避免单次执行过久 |
| `batchSize` + 循环 | 每批 100 条，取完不满一批即退出，不必等固定轮数 |
| 先 terminate 后改状态 | 顺序与删除接口一致：先确保存储干净，再推进状态 |
| `expireIfStillUploading` 返回 false | 扫描到处理之间被 post-finish 改成 COMPLETED，属正常竞态，**绝不覆盖** |
| 单独方法 + `@Transactional` | `@Modifying` 更新必须在事务内；而整段方法里夹着 tusd 的 HTTP 调用，不能一起包进事务 |
| `applyLifecycle` | 存储侧兜底：即使任务停摆或记录丢失，分片也会在 7 天后被存储端中止 |

### 11.5 两道网

| 层 | 依赖 | 失效场景 | 兜底效果 |
|---|---|---|---|
| 定时任务 | 应用在跑、记录存在 | 应用停机、记录被误删 | 无 |
| 桶生命周期 | 存储端自身 | 前缀配置不一致 | 无 |

两层各自独立，任一存活即可回收分片。`object-prefix` 必须与 tusd 的 `-s3-object-prefix` 一致，否则规则匹配不到对象——这是最容易配错的一项。

## 12. 前端配合要点

前端不是本文重点，但有三处与后端强耦合，改动时容易踩坑。

### 12.1 分片与重试参数

| 位置 | 参数 | 说明 |
|---|---|---|
| `Upload.vue:243` | `retryDelays` | `[0, 1000, 3000, 5000, 10000]`，5 次退避 |
| `Upload.vue:245` | `removeFingerprintOnSuccess` | 置 true，否则同名文件再次上传会被判成"已完成" |
| `Upload.vue:248` | `allowedMetaFields` | 必须含 `name`、`type`，否则 @uppy/tus 取不到 filename |
| `Upload.vue:255` | GoldenRetriever | 只缓存 ≤200MB 的文件，更大的变 ghost |

### 12.2 凭证刷新

`Upload.vue:312` 的 `ensureToken()` 在每次点「开始上传」时强制刷新凭证。这里有个必须注意的点：`setMeta` 只更新全局 meta，而**文件级 meta 的优先级更高**，所以恢复出来的旧文件不会被它修正。

### 12.3 恢复文件的身份纠正

`Upload.vue:341` 的 `rebindFileIdentity()` 逐个 `setFileMeta`，把当前会话的 studentId 与 token 写回每个未完成文件。不这么做的话，刷新后继续上传会被 pre-create 判"凭证与 studentId 不匹配"而拒绝——表现是"续传永远失败"。

### 12.4 队列空转的两个兜底

`Upload.vue:429` 的 `requeueStuckFiles()` 清理残留批次与僵尸 `uploadStarted`；`Upload.vue:457` 的 `rescueSkippedFiles()` 用 `retryUpload` 把被调度器跳过的文件重新拉起。二者解决的是同一个现象：点「开始上传」无反应、进度一直 0%。

## 13. 三条端到端链路

### 13.1 上传

| 步 | 动作 | 代码位置 |
|---|---|---|
| 1 | 页面加载，取配置 | `UploadFileController.java:50` |
| 2 | 申请凭证（首次同时建会话） | `UploadFileController.java:66` → `CurrentUserService.java:63` |
| 3 | 前端创建 tus 上传 | 前端 Uppy |
| 4 | tusd 回调 pre-create 校验 | `UploadHookController.java:30` → `UploadFileService.java:52` |
| 5 | 落 UPLOADING 记录 | `UploadFileService.java:80` |
| 6 | 分片 PATCH（可中断续传） | 前端 ↔ tusd |
| 7 | 合并完成回调 post-finish | `UploadFileService.java:94` |

### 13.2 下载

| 步 | 动作 | 代码位置 |
|---|---|---|
| 1 | 取会话身份 | `CurrentUserService.java:87` |
| 2 | 按 id + 归属查记录 | `UploadFileService.java:219` |
| 3 | 状态必须是 COMPLETED | `UploadFileService.java:171` |
| 4 | 带 Range 请求对象 | `RustFsStorageService.java:46` |
| 5 | 按有无 Content-Range 回 200/206 | `UploadFileController.java:128` |

### 13.3 删除

| 步 | 动作 | 代码位置 |
|---|---|---|
| 1 | 归属校验 | `UploadFileService.java:219` |
| 2 | 调 tusd DELETE 清分片 | `RustFsStorageService.java:71` |
| 3 | 失败降级删对象 | `RustFsStorageService.java:57` |
| 4 | 记录置 DELETED | `UploadFileService.java:207` |
| 5 | tusd 异步回抛 post-terminate | `UploadFileService.java:130` |

## 14. 代码索引总表

已知代码位置、想反查它属于哪个功能时查这张表。

| 文件 | 行号 | 方法 | 所属功能 |
|---|---|---|---|
| `UploadFileController.java` | 50 | `config()` | 配置下发 |
| `UploadFileController.java` | 66 | `token()` | 上传凭证 |
| `UploadFileController.java` | 73 | `files()` | 文件列表 |
| `UploadFileController.java` | 86 | `download()` | 文件下载 |
| `UploadFileController.java` | 117 | 304 判断 | 缓存协商 |
| `UploadFileController.java` | 149 | `delete()` | 文件删除 |
| `UploadFileController.java` | 161 | `handleRangeNotSatisfiable()` | 416 |
| `UploadHookController.java` | 30 | `handle()` | Hook 分发 |
| `CurrentUserService.java` | 63 | `resolveOrCreate()` | 身份识别 |
| `CurrentUserService.java` | 87 | `require()` | 身份识别 |
| `CurrentUserService.java` | 96 | `find()` | 身份识别 |
| `CurrentUserService.java` | 110 | `encode()` | Cookie 签名 |
| `CurrentUserService.java` | 116 | `decode()` | Cookie 验签 |
| `UploadTokenService.java` | 35 | `issue()` | 上传凭证 |
| `UploadTokenService.java` | 46 | `verify()` | 凭证校验 |
| `UploadFileService.java` | 52 | `preCreate()` | 准入校验 |
| `UploadFileService.java` | 80 | `postCreate()` | 生命周期 |
| `UploadFileService.java` | 94 | `postFinish()` | 生命周期 |
| `UploadFileService.java` | 130 | `postTerminate()` | 生命周期 |
| `UploadFileService.java` | 151 | `listByStudent()` | 文件列表 |
| `UploadFileService.java` | 169 | `openDownload()` | 文件下载 |
| `UploadFileService.java` | 196 | `delete()` | 文件删除 |
| `UploadFileService.java` | 219 | `get()` | 归属校验 |
| `UploadFileService.java` | 233 | `expireIfStillUploading()` | 残留清理 |
| `UploadFileService.java` | 242 | `resolveStudentId()` | 准入校验 |
| `UploadFileService.java` | 268 | `validateSize()` | 准入校验 |
| `UploadFileService.java` | 281 | `validateExtension()` | 准入校验 |
| `UploadFileService.java` | 332 | `newEntity()` | 生命周期 |
| `UploadFileService.java` | 352 | `toSummary()` | 文件列表 |
| `RustFsStorageService.java` | 33 | `openObject(2 参)` | 文件下载 |
| `RustFsStorageService.java` | 46 | `openObject(3 参)` | Range |
| `RustFsStorageService.java` | 57 | `deleteObject()` | 删除兜底 |
| `RustFsStorageService.java` | 71 | `terminate()` | 终止上传 |
| `UploadCleanupJob.java` | 48 | `@Scheduled` | 残留清理 |
| `UploadCleanupJob.java` | 49 | `purgeStaleUploads()` | 残留清理 |
| `UploadCleanupJob.java` | 87 | `purgeOne()` | 残留清理 |
| `RustFsBucketInitializer.java` | 92 | `applyLifecycle()` | 生命周期规则 |
| `UploadFileRepository.java` | 17 | `findByUploadId()` | 生命周期 |
| `UploadFileRepository.java` | 21 | `findByIdAndStudentId()` | 归属校验 |
| `UploadFileRepository.java` | 23 | `findByStudentIdAndStatus...` | 文件列表 |
| `UploadFileRepository.java` | 29 | `findByStatusAndCreatedAtBefore...` | 残留清理 |
| `UploadFileRepository.java` | 42 | `updateStatusIfCurrent()` | 残留清理 |
| `UploadFileRepository.java` | 59 | `updateStatusUnless()` | 生命周期 |
| `UploadFileEntity.java` | 27 | `UploadStatus` 枚举 | 状态机 |
| `Upload.vue` | 243 | `retryDelays` | 前端重试 |
| `Upload.vue` | 248 | `allowedMetaFields` | 前端元数据 |
| `Upload.vue` | 312 | `ensureToken()` | 凭证刷新 |
| `Upload.vue` | 341 | `rebindFileIdentity()` | 身份纠正 |
| `Upload.vue` | 429 | `requeueStuckFiles()` | 队列兜底 |
| `Upload.vue` | 457 | `rescueSkippedFiles()` | 队列兜底 |

### 14.1 按功能反查文件

| 功能 | 涉及文件 |
|---|---|
| 配置下发 | Controller、UploadProperties |
| 身份识别 | CurrentUserService |
| 上传凭证 | UploadTokenService、Controller |
| 准入校验 | UploadHookController、UploadFileService、UploadProperties |
| 生命周期落库 | UploadFileService、UploadFileRepository、UploadFileEntity |
| 文件列表 | Controller、UploadFileService、UploadFileRepository |
| 文件下载 | Controller、UploadFileService、RustFsStorageService |
| 文件删除 | Controller、UploadFileService、RustFsStorageService |
| 残留清理 | UploadCleanupJob、UploadFileService、UploadFileRepository、RustFsBucketInitializer |

## 15. 改动前必看的三条约定

| 约定 | 原因 | 违反后果 |
|---|---|---|
| 状态变更一律用条件更新 | post-terminate 与业务删除存在异步竞态 | 已删文件重新出现在列表 |
| 下载失败必须返回真实 HTTP 码 | 下载链接由浏览器直接打开，不是 axios | 用户看到 JSON 或空白页 |
| `@Modifying` 更新要单独划事务 | 清理任务里夹着 tusd 的 HTTP 调用 | 事务超时、连接池被占 |

三条都不是风格问题，是踩过之后才写下的。改这附近代码时，先在注释里找有没有对应的说明。
