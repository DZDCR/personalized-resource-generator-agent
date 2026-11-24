# 文件上传系统 · 代码走读手册

> 版本：v1.0 | 状态：与当前代码一致（2026-11-20 核对） | 适用：个性化资源生成与学习多智能体系统

**这份文档解决什么问题**：给你一份「代码地图」。每个结论后面都跟着 `文件路径:行号`，你可以直接跳过去看。看完任意一节，你应该能回答三个问题——**这段代码在哪、它在干什么、为什么必须这么写**。

**和另外两份文档的分工**：

| 文档 | 用途 | 什么时候看 |
|---|---|---|
| `docs/upload-tutorial.md` | 讲原理、讲选型、讲怎么讲给别人听 | 要理解「为什么是 tus 不是别的」 |
| `docs/upload-design.md` | 配置表、参数含义、故障对照 | 要部署、要排错 |
| **本文档** | **代码定位、逐段功能、改动索引** | **要读代码、要改功能、要 review** |

---

## 目录

- [1. 三种查法：先选一条入口](#1-三种查法先选一条入口)
- [2. 文件清单与职责边界](#2-文件清单与职责边界)
- [3. 一次上传的完整调用链（带行号）](#3-一次上传的完整调用链带行号)
- [4. 前端 Upload.vue 逐段走读](#4-前端-uploadvue-逐段走读)
- [5. 后端：Hook 入口层](#5-后端hook-入口层)
- [6. 后端：业务服务 UploadFileService](#6-后端业务服务-uploadfileservice)
- [7. 上传凭证 UploadTokenService](#7-上传凭证-uploadtokenservice)
- [8. 对象存储三件套](#8-对象存储三件套)
- [9. 数据模型与 Repository](#9-数据模型与-repository)
- [10. 配置的四层映射关系](#10-配置的四层映射关系)
- [11. 前端路由与两个代理](#11-前端路由与两个代理)
- [12. 反查表：要改 X，改哪里](#12-反查表要改-x改哪里)
- [13. 关键数字速查](#13-关键数字速查)
- [14. 代码与文档的已知偏差](#14-代码与文档的已知偏差)
- [15. 推荐阅读顺序与自测命令](#15-推荐阅读顺序与自测命令)

---

## 1. 三种查法：先选一条入口

不同目的走不同路径，别从头读到尾。

| 你的目的 | 从哪进 | 跳转 |
|---|---|---|
| 「上传按钮点了之后到底跑了什么」 | 调用链 | [第 3 节](#3-一次上传的完整调用链带行号) |
| 「前端这一大坨 700 行在干嘛」 | 前端逐段 | [第 4 节](#4-前端-uploadvue-逐段走读) |
| 「凭证/校验/落库怎么实现的」 | 后端服务 | [第 6 节](#6-后端业务服务-uploadfileservice) |
| 「我要加个新限制/新字段/换存储」 | 反查表 | [第 12 节](#12-反查表要改-x改哪里) |
| 「这个 8388608 是哪来的」 | 数字速查 | [第 13 节](#13-关键数字速查) |

---

## 2. 文件清单与职责边界

### 2.1 后端（Java，`backend/src/main/java/com/example/learning/`）

按「一层的职责」排序，不是按字母序。

| 文件 | 行数 | 职责 | 关键方法 |
|---|---|---|---|
| `controller/UploadFileController.java` | 99 | 面向前端的 5 个 HTTP 接口 | `config` `token` `files` `download` `delete` |
| `controller/UploadHookController.java` | 62 | **面向 tusd 的 Hook 入口**，按 `Type` 分发 | `handle` |
| `service/UploadFileService.java` | 326 | **整个后端最核心的一层**：校验、落库、查询、删除 | `preCreate` 等 4 个 Hook + 4 个业务方法 |
| `service/UploadTokenService.java` | 90 | 无状态 HMAC 凭证签发/校验 | `issue` `verify` `sign` |
| `service/RustFsStorageService.java` | 67 | 对象读取、删除、调 tusd 终止 | `openObject` `deleteObject` `terminate` |
| `config/UploadProperties.java` | 66 | `app.upload.*` 配置绑定 | 内部类 `Tusd`/`Storage`/`Security` |
| `config/RustFsConfig.java` | 35 | 构建 S3Client Bean（强制 path-style） | `rustFsS3Client` |
| `config/RustFsBucketInitializer.java` | 67 | 启动时自检/自建桶 | `run` `createBucket` |
| `entity/UploadFileEntity.java` | 100 | `upload_files` 表映射 + 状态枚举 | `UploadStatus` |
| `repository/UploadFileRepository.java` | 38 | JPA 查询 + **条件更新**（防竞态） | `updateStatusUnless` |
| `dto/upload/TusdHookRequest.java` | 103 | tusd Hook 请求体（PascalCase） | `header()` |
| `dto/upload/TusdHookResponse.java` | 81 | tusd Hook 响应体 | `ok` `reject` `changeMeta` |
| `dto/request/UploadTokenRequest.java` | 11 | `{studentId}` | — |
| `dto/response/UploadConfigResponse.java` | 16 | 下发给 Uppy 的 5 个参数 | — |
| `dto/response/UploadTokenResponse.java` | 14 | `{studentId, token, expiresAt}` | — |
| `dto/response/UploadFileSummary.java` | 19 | 文件列表项 | — |
| `test/.../UploadTokenServiceTest.java` | 59 | 凭证 4 个用例 | — |

### 2.2 前端（`frontend/src/`）

| 文件 | 行数 | 职责 |
|---|---|---|
| `views/Upload.vue` | 717 | **全部上传逻辑在这一个文件里**：模板 1–138，脚本 140–652，样式 654–717 |
| `api/upload.js` | 26 | 5 个接口封装，薄封装 |
| `api/request.js` | 21 | axios 实例，`baseURL: '/api'`，拦截 `code !== 0` |
| `router/index.js` | 13 | 第 6 行注册 `/upload` |
| `vite.config.js` | 21 | 开发期两个代理：`/api`→8080、`/files`→1080 |
| `nginx.conf` | 59 | 生产期同样两个代理，`/files/` 在 28–54 行 |

### 2.3 基础设施

| 文件 | 关键行 | 内容 |
|---|---|---|
| `docker/docker-compose.yml` | 63–97 | rustfs（4 个数据卷 + 权限 helper） |
| 同上 | 119–156 | tusd 服务，含 14 个参数（125–143）与环境变量（144–148） |
| 同上 | 174–180 | backend 侧的 RUSTFS/TUSD 环境变量 |
| `backend/src/main/resources/application.yml` | 67–91 | `app.upload.*` 全部默认值 |

### 2.4 一句话记住职责边界

```
Upload.vue   → 只切分、只发请求，从不合并文件
tusd         → 只收分片、写 S3 Multipart，从不回调业务逻辑（只发 Hook）
SpringBoot   → 只做校验与元数据，从不碰文件字节
RustFS       → 只存对象，在服务端完成 part 合并
```

这条边界在代码里的证据：`UploadFileService.java:26-28`（类注释明确写「不接收文件字节流」）、`RustFsStorageService.java:13-17`（只做读取/删除/终止，没有 put）、`UploadFileController.java:28`（注释「字节传输不经过本服务」）。

---

## 3. 一次上传的完整调用链（带行号）

### 3.1 阶段一：页面初始化

| 步骤 | 代码位置 | 做了什么 |
|---|---|---|
| ① 进入路由 | `frontend/src/router/index.js:6` | 懒加载 `Upload.vue` |
| ② 拉配置 | `Upload.vue:187` → `api/upload.js:4` → `UploadFileController.java:40-48` | `GET /api/upload/config` 拿分片大小/上限/白名单 |
| ③ 建 Uppy | `Upload.vue:209-229` | `new Uppy({autoProceed:false, restrictions})` |
| ④ 装 tus 插件 | `Upload.vue:231-243` | endpoint `/files/`、`chunkSize: 8388608` |
| ⑤ 装 GoldenRetriever | `Upload.vue:248-253` | IndexedDB 缓存，为断点续传兜底 |
| ⑥ 订阅事件 | `Upload.vue:255-295` | 进度、错误、恢复、完成 |
| ⑦ 拉已有列表 | `Upload.vue:189` → `UploadFileController.java:58-61` | `GET /api/upload/files?studentId=1` |

### 3.2 阶段二：选文件

```
点击/拖拽  →  Upload.vue:326-338   （pickFiles / onInputChange / onDrop）
          →  Upload.vue:340-368   addFiles()
              ├─ :346-349  空文件直接跳过
              ├─ :351-356  同名 ghost 占位先清掉
              ├─ :358      uppy.addFile()  ← 交给 Uppy 管
              └─ :360-364  isRestriction 错误单独提示
```

`uppy.addFile` 会在 `Upload.vue:255` 触发 `file-added` → `syncRows()` 重绘表格。这一步**不发任何网络请求**。

### 3.3 阶段三：点「开始上传」——最关键的 13 行

`Upload.vue:459-494`：

```js
async function startUpload() {
  if (!uppy || !rows.value.length) return                    // :460-462
  if (hasPaused.value) { uppy.resumeAll(); return }          // :463-467 有暂停的先继续
  const pending = ... filter(!uploadComplete && !isGhost)     // :469-471
  if (!pending.length) { ...提示... return }                  // :472-479

  try {
    await ensureToken(true)      // :482  ①  强制换新凭证
    requeueStuckFiles()          // :483  ②  洗掉残留调度标记
    busy.value = true            // :484
    await uppy.upload()          // :485  ③  真正开始
    await rescueSkippedFiles()   // :486  ④  兜底：没被调度的逐个补发
    reportOutcome()              // :487  ⑤  统一汇总提示
  } finally {
    busy.value = false; syncRows()   // :490-493
  }
}
```

**为什么是这 5 步，顺序不能换**：

- ① 必须在 ②③ 之前——`requeueStuckFiles` 清的是调度标记，而 `setMeta` 写凭证（`Upload.vue:316`）必须发生在文件被调度前，否则 tus 请求头里没有 `uploadToken`
- ④ 必须在 ③ 之后——它检查的就是「`upload()` 跑完后还有谁没动」
- ⑤ 必须在最后——Uppy 的 `complete` 事件在空批次时也会触发，不能在那里弹提示（见 `Upload.vue:280-286` 的注释）

### 3.4 阶段四：tus 协议握手（网络层）

| 步骤 | 发起方 | 落到服务端 |
|---|---|---|
| 创建上传 `POST /files/` | `@uppy/tus` | → nginx `nginx.conf:28` → tusd → **`UploadHookController.java:29`** |
| ↳ pre-create 校验 | tusd 回调 | `UploadHookController.java:34` → **`UploadFileService.java:48`** |
| ↳ 落 UPLOADING 记录 | tusd 回调 | `UploadHookController.java:35-38` → **`UploadFileService.java:76`** |
| 分片 `PATCH /files/{id}` | `@uppy/tus` | → tusd 直接写 RustFS，**不经过后端** |
| 完成 `post-finish` | tusd 回调 | `UploadHookController.java:39-42` → **`UploadFileService.java:90`** |
| 取消 `post-terminate` | tusd 回调 | `UploadHookController.java:43-46` → **`UploadFileService.java:126`** |

两条重要结论：

1. **SpringBoot 只出现在 Hook 里，不在字节流路径上**。所以后端重启不影响正在传的分片，`server.tomcat.max-swallow-size` 之类参数也不用为上传调整。
2. **`PATCH` 请求根本不进后端**，这是 `nginx.conf:28-54` 的 `location /files/` 直接把流量交给 tusd 的原因。

### 3.5 阶段五：读取与删除

| 操作 | 前端 | 后端 |
|---|---|---|
| 刷新列表 | `Upload.vue:583` | `UploadFileController.java:58` → `UploadFileService.java:147` |
| 下载 | `Upload.vue:593` `window.open` | `UploadFileController.java:64` → `UploadFileService.java:157` |
| 删除 | `Upload.vue:597` 二次确认 | `UploadFileController.java:83` → `UploadFileService.java:170` |

---

## 4. 前端 Upload.vue 逐段走读

整份文件 717 行，只有三段：模板 1–138、脚本 140–652、样式 654–717。**脚本是重点**。

### 4.1 模块级状态（155–176）

```js
const studentId = 1              // :155  TODO 接登录态后替换，现在写死
const rows = ref([])             // :162  队列视图（从 uppy 状态派生）
const serverFiles = ref([])      // :163  已上传列表（来自后端）
const config = ref({...})        // :165-171 默认值兜底，避免 config 未到时崩溃
let uppy = null                  // :173  不放进 ref：Uppy 实例不需要响应式
let credential = {token:'',expiresAt:0}  // :174 凭证内存缓存
let tokenPromise = null          // :175  并发去重用的「进行中 Promise」
const speedTracker = new Map()   // :176  每个文件的测速上一次采样点
```

**`tokenPromise` 为什么需要**（`:310-322`）：如果页面同时触发多次上传（多选文件后 Uppy 并发 3 个），每个都可能调 `ensureToken`。若没有这个变量，会连发 3 次 `/upload/token`。这里用「同一个 Promise 复用」把它收敛成 1 次：

```js
if (!tokenPromise) {
  tokenPromise = createUploadToken(studentId).then(...).finally(() => { tokenPromise = null })
}
return tokenPromise          // :323
```

`.finally` 里置 null 而不是 `.then` 里置——保证失败时也能重试。

### 4.2 initUppy 与「旧实例守卫」（209–219）

```js
if (uppy) {                    // :212
  try { uppy.destroy() } catch (e) {}
  uppy = null
}
```

这三行看起来多余（`onBeforeUnmount:192` 已经 destroy 了），但**不能删**：开发期 Vite 热更新时，组件可能重新挂载而不走 `onBeforeUnmount`，于是两个 Uppy 实例同时存在。两个实例都监听 `state-update` 并写**同一个 IndexedDB 恢复位**，会把 `files` 和 `currentUploads` 搅乱，症状就是「刷新后队列里出现重复文件」或「文件卡在等待中」。

### 4.3 Uppy 配置（221–229）

```js
uppy = new Uppy({
  id: 'resource-uploader',           // :222 固定 id，GoldenRetriever 靠它定位 IndexedDB 记录
  autoProceed: false,                // :223 选完文件不自动开始 —— 这是「等待中」观感的来源之一
  allowMultipleUploadBatches: true,  // :224 允许分多批上传（追加文件时不会清空上一批结果）
  restrictions: {
    maxFileSize: config.value.maxFileSize,                          // :226
    allowedFileTypes: config.value.allowedExtensions.map(e=>`.${e}`) // :227 加点的 glob 形式
  }
})
```

`id` 是硬编码字符串，**改了等于清空所有用户的历史恢复点**——重新部署后用户刷新会丢失未完成队列。

### 4.4 Tus 插件 5 个参数（231–243）

| 行 | 参数 | 值 | 作用 / 为什么 |
|---|---|---|---|
| 232 | `endpoint` | `/files/` | 与 `tusd -base-path=/files/`（compose:138）严格一致 |
| 234 | `chunkSize` | `8MB` | **前端分片粒度**，只影响 PATCH 频率，不影响 S3 part |
| 235 | `limit` | `3` | 并发文件数，与 `app.upload.max-concurrent-uploads` 对齐 |
| 236 | `retryDelays` | `[0,1s,3s,5s,10s]` | 失败自动重试 5 次，指数退避 |
| 238 | `removeFingerprintOnSuccess` | `true` | **同名文件重传的关键**，见下 |

**`removeFingerprintOnSuccess`（`:238`）值得单独讲**。`@uppy/tus` 用「文件名+大小+修改时间」算指纹，存在 localStorage，用来在重选文件时找回上次的 `uploadUrl`。如果为 `false`，你传完 `a.pdf` 再传一次同名的（内容变了但大小恰好相同），tus 会认为「这个指纹我传过」而直接跳到已完成，**服务端根本收不到文件**。置 `true` 让成功后就清掉指纹。

**两个看起来奇怪但必须写的参数**：

```js
allowedMetaFields: ['name', 'type', 'studentId', 'uploadToken'],   // :241
headers: () => (credential.token ? { 'X-Upload-Token': credential.token } : {})  // :242
```

- `:241` —— `@uppy/tus` 生成 `Upload-Metadata` 时，**是从「已被 allowedMetaFields 过滤后的 meta」里取 `name` 来拼 `filename` 的**。漏掉 `name` 会导致请求里没有 `filename` 字段，后端 `sanitizeFileName` 在 `UploadFileService.java:243-245` 抛「缺少文件名」，客户端看到 400。这个坑的实际表现是「选完文件点上传，立刻失败，服务端日志说缺文件名」。
- `:242` —— 用**函数**而不是对象，因为 `credential` 是普通变量，`use(Tus,...)` 只在初始化时读一次；写成对象会永久固化首次的（空）token。写成函数则每次请求现取。

### 4.5 GoldenRetriever（248–253）

```js
uppy.use(GoldenRetriever, {
  indexedDB: {
    maxFileSize: 200 * 1024 * 1024,     // :250 单文件缓存上限
    maxTotalSize: 1024 * 1024 * 1024    // :251 总缓存上限 1GB
  }
})
```

**默认值是 10MB**，这也是必须显式覆盖的原因：一个 50MB 的 PDF 刷新后会被标成 ghost（「需重选」），用户以为功能坏了。放宽到 200MB 后，常见课件/试卷可以刷新即续传；超过 200MB 的（如 465MB 的视频）仍然需要重选，但重选后靠指纹续传，不重传字节。

### 4.6 事件订阅（255–295）

| 行 | 事件 | 处理 |
|---|---|---|
| 255–256 | `file-added` / `file-removed` | 只重绘，不做业务 |
| 257–259 | `upload` | `busy = true` |
| 260–263 | `upload-progress` | 测速 + 重绘 |
| 264 | `upload-pause` | 重绘 |
| 265 | `upload-success` | 重绘 |
| 266–279 | `upload-error` | **按 HTTP 状态分 4 种提示** |
| 282–286 | `complete` | 只重置状态 + 刷新列表，**不弹提示** |
| 287–294 | `restored` | 恢复后洗状态（见 4.8） |

**`upload-error` 的四分支（266–279）** 对应后端四个错误码：

```js
if (status === 401) { credential = {token:'',expiresAt:0}; 提示重新获取 }   // :268-270 → UploadTokenService:63/70
else if (status === 413) { 超大小上限 }                                    // :271-272 → UploadFileService:227
else if (status === 415) { 类型不允许 }                                    // :273-274 → UploadFileService:238
else { 通用失败 }
```

注意 `:269` 顺手把凭证清空——否则 `ensureToken` 会认为「还没过期」继续用坏 token，陷入重复 401。

**`complete` 为什么不弹提示（282–286）**：Uppy 在 `waitingFileIDs` 为空时会创建空批次、静默跑完并 `emit complete`。此时 `result.successful.length === 0`，弹 toast 就变成「本次上传结束：成功 0 个，失败 0 个」——用户会更困惑。所以改为在 `reportOutcome()`（`:445-457`）里按实际文件状态汇总。

### 4.7 凭证获取 ensureToken（305–324）

```js
const stillValid = credential.token && Date.now() < credential.expiresAt - 60 * 1000  // :306
```

提前 60 秒判过期，避免「校验通过但发到服务端时刚好过期」。这是常见的时钟边界处理。

```js
uppy.setMeta({ studentId: String(studentId), uploadToken: data.token })  // :316
```

**这一行是让「已恢复的历史文件」也能带新凭证的关键**：`setMeta` 是 Uppy 全局 meta，会同步到所有已存在的文件（包括 GoldenRetriever 恢复出来的），而 `addFile` 时的 per-file meta 做不到这点。

### 4.8 三层「调度修复」——本项目最需要理解的一段

这三层都是为了解决同一个 bug：**点「开始上传」毫无反应，进度永远 0%，服务端零请求**。

#### 根因

`@uppy/core` 的 `upload()` 只调度同时满足两个条件的文件：

```
!file.progress.uploadStarted        （没被标记为已开始）
&& !currentUploads[*].fileIDs.includes(id)   （没被分配给某个批次）
```

然后**无条件** `#createUpload(waitingFileIDs)`。当这两个标记被上次异常中断（刷新、HMR、请求取消）残留下来时，`waitingFileIDs` 是空数组——Uppy 对此**不报错**，创建空批次、跑完、`emit complete`。

#### 第一层：resetRestoredProgress（376–394）

```js
uppy.setFileState(file.id, {
  progress: { ...file.progress, uploadStarted: null, bytesUploaded: 0, percentage: 0 }  // :385-392
})
```

时机：`restored` 事件（`:288`）。作用：把 GoldenRetriever 恢复出来的「已开始」标记洗掉。

`if (file.isGhost) return`（`:382`）——ghost 文件没有 `data`，重置了也没法传，跳过。

#### 第二层：requeueStuckFiles（404–424）

```js
uppy.setState({ currentUploads: {}, allowNewUpload: true, error: null })   // :411
```

时机：**每次点「开始上传」之前**（`:483`）。作用：一次性丢弃所有历史批次。

`:409-410` 的注释是关键论证：按钮处于 `loading` 状态时不可能有批次正在跑，所以 `currentUploads` 里的一切都是残留，整体清空最安全。

```js
if (file.progress?.uploadStarted) {
  patch.progress = { ...file.progress, uploadStarted: null, bytesUploaded: 0, percentage: 0 }  // :420
}
```

`:417-421` 处理「僵尸 uploadStarted」——注意这里**无条件**判断，不检查是否真的在上传，因为已确认此刻没有批次在跑。

#### 第三层：rescueSkippedFiles（432–443）

```js
const stuck = files.filter(f => !f.progress?.uploadComplete && !f.isGhost && f.data)   // :436-438
await Promise.allSettled(stuck.map(file => uppy.retryUpload(file.id)))                 // :442
```

时机：`await uppy.upload()` **之后**（`:486`）。作用：如果前两层还有漏网，这里用 `retryUpload` 定向启动。

`retryUpload` 之所以能兜住，是因为它内部走 `forceAllowNewUpload` + 直接按 fileID 建批次，**绕开了所有残留判断**。`Promise.allSettled` 而非 `all`，保证一个失败不影响其他。

#### 三层的分工

| 层 | 时机 | 治什么 | 手段 |
|---|---|---|---|
| 第一层 | `restored` 事件 | 恢复出来的假 `uploadStarted` | 归零进度字段 |
| 第二层 | 点上传、`upload()` 前 | 残留 `currentUploads` + 僵尸标记 | 清批次 + 归零 |
| 第三层 | `upload()` 之后 | 前两层都没覆盖到的 | 逐个 `retryUpload` |

**为什么归零不影响断点续传**：真实偏移量在 tusd 服务端（S3 Multipart 里），前端只需要 `file.tus.uploadUrl`。归零 `uploadStarted` 只是让 Uppy 愿意重新调度；随后 `@uppy/tus` 拿 uploadUrl 发 `HEAD`，从服务端返回的 `Upload-Offset` 继续 `PATCH`。**清的是调度标记，不是进度。**

### 4.9 addFiles 与 ghost 清理（340–368）

```js
rows.value.filter(r => r.status === 'ghost' && r.name === file.name && r.size === file.size)
  .forEach(r => { uppy.removeFile(r.id); speedTracker.delete(r.id) })   // :351-356
```

重选文件的场景：刷新后 465MB 视频变成 ghost 占位，用户重新选同一个文件。如果不先删掉 ghost，队列里会出现两行（一行 ghost 一行新的），看起来像重复上传。

匹配条件是「名字 + 大小」，不用路径——浏览器的 `File` 对象拿不到完整路径（安全限制），这是唯一可靠的组合。

### 4.10 syncRows：状态机（549–581）

**判定顺序决定了状态优先级，不能随便换**：

```js
let status = 'queued'                    // :557 默认
if (file.isGhost) status = 'ghost'       // :558 ① 恢复后无内容，最高优先级
else if (file.error) status = 'error'    // :560 ② 失败
else if (progress.uploadComplete) 'done' // :562 ③ 完成
else if (file.isPaused) 'paused'         // :564 ④ 暂停
else if (progress.uploadStarted) 'uploading'  // :566 ⑤ 上传中
```

为什么 `isGhost` 排第一：ghost 文件可能同时有 `error`（恢复时读 blob 失败），若先判 error，用户看到的是「失败」而不是「需重选」，不知道该干什么。

为什么 `error` 排在 `uploading` 前：失败的文件 `uploadStarted` 可能仍有值，若先判 uploading 会永远显示「上传中」。

`:573` `progress.percentage` 直接来自 Uppy，`:576` 速度查 `speedTracker`。

### 4.11 测速（531–547）

```js
const seconds = (now - prev.at) / 1000
if (seconds < 0.5) return     // :539-541
```

**0.5 秒采样间隔**是必须的：`upload-progress` 事件频率很高（每个分片多次），如果每次都算，`(uploaded - prev.bytes)` 分子很小、分母也小，除出来的速度会剧烈抖动。0.5 秒窗口能给出稳定读数。

`:545` `Math.max(0, ...)` 防止暂停/重试导致的负值。

### 4.12 clearQueue 的顺序（520–529）

```js
uppy.pauseAll()                                          // :525
Object.keys(uppy.getState().files).forEach(id => uppy.removeFile(id))   // :526
```

**必须先 pauseAll**：直接 `removeFile` 会触发 tus 的终止请求（`DELETE /files/{id}`），把服务端分片和 `.info` 一起删掉，用户想续传就没了。先暂停让 `removeFile` 只做本地移除。

### 4.13 模板中的三个「隐性功能」

| 行 | 元素 | 作用 |
|---|---|---|
| 13 | `:disabled="!rows.length"` | 队列空时禁用开始按钮 |
| 32 | `<input type="file" multiple hidden>` | 真正的选择器，`pickFiles` 只负责 `.click()` |
| 44–49 | `waiting-tip` | **「n 个待上传 · 点开始上传」橙色提示** |

`:44-49` 是可用性补救：`autoProceed: false` 意味着选完文件不会自动开始，而按钮文案「开始上传」太小不明显，用户会以为卡住了。这个提示在 `waitingCount > 0 && !hasActive` 时出现（`:182-184` 计算）。

---

## 5. 后端：Hook 入口层

### 5.1 UploadHookController：一个接口吃掉 4 个事件（23–61）

```java
@RequestMapping("/api/upload/hooks")    // :23
@PostMapping
public ResponseEntity<TusdHookResponse> handle(@RequestBody TusdHookRequest request)
```

**为什么只有一个 `@PostMapping` 而不是 4 个接口**：tusd 的 `-hooks-http` 只配置一个 URL（compose:141），事件类型放在请求体 `Type` 字段里。所以必须按 `Type` 自己分发：

```java
return switch (type) {
    case "pre-create"     -> ResponseEntity.ok(uploadFileService.preCreate(request));   // :34
    case "post-create"    -> { postCreate(request); yield ok(); }                        // :35-38
    case "post-finish"    -> { postFinish(request); yield ok(); }                        // :39-42
    case "post-terminate" -> { postTerminate(request); yield ok(); }                     // :43-46
    default               -> { log.debug(...); yield ok(); }                             // :47-50
};
```

四个分支的**返回形态不同**：只有 `pre-create` 需要把 Service 的响应原样返回（可能含 `RejectUpload`），其余三个只回 `TusdHookResponse.ok()`。

`default` 分支（`:47-50`）返回 200——如果 compose 里 `-hooks-enabled-events` 加了 `post-receive` 之类，这里不会 500，只是忽略。

### 5.2 异常处理的两条不同路径（52–60）★

```java
} catch (Exception ex) {
    if ("pre-create".equals(type)) {
        return ResponseEntity.ok(TusdHookResponse.reject(500, "上传校验失败，请稍后重试"));  // :55
    }
    log.error("tusd hook 处理失败 type={}", type, ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(TusdHookResponse.ok());  // :59
}
```

这是全项目最容易看漏、但最容易出错的地方：

| 事件 | 异常时返回 | 为什么 |
|---|---|---|
| `pre-create` | **HTTP 200** + `RejectUpload:true` | tusd 只在 200 时读响应体做决策；返回 500 会被当成「Hook 服务异常」，行为取决于 tusd 的失败策略 |
| 其他 | **HTTP 500** | 让 tusd 触发重试（默认 3 次，指数退避），避免丢事件 |

也就是说：**`pre-create` 用业务语义拒绝，`post-*` 用 HTTP 语义要求重试**。搞反了，前者会变成不可控的放行，后者会变成静默丢记录。

### 5.3 不要用 ApiResult 包装（:18 的注释）

`UploadHookController.java:18` 明确写着：

> 这里不能用 ApiResult 包装，tusd 只认识自己的 HookResponse 结构。

对比 `UploadFileController.java:41` 全部返回 `ApiResult<...>`——两套响应格式并存是**故意的**，因为调用方不同（浏览器 vs tusd）。

### 5.4 UploadFileController：5 个接口

| 行 | 方法 | 路径 | 返回类型 | 说明 |
|---|---|---|---|---|
| 40–48 | `config` | `GET /api/upload/config` | `ApiResult<UploadConfigResponse>` | 5 个参数全来自 `UploadProperties` |
| 51–56 | `token` | `POST /api/upload/token` | `ApiResult<UploadTokenResponse>` | `@Valid` 校验 studentId 非空 |
| 58–61 | `files` | `GET /api/upload/files` | `ApiResult<List<...>>` | studentId 作为必填 query 参数 |
| 64–80 | `download` | `GET /api/upload/files/{id}/download` | `ResponseEntity<Resource>` | **不包 ApiResult**，直接返回字节流 |
| 83–87 | `delete` | `DELETE /api/upload/files/{id}` | `ApiResult<Void>` | — |

**下载接口的三个细节**：

```java
ContentDisposition.attachment().filename(file.getFileName(), StandardCharsets.UTF_8)  // :70-73
```
用 `StandardCharsets.UTF_8` 重载，中文文件名才不会变成 `%E8%AF%BE...`。这是 `Content-Disposition` 的 RFC 5987 编码。

```java
if (file.getSizeBytes() != null) { builder.contentLength(file.getSizeBytes()); }   // :76-78
```
注释（`:75`）写明：**大小未知时不要设 Content-Length**。若强行设 0，响应会被截断成 0 字节——用户下到一个空文件。

```java
return builder.body(new InputStreamResource(payload.stream()));   // :79
```
`InputStreamResource` 让 Spring 边读边写，不会把整个文件读进堆内存。这是「后端不参与字节流」之外，唯一经过后端的字节路径，也必须流式。

`mediaType()`（`:89-98`）：解析失败退回 `application/octet-stream`，避免一个脏 `content_type` 让下载 500。

---

## 6. 后端：业务服务 UploadFileService

326 行，是整个后端最需要读懂的文件。分四块：**4 个 Hook（42–143）→ 4 个业务方法（145–188）→ 5 个校验（190–267）→ 4 个组装（269–321）**。

### 6.1 preCreate（48–73）：唯一能拒绝上传的地方

```java
public TusdHookResponse preCreate(TusdHookRequest request) {
    TusdHookRequest.Upload upload = requireUpload(request);      // :49
    Map<String, String> meta = safeMeta(upload);                 // :50
    try {
        long studentId = resolveStudentId(meta, request);        // :52  ① 凭证
        validateSize(upload);                                    // :53  ② 大小
        String fileName = sanitizeFileName(meta.get("filename")); // :54  ③ 文件名
        validateExtension(fileName);                             // :55  ④ 扩展名

        Map<String, String> persisted = new LinkedHashMap<>();   // :57
        persisted.put("filename", fileName);                     // :58
        persisted.put("filetype", Optional.ofNullable(meta.get("filetype"))
                .filter(v -> !v.isBlank()).orElse("application/octet-stream"));  // :59-61
        persisted.put("studentId", Long.toString(studentId));    // :62
        return TusdHookResponse.changeMeta(persisted);           // :64
    } catch (BizException ex) { ... return reject(...) }         // :65-68
```

**四步校验的顺序是有讲究的**：

1. `:52` 凭证先验——最便宜（纯计算，无 IO），且能挡掉所有未授权请求
2. `:53` 大小——纯比较，第二便宜
3. `:54` 文件名——字符串处理，顺带为第 4 步准备输入
4. `:55` 扩展名——依赖第 3 步的结果

**`:57-64` 是「整体替换」而不是「合并」**：`ChangeFileInfo.MetaData` 的语义是**用服务端给的 map 完全覆盖客户端 meta**（不是合并）。所以必须把要保留的三个字段全写进去。`:63` 的注释说明了为什么不回写 `uploadToken`：

> 不把 uploadToken 回写：凭证只在本次请求里使用，避免长期留在 tusd 的 `.info` 中

tusd 会把 meta 写进 `.info` 文件（file store）或对象元数据（S3 store），凭证留在那里等于长期泄露。

**`:65-72` 的双层 catch**：`BizException` 保留业务码（`normalizeStatus` 把非 4xx/5xx 归到 400），其他异常一律 500。这样客户端能区分「我的文件不符合要求」和「服务端出错」。

### 6.2 resolveStudentId（192–216）：凭证与身分的绑定校验

```java
String rawStudentId = meta.get("studentId");                    // :193
if (blank) rawStudentId = request.header("X-Student-Id");       // :195  ← 兜底到请求头
...
String token = meta.get("uploadToken");                         // :200
if (blank) token = request.header("X-Upload-Token");            // :202  ← 兜底到请求头
long tokenStudentId = tokenService.verify(token);               // :204  验签 + 验过期
...
if (studentId != tokenStudentId) {                              // :212
    throw new BizException(401, "上传凭证与 studentId 不匹配");
}
```

**两个兜底来源**对应两条通路：

- `meta` 里 —— 来自 `Upload.vue:241` 的 `allowedMetaFields`（Uppy 写进 `Upload-Metadata`）
- header 里 —— 来自 `Upload.vue:242` 的 `headers()` 函数 + `compose:143` 的 `-hooks-http-forward-headers`

为什么要两条？因为 meta 在某些代理/客户端下可能被截断，header 更可靠；而 `compose:143` 明确把这三个 header 转发给 Hook。

**`:212` 的比对是这套方案的真正价值**：凭证只能证明「某学生拿到了许可」，不能证明「他要传的就是他自己的文件」。这一步把两者绑定，防止拿 A 的凭证传成 B 的文件。

### 6.3 validateSize（218–229）

```java
if (upload.isSizeIsDeferred() || upload.getSize() == null) {     // :219
    throw new BizException(400, "必须提供文件大小（不支持延迟长度上传）");
}
if (upload.getSize() <= 0) { ... }                              // :222
long max = properties.getSecurity().getMaxFileSize();           // :225
if (upload.getSize() > max) { throw new BizException(413, ...); }  // :226-228
```

**为什么拒绝 `Upload-Defer-Length`（`:219`）**：延迟长度意味着客户端先建上传、后声明大小。这会让「大小上限」无法在创建时校验，等于放弃 `max-size` 这道防线。`compose:135` 的 `-max-size=5368709120` 是第二道（tusd 层），这里 `:226` 是第一道（业务层）。

413 这个状态码的选择是刻意的——`Upload.vue:271` 专门处理 413。

### 6.4 sanitizeFileName（242–256）：路径穿越防护

```java
String name = raw.replace('\\', '/');        // :246  统一分隔符，防 Windows 风格路径
int slash = name.lastIndexOf('/');           // :247
if (slash >= 0) name = name.substring(slash + 1);   // :248-250  只取最后一段
name = name.replaceAll("[\\p{Cntrl}]", "").trim(); // :251  去掉控制字符
if (name.isEmpty()) throw ...                       // :252-254
return name.length() > 255 ? name.substring(0,255) : name;  // :255
```

四步各防一类攻击：

| 行 | 防的是 | 攻击样例 |
|---|---|---|
| 246 | 反斜杠路径 | `C:\Users\x\a.pdf` |
| 247–250 | **路径穿越** | `../../etc/passwd` |
| 251 | 控制字符注入 | `a.pdf\r\nX-Injected: 1` |
| 255 | 超长名撑爆列 | 10000 字符的文件名 |

`MAX_FILE_NAME_LENGTH = 255`（`:35`）与实体 `file_name varchar(512)`（`UploadFileEntity.java:55`）不冲突——前者是业务截断，后者是列宽余量。

**注意这里不改 `objectKey`**：文件名只用于展示和下载时的 `Content-Disposition`，对象 key 是 tusd 生成的 `uploads/{uploadId}`（`compose:128`），所以文件名里的特殊字符影响不到存储路径。

### 6.5 validateExtension（231–240）

```java
int dot = fileName.lastIndexOf('.');              // :232
if (dot < 0 || dot == fileName.length() - 1) { throw 415 }   // :233-235  必须带扩展名
String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);   // :236
if (!allowedExtensions.contains(ext)) { throw 415 }           // :237-239
```

`Locale.ROOT`（`:236`）而不是默认 locale——土耳其语的 `toLowerCase` 会把 `I` 变成 `ı`（无点i），导致 `PDF` 与 `pdf` 匹配失败。这是 `String.toLowerCase()` 的经典陷阱。

### 6.6 postCreate（76–87）：幂等插入

```java
if (upload.getId() == null) return;                                    // :78-80
if (repository.findByUploadId(upload.getId()).isPresent()) return;     // :81-83  ← 幂等
repository.save(newEntity(upload, UPLOADING));                         // :84
```

`:82` 的注释：`tusd 重试导致的重复回调`。tusd 对 `post-*` 事件是**至少一次**投递（失败会重试，`UploadHookController.java:57` 就是配合这个机制），所以必须自己保证幂等。这里靠 `upload_id` 唯一约束（`UploadFileEntity.java:20`）+ 先查后插实现。

严格来说「先查后插」在并发下仍有窗口，但 `upload_id` 的唯一约束会兜住——第二个插入会抛 `DataIntegrityViolationException`，被 `UploadHookController.java:52` 捕获后返回 500 让 tusd 重试，重试时 `findByUploadId` 就能查到，走 `return`。

### 6.7 postFinish（90–122）：补齐存储位置

```java
UploadFileEntity entity = repository.findByUploadId(upload.getId())
        .orElseGet(() -> newEntity(upload, COMPLETED));    // :95-96  找不到就补建
```

**为什么允许 `orElseGet` 补建**：如果 `post-create` 因为后端重启/网络抖动丢了，`post-finish` 是最后的机会。宁可补一条记录，也不要让用户传完的文件消失。

```java
Map<String, String> storage = upload.getStorage();      // :98
if (storage != null && storage.get("Key") != null) {
    entity.setStorageType(storage.get("Type"));         // :100  "s3store"
    entity.setBucket(storage.get("Bucket"));            // :101
    entity.setObjectKey(storage.get("Key"));            // :102  "uploads/{uploadId}"
}
```

**这三行是「下载能工作」的前提**：`objectKey` 只有 `post-finish` 的 `Storage` 字段里才有，`pre-create`/`post-create` 阶段拿不到（`TusdHookRequest.java:38` 的注释明确写了「pre-create 阶段 ID 为空、Storage 不可用」）。

```java
if (upload.getSize() != null) entity.setSizeBytes(upload.getSize());       // :104-105
else if (upload.getOffset() != null) entity.setSizeBytes(upload.getOffset()); // :106-108
entity.setOffsetBytes(upload.getOffset());                                 // :109
```

双层兜底：`size` 缺失时用 `offset`（已接收字节数）当文件大小。

```java
if (upload.getSize() != null && upload.getOffset() != null
        && !upload.getSize().equals(upload.getOffset())) {                 // :110-111
    log.warn("上传结束但 offset 与 size 不一致，可能存在截断 uploadId={} ...");  // :112-113
}
```

**只告警不拒绝**（`:110-114`）。这是有意的取舍：已经传完的字节不该因为一个不一致就被丢掉，但运维需要知道这次上传可能不完整。

### 6.8 postTerminate（125–143）：一次真实的读写竞态

```java
@Transactional                                                    // :125
int updated = repository.updateStatusUnless(upload.getId(),
        TERMINATED, DELETED, Instant.now());                      // :134-137
```

`UploadFileService.java:131-133` 的注释是这段代码的全部价值：

> 业务删除会主动调用 tusd DELETE，而 tusd 在返回 204 之后才异步回抛 post-terminate；若此处"先查再改"，会把刚写入的 DELETED 覆盖回 TERMINATED，导致已删除文件重新出现在列表里。

时序：

```
浏览器 DELETE → 后端 delete() → 调 tusd DELETE → tusd 回 204
                                              ↓（异步）
                                    tusd 发 post-terminate Hook
                                              ↓
                          若此时 delete() 已写入 DELETED，会被覆盖成 TERMINATED
```

修法是把「判断」下推到 SQL：

```java
@Query("update UploadFileEntity e set e.status = :target, e.updatedAt = :now "
     + "where e.uploadId = :uploadId and e.status <> :excluded")   // Repository:32-33
int updateStatusUnless(...)
```

`where status <> 'DELETED'` 让数据库保证先后顺序。`:138-142` 用受影响行数区分两种结果：`> 0` 正常终止，`= 0` 说明已被逻辑删除，用 `log.debug` 而非 `warn`（这是正常路径，不是异常）。

### 6.9 listByStudent（147–155）：只返回 COMPLETED

```java
return repository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
        .filter(e -> e.getStatus() == UploadStatus.COMPLETED)     // :152
        .map(this::toSummary)
        .toList();
```

`:149-151` 的注释解释了为什么过滤：

> UPLOADING 是断点未续完的残留（续传靠前端 tus uploadUrl，不在列表里操作），DELETED 业务已删除，TERMINATED 客户端中途取消，均无可用对象

**这一条与 `docs/data-dictionary.md` 的描述有不一致**，见[第 14 节](#14-代码与文档的已知偏差)。

### 6.10 openDownload（157–163）

```java
if (entity.getStatus() != COMPLETED || entity.getObjectKey() == null) {   // :159
    throw new BizException(409, "文件尚未上传完成，无法下载");
}
return new DownloadPayload(entity, storageService.openObject(...));       // :162
```

409 Conflict 而不是 404——文件记录存在，只是状态不允许这个操作。`objectKey == null` 的判断很必要：`post-finish` 之前这个字段是空的，硬读 S3 会抛 `NoSuchKeyException`。

`DownloadPayload`（`:324-325`）是一个 `record`，只为了把「实体 + 流」这两个必须成对传递的东西绑在一起。

### 6.11 delete（170–184）：两级兜底

```java
public void delete(Long id) {
    UploadFileEntity entity = get(id);
    if (entity.getStatus() == DELETED) return;                  // :172-174  幂等
    try {
        storageService.terminate(entity.getUploadId());          // :176  ① 首选：让 tusd 清
    } catch (Exception ex) {
        storageService.deleteObject(entity.getBucket(), entity.getObjectKey());  // :179  ② 兜底：直接删对象
    }
    entity.setStatus(DELETED);                                   // :181
    repository.save(entity);                                     // :183
}
```

**为什么不加 `@Transactional`**（`:168` 的注释）：

> 内部有外部 HTTP/S3 调用，避免长时间占用数据库连接。

`terminate` 是一次 HTTP 调用（`RustFsStorageService.java:56-60`），如果包在事务里，数据库连接会被网络 IO 占住，高并发下连接池很快耗尽。

**为什么优先 `terminate` 而不是直接删对象**（`RustFsStorageService.java:52-53`）：

> tusd 会清理已落盘分片与 `.info`，并回调 post-terminate。这是比直接删对象更干净的方式。

直接删 S3 对象会留下两样垃圾：RustFS 里的未完成 Multipart（除了已提交的 part）、tusd 的 `.info` 元数据。`terminate` 把它们一起清掉。

### 6.12 newEntity 与 toSummary（282–313）

`newEntity`（`:282-300`）是「从 Hook 请求构造实体」的唯一入口，`:296-298` 有个细节：

```java
if (status == COMPLETED) { entity.setFinishedAt(now); }
```

只有直接建成 COMPLETED（`post-finish` 的补建路径）时才写 `finishedAt`；`postCreate` 建 UPLOADING 时留空。

`toSummary`（`:302-313`）最后一行：

```java
"/api/upload/files/" + entity.getId() + "/download"     // :312
```

**用数据库主键 `id` 而不是 `uploadId` 拼下载地址**。`uploadId` 会给外部暴露 tusd 的内部标识，且它是可被用来构造 `DELETE /files/{uploadId}` 的——隐藏它降低攻击面。

---

## 7. 上传凭证 UploadTokenService

### 7.1 格式与设计取舍（20–21 的注释）

```
v1.{studentId}.{expEpochSecond}.{base64url(HMAC-SHA256)}
```

三个设计决定：

| 决定 | 原因 |
|---|---|
| 不用 JWT 库 | 只需要「签发 + 验签」，JWT 的 header/claims 结构是多余负担；90 行搞定，无依赖 |
| 无状态、不入库 | 上传是高频操作，每次查库/写库都是额外开销；HMAC 验签是纯 CPU |
| `v1` 前缀 | 换算法时可并行支持 `v2`，`verify` 里 `!VERSION.equals(parts[0])`（`:51`）直接拒绝 |

### 7.2 issue（35–39）

```java
Instant expiresAt = Instant.now().plus(Duration.ofMinutes(tokenTtlMinutes));   // :36
String payload = VERSION + "." + studentId + "." + expiresAt.getEpochSecond(); // :37
return new IssuedToken(payload + "." + sign(payload), expiresAt);              // :38
```

**签名覆盖的是前 3 段拼接**，不含签名本身——这是标准做法，避免自引用。`getEpochSecond()` 用秒级精度，与 `Upload.vue:314` 的 `new Date(expiresAt).getTime()` 毫秒值不同，前端只用于本地预判，真正的过期判定在服务端 `:62`。

### 7.3 verify（46–73）：五道检查

```java
if (token == null || token.isBlank()) throw 401;                  // :47-49  ① 空
String[] parts = token.split("\\.");                              // :50
if (parts.length != 4 || !VERSION.equals(parts[0])) throw 401;    // :51-53  ② 段数 + 版本
try { studentId = parse(parts[1]); expiresAt = parse(parts[2]); } // :56-58
catch (NumberFormatException) { throw 401; }                      // :59-61  ③ 可解析
if (Instant.now().getEpochSecond() > expiresAt) throw 401;        // :62-64  ④ 未过期
String expected = sign(parts[0] + "." + parts[1] + "." + parts[2]);          // :65
boolean valid = MessageDigest.isEqual(expected.getBytes(UTF_8), parts[3].getBytes(UTF_8));  // :66-68
if (!valid) throw 401;                                            // :69-71  ⑤ 签名正确
```

**`:66-68` 用 `MessageDigest.isEqual` 而不是 `String.equals`**——这是**时序攻击**防护。`String.equals` 在第一个不同字符就返回，攻击者可以通过响应时间差逐字节猜出签名。`MessageDigest.isEqual` 是常量时间比较。

**`:62` 的过期判断在验签之前**。这看似不安全（未验签就信了 `expiresAt`），实则有意：过期检查便宜，先挡掉大量无效请求；而且伪造的 `expiresAt` 最终会在 `:69` 被签名校验拦住——签名覆盖了 `expiresAt`，改它必然导致签名不符。

### 7.4 sign（75–86）

```java
Mac mac = Mac.getInstance("HmacSHA256");                          // :77
mac.init(new SecretKeySpec(signingKey.getBytes(UTF_8), ALGORITHM));  // :78-79
return ENCODER.encodeToString(mac.doFinal(payload.getBytes(UTF_8)));  // :80
} catch (Exception ex) {
    log.error("上传凭证签名失败", ex);
    throw new IllegalStateException("上传凭证签名失败", ex);        // :84
}
```

**`:82-84` 是关键的安全设计**：注释写着「密钥配置错误属于服务端问题，不应退化为「校验通过」」。也就是说这里绝不能 `return ""` 或 `return null`——那会让空签名被当成有效签名（因为 `isEqual` 比较的是空串和空串）。

`ENCODER`（`:31`）是 `Base64.getUrlEncoder().withoutPadding()`——URL 安全字符集，因为 token 要放进 `Upload-Metadata` 头（不能有 `+` `/` `=`）。

### 7.5 单元测试（UploadTokenServiceTest，59 行）

4 个用例覆盖 4 类失败：

| 行 | 用例 | 验证 |
|---|---|---|
| 22–28 | `issueAndVerify` | 正常签发可验回同一个 studentId |
| 30–40 | `rejectsTamperedStudentId` | 把 `parts[1]` 从 42 改成 43 → 401 |
| 42–49 | `rejectsExpiredToken` | `ttlMinutes = -1` 造过期 → 401 |
| 51–58 | `rejectsMalformedToken` | null / 无点串 / `v2` 前缀 → 401 |

`:35-36` 的篡改手法值得注意：只改 studentId 不改签名，验证「签名覆盖了 studentId」这件事。如果 `sign()` 里漏拼了某一段，这个测试会失败。

`service(long ttlMinutes)`（`:15-20`）手工 new 而不启动 Spring 上下文——纯内存、毫秒级，这是这个类能被单测的原因（`UploadProperties` 是普通 `@Data` 类，没有外部依赖）。

---

## 8. 对象存储三件套

### 8.1 RustFsConfig（24–34）：三行关键配置

```java
S3Client.builder()
    .endpointOverride(URI.create(storage.getEndpoint()))     // :28  指向 RustFS 而非 AWS
    .region(Region.of(storage.getRegion()))                  // :29  RustFS 仍需一个 region 占位
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKey, secretKey)))   // :30-31  静态密钥
    .forcePathStyle(true)                                    // :32  ★ 必须
    .build();
```

**`forcePathStyle(true)` 为什么不能省**（`:15` 的注释）：AWS 默认用虚拟主机风格 `https://bucket.s3.amazonaws.com/key`，这要求 DNS 有 `*.s3.amazonaws.com` 泛解析。`localhost:9000` 没有，所以必须用路径风格 `http://localhost:9000/bucket/key`。

### 8.2 RustFsBucketInitializer（32–57）：为什么不用 minio/mc

`:16-19` 的注释记录了这段代码的来历：

> 早期版本用一次性容器（minio/mc）建桶，但 MinIO 已从 Docker Hub 下架该镜像，拉取必然失败，因此改为由后端在建连后自建桶。

```java
rustFsS3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());  // :43
} catch (NoSuchBucketException e) {
    createBucket(bucket);                                                        // :45-46
} catch (S3Exception e) {
    if (e.statusCode() == 404) { createBucket(bucket); }                         // :47-49
```

**两个 catch 都要**：SDK 对「桶不存在」可能抛 `NoSuchBucketException`（有明确错误码时），也可能只抛泛化的 `S3Exception` 带 404 状态。RustFS 的行为在不同版本间有差异，两条都写才稳。

```java
} catch (Exception e) {
    log.warn("对象存储暂不可用，跳过桶自检（上传功能需先启动 RustFS）: {}", e.getMessage());  // :54-56
}
```

**只告警不阻断启动**（`:20-22` 的注释）：`ApplicationRunner` 抛异常会导致 Spring Boot 启动失败。后端还要提供聊天、资源生成等其它能力，不该因为 RustFS 没起来就整体挂掉。

`:37-40` 还有一个开关：`autoCreateBucket=false` 时直接跳过——生产环境应该用运维预置的桶 + 最小权限账号（不给 `CreateBucket` 权限）。

### 8.3 RustFsStorageService（26–66）

构造器（`:26-30`）同时初始化两个客户端：

```java
this.s3Client = s3Client;
this.tusdClient = builder.baseUrl(properties.getTusd().getBaseUrl()).build();   // :29
```

`RestClient.Builder` 是 Spring 注入的，`baseUrl` 指向 `app.upload.tusd.base-url`（`application.yml:73`，默认 `http://localhost:1080`）——**内网地址，浏览器永远看不到**。

`openObject`（`:33-38`）：返回 `ResponseInputStream`，调用方（Controller）直接透传，不在内存里缓冲。

`deleteObject`（`:41-49`）：`:42-44` 的空判断是必要的——`objectKey` 为 null 时（UPLOADING 状态的文件）调 S3 会抛异常，而删除未完成上传是正常操作。

`terminate`（`:55-62`）：

```java
tusdClient.delete()
    .uri("/files/{id}", uploadId)                     // :57
    .header("Tus-Resumable", "1.0.0")                 // :58  ★ tus 协议要求
    .retrieve().toBodilessEntity();                   // :59-60
```

**`Tus-Resumable: 1.0.0` 头不能省**——tus 协议规定 `DELETE`/`PATCH`/`HEAD` 都必须带这个头，否则 tusd 返回 412 Precondition Failed。

注意 `uri` 写的是 `/files/{id}` 而不是 `/api/...`——tusd 自己的路由前缀就是 `-base-path=/files/`（`compose:138`）。这里用的是 `baseUrl`（tusd 直连 1080），不加 `/api`。

`defaultBucket`（`:64-66`）：实体里 `bucket` 为空时回落到配置值。因为 `post-finish` 之前这个字段没写。

---

## 9. 数据模型与 Repository

### 9.1 表结构映射（UploadFileEntity，19–99）

```java
@Table(name = "upload_files",
    uniqueConstraints = @UniqueConstraint(name = "uk_upload_files_upload_id", columnNames = "upload_id"),  // :20
    indexes = {
        @Index(name = "idx_upload_files_student", columnList = "student_id, created_at"),   // :22
        @Index(name = "idx_upload_files_status", columnList = "status")                     // :23
    })
```

两个索引对应两种查询：学生列表（`Repository:18`）、孤儿清理（按状态扫）。唯一约束对应 `postCreate` 的幂等（`UploadFileService:81`）。

### 9.2 状态枚举（27–36）

```
UPLOADING   → 分片上传中（可断点续传）
COMPLETED   → 合并完成，可下载
TERMINATED  → 客户端主动终止（tus DELETE）
DELETED     → 业务侧逻辑删除
```

**为什么需要 4 个而不是 2 个**：`TERMINATED` 和 `DELETED` 的区别在于**发起方**。前者是用户在浏览器里点「移除」/关闭页面触发 tus DELETE；后者是业务侧调 `delete()`。区分它们才能实现 `postTerminate` 的竞态修复（`Repository:32-33` 的 `where status <> DELETED`）。

### 9.3 两个踩过坑的字段

**`uploadId` 长度 255（47–48）**：

```java
/**
 * tusd Upload-ID。S3 store 会带版本后缀：<uuid>+<base64(uid.version)>，
 * 实测长度 150+，故不能按 uuid 的 36/64 设列宽。
 */
@Column(name = "upload_id", nullable = false, length = 255)
```

最初设的是 64，运行时报 `value too long for varchar(64)`。tusd 的 S3 store 会在 UUID 后面拼 `+<base64编码的版本号>`，实际长度 150 字符以上。

**`metaData` 用 Map 不用 String（88–90）**：

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "meta_json", columnDefinition = "jsonb")
private Map<String, String> metaData;
```

`:85-86` 的注释是完整的诊断记录：

> 用 Map 而非 String：String 会被 Hibernate 当 varchar 绑定，PostgreSQL 不会隐式转成 jsonb（SQLState 42804）；Map + JSON 类型才会正确走 jsonb 参数。

这个坑值得推广——项目里 `Resource`、`Assessment`、`LearningPath` 等实体也是 `String` + jsonb 的写法（见[第 14 节](#14-代码与文档的已知偏差)），有同样的隐患。

### 9.4 Repository 的四个方法（13–37）

| 行 | 方法 | 用途 |
|---|---|---|
| 16 | `findByUploadId` | Hook 幂等（`postCreate`/`postFinish` 用） |
| 18 | `findByStudentIdOrderByCreatedAtDesc` | 学生文件列表 |
| 20 | `findByStudentIdAndStatusOrderByCreatedAtDesc` | 按状态查（当前**未被调用**，为后续孤儿清理预留） |
| 31–37 | `updateStatusUnless` | 条件更新，防竞态 |

`updateStatusUnless` 上的注解：

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)   // :31
```

- `flushAutomatically = true`：执行前先把挂起的 `save` 刷到库，否则前一步 `delete()` 里的 `entity.setStatus(DELETED)` 还在内存里，条件更新会读到旧值
- `clearAutomatically = true`：执行后清一级缓存，避免后续查询拿到脏实体

这两个参数缺一个，竞态修复就会失效。

---

## 10. 配置的四层映射关系

同一个参数在四个地方出现，改的时候要一起改。这是最容易出不一致的地方。

### 10.1 一条完整链路：`chunkSize`

| 层 | 位置 | 值 |
|---|---|---|
| ① YAML 源 | `application.yml:69` | `chunk-size: 8388608` |
| ② 绑定类 | `UploadProperties.java:19` | `private long chunkSize = 8L*1024*1024` |
| ③ 下发 DTO | `UploadFileController.java:44` | `properties.getChunkSize()` |
| ④ 前端消费 | `Upload.vue:234` | `chunkSize: config.value.chunkSize` |

`UploadProperties.java:19` 的默认值和 yml 是**同一份数字**——这是刻意的双保险：yml 漏了配也不会崩。

### 10.2 参数对照总表

| 参数 | yml 行 | 类字段 | 前端/后端使用点 | compose |
|---|---|---|---|---|
| 分片大小 | 69 | `UploadProperties:19` | `Upload.vue:234` | — |
| 并发数 | 70 | `:22` | `Upload.vue:235` | — |
| tusd 内网地址 | 73 | `:32` | `RustFsStorageService:29` | `TUSD_BASE_URL` (180) |
| tus 公开路径 | 74 | `:34` | `Upload.vue:232`（经 config） | `-base-path` (138) |
| 存储端点 | 76 | `:40` | `RustFsConfig:28` | `-s3-endpoint` (127) |
| 桶名 | 77 | `:41` | `RustFsStorageService:65` | `-s3-bucket` (126) |
| 自动建桶 | 79 | `:49` | `RustFsBucketInitializer:37` | — |
| 签名密钥 | 85 | `:56` | `UploadTokenService:79` | 未显式传（见下） |
| 凭证有效期 | 86 | `:58` | `UploadTokenService:36` | — |
| 单文件上限 | 87 | `:60` | `UploadFileService:225` | `-max-size` (135) |
| 扩展名白名单 | 88–91+ | `:62-64` | `UploadFileService:237` | — |

### 10.3 三个必须对齐的跨进程约束

**① `-base-path=/files/` ↔ `public-path: /files/` ↔ `endpoint: '/files/'`**

三处分别是 tusd（compose:138）、后端（yml:74）、前端（`Upload.vue:232`）。任一处不同，前端请求的路径就到不了 tusd 的路由。

**② 前端分片 8MB < S3 part 16MB**

`compose:130` 的 `-s3-part-size=16777216` 是 S3 part 大小，`application.yml:69` 的 8388608 是前端分片。tusd 会把**两个前端分片聚合成一个 S3 part**。如果反过来（前端分片 > part 大小），tusd 就必须把一个 PATCH 拆成多个 part，内存缓冲压力变大。

**③ 签名密钥必须通过环境变量注入**

`application.yml:85` 写的是 `${UPLOAD_SIGNING_KEY:dev-only-change-me-please-32bytes}`。`docker-compose.yml` 的 backend 段（174–180）**没有列出这个变量**——意味着容器里用的是 yml 的默认值。生产部署时必须自己加：

```yaml
UPLOAD_SIGNING_KEY: ${UPLOAD_SIGNING_KEY:?must-set}
```

用 `:?` 语法让它「没设就启动失败」，比默认值安全。

---

## 11. 前端路由与两个代理

### 11.1 开发期（vite.config.js:8–20）

```js
proxy: {
  '/api':   { target: 'http://localhost:8080', changeOrigin: true },              // :9-12
  '/files': { target: 'http://localhost:1080', changeOrigin: false, xfwd: true }  // :15-19
}
```

两个配置的差异是刻意的：

- `/api` 用 `changeOrigin: true` —— 后端不关心 Host
- `/files` 用 `changeOrigin: false` + `xfwd: true` —— **让 tusd 生成的 `Location`（uploadUrl）指回 5173**。`:13-14` 的注释：`xfwd 让 tusd 生成的 uploadUrl 指回 5173，避免跨域`。配合 tusd 的 `-behind-proxy`（compose:137），tusd 会信任 `X-Forwarded-Host` 并据此拼 uploadUrl。

如果这里写错了，现象是：POST 成功返回 201，但 `Location` 指向 `http://localhost:1080/files/xxx`，浏览器随后 PATCH 时跨域被拦。

### 11.2 生产期（nginx.conf:28–54）

```nginx
location /files/ {
    proxy_pass http://tusd:1080;          # :29  不加尾部斜杠，路径原样转发
    proxy_set_header Host $http_host;      # :33  保留端口
    proxy_set_header X-Forwarded-Host $http_host;  # :37
    proxy_set_header Connection "";        # :39  不复用连接
    client_max_body_size 0;                # :45
    proxy_request_buffering off;           # :47
    proxy_buffering off;                   # :48
}
```

**`:33` 用 `$http_host` 而不是 `$host`**：`$host` 会丢掉端口，tusd 生成的 uploadUrl 就少了端口号。`:32` 的注释明确了这一点。

**`:45-48` 三项是必须的**（`:41-44` 的注释逐条解释）：

| 配置 | 不加的后果 |
|---|---|
| `client_max_body_size 0` | nginx 默认 1m，8MB 分片直接 413 |
| `proxy_request_buffering off` | nginx 先把整个分片落盘再转发，磁盘 IO 翻倍、延迟变高 |
| `proxy_buffering off` | TUS 的 `HEAD`/`PATCH` 响应是实时语义，缓冲会导致偏移量更新延迟 |

`:52-53` 的 `proxy_send_timeout`/`proxy_read_timeout` 都设 3600s——大文件慢速上传可能持续很久，默认 60s 会中途断开。

---

## 12. 反查表：要改 X，改哪里

这是本文档最实用的一节。按「你想做什么」查。

### 12.1 改限制

| 需求 | 改这里 | 连带要改 |
|---|---|---|
| 调整单文件上限 | `application.yml:87` `max-file-size` | `compose:135` `-max-size`；`UploadProperties:60` 默认值 |
| 允许新的扩展名 | `application.yml:88-91+` | `UploadProperties:62-64` 默认列表（两份都要，否则默认值兜底会失效） |
| 改分片大小 | `application.yml:69` | `compose:130` 的 part 需保持 ≥ 2 倍 |
| 改并发文件数 | `application.yml:70` | `UploadProperties:22` |
| 改凭证有效期 | `application.yml:86` | `UploadProperties:58`；前端 `Upload.vue:306` 的 60 秒余量 |

### 12.2 加字段

**给上传记录加一个新字段（例如「课程 ID」）**，要动 6 处：

| 顺序 | 位置 | 改什么 |
|---|---|---|
| ① | `UploadFileEntity.java:79` 附近 | 加 `@Column` 字段 |
| ② | 数据库 | 通常不用手工：`application.yml:21` 是 `ddl-auto: update`，Hibernate 启动时自动加列。但新列若是 jsonb，Java 侧必须按 `UploadFileEntity.java:88-90` 的写法，否则加了列也写不进去 |
| ③ | `Upload.vue:241` | 把新字段名加进 `allowedMetaFields` |
| ④ | `Upload.vue:316` | 在 `setMeta` 里带上值 |
| ⑤ | `UploadFileService.java:57-62` | 在 `persisted` 里保留（**否则会被 `changeMeta` 整体替换掉**） |
| ⑥ | `UploadFileService.java:282-300` | 在 `newEntity` 里写入实体 |
| ⑦ | `UploadFileSummary.java:9-17` + `UploadFileService.java:303-312` | 列表要展示才加 |

**第 ⑤ 步是最容易漏的**：`ChangeFileInfo.MetaData` 是整体替换，不在 `persisted` 里的 meta 字段全部丢失。

### 12.3 换存储

| 场景 | 改动 |
|---|---|
| 换一个 S3 兼容存储 | 只改环境变量：`RUSTFS_ENDPOINT`/`RUSTFS_BUCKET`/`RUSTFS_ACCESS_KEY`/`RUSTFS_SECRET_KEY` |
| 换成非 S3 存储 | tusd 需换 store 实现（`-s3-*` 换成 `-file-*` 等），且 `RustFsStorageService` 要重写读取/删除 |
| 加 CDN 直链下载 | 改 `UploadFileController.java:64-80`，改成返回 302 到预签名 URL；`RustFsStorageService` 加 `generatePresignedUrl` |

### 12.4 改行为

| 需求 | 位置 |
|---|---|
| 让列表显示「上传中」的文件 | `UploadFileService.java:152` 的 filter 加上 `UPLOADING` |
| 上传完成后触发文本抽取 | `UploadFileService.java:118` 之后（`repository.save` 成功后）发异步事件 |
| 支持延迟长度上传 | `UploadFileService.java:219` 去掉 `isSizeIsDeferred` 检查，但要在 `post-finish` 补 size 校验 |
| 改「点开始上传」的调度逻辑 | `Upload.vue:459-494`（理解三层修复后再动，见 4.8） |
| 改断点续传的缓存上限 | `Upload.vue:250-251` |
| 换提醒文案/错误码映射 | `Upload.vue:266-279` + 后端对应 `BizException` 状态码 |

### 12.5 排错定位

| 现象 | 先看 |
|---|---|
| 请求 404，路径不对 | `nginx.conf:28` / `vite.config.js:15` / `compose:138` 三处 `/files/` 是否一致 |
| 413 请求体过大 | `nginx.conf:45` 的 `client_max_body_size 0` 是否生效 |
| 400「缺少文件名」 | `Upload.vue:241` 的 `allowedMetaFields` 是否含 `name` |
| 401 凭证无效 | `UploadTokenService:46-73`；检查 `UPLOAD_SIGNING_KEY` 前后端是否一致（后端单方，但重启后密钥变了会全失效） |
| 412 Precondition Failed | tus 请求缺 `Tus-Resumable: 1.0.0`（见 `RustFsStorageService:58`） |
| 上传完成但列表没有 | `UploadFileService.java:152` 的 filter；查 `select status from upload_files` |
| 删除后文件又出现 | `Repository:32-33` 的 `where status <> DELETED` 是否生效 |
| 下载是 0 字节 | `UploadFileController.java:76-78` 的 Content-Length 条件 |
| 中文文件名乱码 | `UploadFileController.java:70-73` 的 `StandardCharsets.UTF_8` |
| 点上传无反应、服务端零请求 | `Upload.vue:404-443` 三层修复；用 `tusd` 日志确认有无 POST |

---

## 13. 关键数字速查

每个数字都能追到一个「为什么」。

| 数字 | 位置 | 为什么是这个值 |
|---|---|---|
| `8388608`（8MB） | `yml:69` | 前端分片。8MB 时 50GB 文件约 6400 次 PATCH，兼顾进度平滑与请求数 |
| `16777216`（16MB） | `compose:130` | S3 part。S3 除最后一片外**最小 5MB**，16MB 留 3 倍余量 |
| `5368709120`（5GB） | `yml:87` | 单文件上限。S3 单对象上限 5TB，这里按业务需要收窄 |
| `3` | `yml:70` | 并发文件数。单机出口带宽通常 10–100Mbps，3 个并发能吃满又不打爆 tusd 内存 |
| `255` | `Entity:47` | `upload_id` 列宽。tusd S3 store 实测 150+ 字符 |
| `512` | `Entity:55` | `file_name` 列宽。比业务截断值 255 大，留余量 |
| `255` | `UploadFileService:35` | 业务层文件名截断长度 |
| `200MB` | `Upload.vue:250` | GoldenRetriever 单文件缓存上限。默认 10MB 太小 |
| `1GB` | `Upload.vue:251` | GoldenRetriever 总缓存上限 |
| `120` 分钟 | `yml:86` | 凭证有效期。够传完最大文件又不过长 |
| `60` 秒 | `Upload.vue:306` | 凭证提前判过期的余量 |
| `0.5` 秒 | `Upload.vue:539` | 测速采样窗口，防抖动 |
| `4` | `compose:131-132` | `-s3-max-buffered-parts` / `-s3-concurrent-part-uploads`。缓冲 4 个 16MB part = 64MB 内存 |
| `3600` 秒 | `nginx.conf:52-53` | 大文件慢速上传的超时 |
| `150+` 字符 | `Entity:44-46` 注释 | tusd S3 store 的 upload-id 实际长度 |
| `4` 段 | `UploadTokenService:50-51` | token 用 `.` 分 4 段 |

---

## 14. 代码与文档的已知偏差

读代码时如果发现和别的文档对不上，以这里为准。

### 14.1 列表接口只返回 COMPLETED

| | 说法 |
|---|---|
| `docs/data-dictionary.md:142` | 状态含 UPLOADING/COMPLETED/TERMINATED/DELETED |
| 早期实现 | filter 保留 UPLOADING + COMPLETED |
| **当前代码** | `UploadFileService.java:152` **只保留 COMPLETED** |

结论：**列表页看不到未完成的上传**。这是有意的（`:149-151` 注释：未完成项的续传靠前端 `uploadUrl`，不在列表里操作），但如果要加「断点续传文件列表」功能，就得放开这个 filter。

### 14.2 studentId 是硬编码

`Upload.vue:155`：

```js
// TODO: 接入登录态后从用户上下文取，不要写死
const studentId = 1
```

全前端只有这一处定义，`ensureToken`（`:311`）、`loadServerFiles`（`:586`）都用它。接登录态时改这里一处即可，但同时要考虑：后端 `listByStudent` 目前**不校验 studentId 归属**（`UploadFileController.java:59` 只接收参数），任何学生都能查别人的列表。上线前必须补。

### 14.3 jsonb 隐患：9 个字段全是 `String`

`UploadFileEntity.java:85-90` 修的是 `String` 绑 jsonb 报 42804 的问题。项目里还有 **9 个字段仍是 `String` + `columnDefinition = "jsonb"`**，**每一个都会在真正写入时复现同一个报错**：

| 实体 | 字段 | 行 |
|---|---|---|
| `AssessmentEntity` | `reportJson` | :31-32 |
| `LearningEventEntity` | `payloadJson` | :28-29 |
| `LearningPathEntity` | `planJson` | :28-29 |
| `PathNodeEntity` | `resourceIds` | :33-34 |
| `QuizRecordEntity` | `answersJson` | :33-34 |
| `ResourceEntity` | `contentJson` | :41-42 |
| `ResourceEntity` | `metaJson` | :48-49 |
| `StudentProfileEntity` | `profileJson` | :25-26 |
| `TutorSessionEntity` | `messagesJson` | :24-25 |

检查命令：

```bash
grep -rn -A 2 'columnDefinition = "jsonb"' backend/src/main/java/com/example/learning/entity/
```

凡是输出里出现 `private String` 的，都要按 `UploadFileEntity.java:88-90` 的三行改法处理：

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "...", columnDefinition = "jsonb")
private Map<String, Object> ...;      // 或 JsonNode / List<...>，取决于内容结构
```

**注意不是机械替换 `String` → `Map`**：`PathNodeEntity.resourceIds` 的语义是数组，应该用 `List<String>`；`ResourceEntity.contentJson` 是 Markdown 内容不是 JSON 结构，可能该把列类型改成 `text` 而不是改 Java 类型。逐个判断。

### 14.4 `findByStudentIdAndStatusOrderByCreatedAtDesc` 未被调用

`UploadFileRepository.java:20` 定义了但全项目无调用点。是为后续「孤儿上传清理」预留的（按 `UPLOADING` + `created_at` 早于 N 小时扫）。

### 14.5 密签密钥未在 compose 中声明

见 [10.3 ③](#103-三个必须对齐的跨进程约束)。当前 compose 的 backend 段（174–180）没有 `UPLOAD_SIGNING_KEY`，走 yml 默认值 `dev-only-change-me-please-32bytes`。

---

## 15. 推荐阅读顺序与自测命令

### 15.1 第一次读（约 40 分钟）

```
① Upload.vue:209-303         建 Uppy + 装插件 + 订阅事件（看懂前端骨架）
② Upload.vue:459-494         startUpload 五步（看懂触发链路）
③ UploadHookController:29-61  一个接口四个事件（看懂前后端衔接）
④ UploadFileService:48-73    preCreate 四步校验（看懂安全边界）
⑤ UploadFileService:90-122   postFinish 落存储位置（看懂数据从哪来）
⑥ UploadFileService:147-184  查询/下载/删除（看懂业务接口）
```

读到 ③ 时如果对 Hook 契约不清楚，先看 `TusdHookRequest.java:20-72`（字段定义就是契约）和 `TusdHookResponse.java:28-51`（三种响应形态）。

### 15.2 第二次读（约 20 分钟）

看「为什么」，不是「做什么」：

```
① Upload.vue:376-443          三层调度修复（项目最绕的一段）
② UploadFileService:125-143   条件更新防竞态
③ UploadTokenService:46-73    五道检查 + 常量时间比较
④ UploadFileEntity:83-90      jsonb 的 42804 坑
⑤ nginx.conf:41-48            上传必须的三项配置
```

### 15.3 自测命令

验证服务链路（不传文件也能跑）：

```bash
# 1. 配置接口
curl -s http://localhost:8080/api/upload/config | python -m json.tool

# 2. 凭证签发
curl -s -X POST http://localhost:8080/api/upload/token \
  -H "Content-Type: application/json" -d '{"studentId":1}' | python -m json.tool

# 3. tusd 是否响应 tus 协议
curl -s -i -X OPTIONS http://localhost:1080/files/ | head -8

# 4. RustFS 健康
curl -s http://localhost:9000/health

# 5. 走代理（模拟浏览器路径）
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:5173/api/upload/config
```

验证数据落库：

```bash
docker exec learning-postgres psql -U postgres -d learning \
  -c "select id, file_name, size_bytes, offset_bytes, status, object_key from upload_files order by created_at desc limit 5;"
```

**重点看 `object_key` 是否有值**——为空说明 `post-finish` 没跑（`UploadFileService.java:99-103` 没执行），下载必然 409。

验证前端调度状态（需要 DEV 模式，`Upload.vue:298-300` 暴露了 `window.__uppy`）：

```js
// 浏览器控制台
Object.values(__uppy.getState().files).map(f => ({
  name: f.name,
  started: f.progress?.uploadStarted,
  complete: f.progress?.uploadComplete,
  inBatch: Object.values(__uppy.getState().currentUploads).some(b => b.fileIDs.includes(f.id))
}))
```

如果 `started` 有值但 `complete` 为 false、且没有任何文件真的在传 → 僵尸标记，对应 4.8 的修复场景。

---

## 附：一句话总结每个文件

| 文件 | 一句话 |
|---|---|
| `Upload.vue` | 唯一的复杂点：把 Uppy 的调度状态洗成「随时可以重新开始」，同时不丢服务端偏移 |
| `UploadFileController` | 5 个薄接口，下载那个最需要小心（编码、Content-Length、流式） |
| `UploadHookController` | pre-create 用业务语义拒绝，post-* 用 500 要求重试，这两条不能混 |
| `UploadFileService` | 四步校验 → 三点落库 → 条件更新防竞态，全项目最该读懂的文件 |
| `UploadTokenService` | 90 行无状态 HMAC，重点是常量时间比较和「签名失败必须抛异常」 |
| `RustFsStorageService` | 只读不写，`Tus-Resumable` 头不能省 |
| `RustFsConfig` | 三行配置，`forcePathStyle(true)` 是灵魂 |
| `RustFsBucketInitializer` | 替代已下架的 minio/mc，失败只告警不阻断启动 |
| `UploadFileEntity` | 两个踩过的坑写在字段上（255 长度、Map+jsonb） |
| `UploadFileRepository` | 条件更新是并发正确性的唯一保障 |
| `TusdHookRequest/Response` | 字段名就是 tusd 契约，PascalCase 必须显式标注 |
| `docker-compose.yml` | tusd 的 14 个参数，每个都对应一个行为 |
| `nginx.conf` | `/files/` 那 27 行决定了大文件能不能传 |

---

## 变更记录

| 版本 | 日期 | 变化 |
|---|---|---|
| v1.0 | 2026-11-20 | 首版：按调用链 + 文件逐段编排，附反查表、数字速查、已知偏差 |
