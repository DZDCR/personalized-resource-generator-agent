<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div class="head">
          <span>文件上传</span>
          <span class="hint">大文件分片续传 · 小文件（≤10MB）自动走批量直传</span>
        </div>
      </template>

      <div class="toolbar">
        <el-button type="primary" @click="pickFiles">选择文件</el-button>
        <el-button type="success" :loading="busy" :disabled="!rows.length" @click="startUpload">
          开始上传
        </el-button>
        <el-button :disabled="!hasActive" @click="pauseAll">全部暂停</el-button>
        <el-button :disabled="!hasPaused" @click="resumeAll">全部继续</el-button>
        <el-button :disabled="!rows.length" @click="clearQueue">清空队列</el-button>
      </div>

      <div
        class="dropzone"
        :class="{ over: dragOver }"
        @click="pickFiles"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
      >
        <div class="dz-title">拖拽文件到此处，或点击选择（支持批量，小文件自动快速上传）</div>
        <div class="dz-sub">浏览器逐片发送，不在本地拼装完整文件；分片由 RustFS 在服务端合并</div>
      </div>
      <input ref="fileInput" type="file" multiple hidden @change="onInputChange" />

      <div class="limits">
        分片 {{ formatSize(config.chunkSize) }} · 单文件上限 {{ formatSize(config.maxFileSize) }} ·
        同时上传 {{ config.maxConcurrentUploads }} 个 · 支持 {{ config.allowedExtensions.join(' / ') }}
      </div>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div class="head">
          <span>上传队列</span>
          <span class="hint" v-if="rows.length">
            <span v-if="waitingCount && !hasActive" class="waiting-tip">
              {{ waitingCount }} 个待上传 · 点「开始上传」
            </span>
            {{ formatSize(totalUploaded) }} / {{ formatSize(totalSize) }}
          </span>
        </div>
      </template>

      <el-table :data="rows" stripe>
        <el-table-column prop="name" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="进度" min-width="260">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.floor(row.progress)"
              :status="progressStatus(row)"
              :stroke-width="10"
            />
            <div class="sub">
              {{ formatSize(row.uploaded) }} / {{ formatSize(row.total) }}
              <span v-if="row.status === 'uploading'"> · {{ formatSpeed(row.speed) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'uploading' || row.status === 'paused'"
              link
              type="primary"
              @click="togglePause(row)"
            >
              {{ row.status === 'paused' ? '继续' : '暂停' }}
            </el-button>
            <el-button v-if="row.status === 'error'" link type="warning" @click="retry(row)">
              重试
            </el-button>
            <el-button link type="danger" @click="remove(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="暂无待上传文件" :image-size="80" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span>已上传文件</span>
          <span class="head-actions">
            <span v-if="syncing" class="syncing">同步中…</span>
            <el-button link @click="loadServerFiles()">刷新</el-button>
          </span>
        </div>
      </template>

      <el-table :data="serverFiles" stripe v-loading="loadingServer">
        <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small">
              {{ row.status === 'COMPLETED' ? '已完成' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="180">
          <template #default="{ row }">
            {{ row.finishedAt ? new Date(row.finishedAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status !== 'COMPLETED'"
              @click="download(row)"
            >
              下载
            </el-button>
            <el-button link type="danger" @click="removeServerFile(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!serverFiles.length && !loadingServer" description="还没有上传记录" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import Uppy from '@uppy/core'
import Tus from '@uppy/tus'
import GoldenRetriever from '@uppy/golden-retriever'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createUploadToken,
  deleteUploadFile,
  downloadUrl,
  getUploadConfig,
  listUploadFiles,
  uploadBatch
} from '../api/upload'

// 身份由服务端会话（HttpOnly Cookie）决定，前端不再声明 studentId。
// 申请凭证时后端回传该会话的 studentId，仅用于写入 tus 元数据。

const fileInput = ref(null)
const dragOver = ref(false)
const busy = ref(false)
const loadingServer = ref(false)
// 上传完成后等待后端落库期间置为 true，用于在卡片头部提示「同步中」
const syncing = ref(false)
// 列表请求序号：轮询与手动刷新并发时，只认最新一次请求的结果
let loadSeq = 0

const rows = ref([])
const serverFiles = ref([])

// 小文件快速通道：≤10MB 不走 tus（每个文件省掉 3 次往返），改走一次 multipart 批量写。
// 它们与 tus 文件共用同一个队列视图，用户不需要知道背后有两条链路。
const smallFiles = ref([])
let smallSeq = 0

// ---- 小文件批量导入 ----

const config = ref({
  tusEndpoint: '/files/',
  chunkSize: 8 * 1024 * 1024,
  maxFileSize: 5 * 1024 * 1024 * 1024,
  allowedExtensions: [],
  maxConcurrentUploads: 3,
  // 批量导入限额，取到服务端配置前先按后端的默认值兜底
  batchMaxFiles: 20,
  batchMaxFileSize: 10 * 1024 * 1024,
  batchMaxTotalSize: 50 * 1024 * 1024
})

let uppy = null
let credential = { token: '', expiresAt: 0 }
let tokenPromise = null
const speedTracker = new Map()

const totalSize = computed(() => rows.value.reduce((sum, r) => sum + (r.total || 0), 0))
const totalUploaded = computed(() => rows.value.reduce((sum, r) => sum + (r.uploaded || 0), 0))
const hasActive = computed(() => rows.value.some((r) => r.status === 'uploading'))
const hasPaused = computed(() => rows.value.some((r) => r.status === 'paused'))
const waitingCount = computed(
  () => rows.value.filter((r) => r.status === 'queued' || r.status === 'ghost').length
)

onMounted(async () => {
  await loadConfig()
  initUppy()
  // 列表接口按会话归属查询，必须先拿到会话（首次访问时由 token 接口下发 Cookie），
  // 否则这里会拿到 401，用户一进页面就看到一条错误提示。
  try {
    await ensureToken()
    await loadServerFiles()
  } catch (e) {
    // 会话建立或列表加载失败不阻断页面：拖文件进来时 ensureToken 会重试。
  }
})

onBeforeUnmount(() => {
  // 只暂停 + 释放实例：保留 tus uploadUrl 与服务端分片，下次进入页面可继续
  uppy?.pauseAll()
  uppy?.destroy()
})

async function loadConfig() {
  try {
    const res = await getUploadConfig()
    if (res.data) {
      config.value = res.data
    }
  } catch (e) {
    // 配置拉取失败时沿用默认值，具体错误已由拦截器提示
  }
}

function initUppy() {
  // 开发期热更新/重复挂载时，旧实例不会被 onBeforeUnmount 回收：
  // 两个实例同时监听 state-update 并写同一个 IndexedDB 恢复位，会把 files/currentUploads 搅乱。
  if (uppy) {
    try {
      uppy.destroy()
    } catch (e) {
      /* 旧实例已失效则忽略 */
    }
    uppy = null
  }

  uppy = new Uppy({
    id: 'resource-uploader',
    autoProceed: false,
    allowMultipleUploadBatches: true,
    restrictions: {
      maxFileSize: config.value.maxFileSize,
      allowedFileTypes: config.value.allowedExtensions.map((ext) => `.${ext}`)
    }
  })

  uppy.use(Tus, {
    endpoint: config.value.tusEndpoint,
    // 关键：流式分片大小，前端只切分发送，不合并
    chunkSize: config.value.chunkSize,
    limit: config.value.maxConcurrentUploads,
    retryDelays: [0, 1000, 3000, 5000, 10000],
    // 成功后续传指纹要清理，避免同名文件再次上传时被误判为「已完成」
    removeFingerprintOnSuccess: true,
    // 必须显式包含 name/type：@uppy/tus 是从「过滤后的 meta」里取 name→filename 的，
    // 漏掉就发不出 filename，服务端会以「缺少文件名」拒绝
    allowedMetaFields: ['name', 'type', 'studentId', 'uploadToken'],
    headers: () => (credential.token ? { 'X-Upload-Token': credential.token } : {})
  })

  // 刷新/崩溃后恢复未完成的上传（含 tus uploadUrl，可续传）
  // 默认只缓存 ≤10MB 的文件，大文件会被标记 ghost（需重新选择同名文件走指纹续传），
  // 这里把上限放宽到 200MB，覆盖课件/PDF 等常见体积
  uppy.use(GoldenRetriever, {
    indexedDB: {
      maxFileSize: 200 * 1024 * 1024,
      maxTotalSize: 1024 * 1024 * 1024
    }
  })

  uppy.on('file-added', syncRows)
  uppy.on('file-removed', syncRows)
  uppy.on('upload', () => {
    busy.value = true
  })
  uppy.on('upload-progress', (file, progress) => {
    trackSpeed(file.id, progress.bytesUploaded || 0)
    syncRows()
  })
  uppy.on('upload-pause', syncRows)
  uppy.on('upload-success', syncRows)
  uppy.on('upload-error', (file, error, response) => {
    const status = response?.status
    if (status === 401) {
      credential = { token: '', expiresAt: 0 }
      ElMessage.warning('上传凭证已过期，请重新点击「开始上传」')
    } else if (status === 413) {
      ElMessage.error(`${file.name} 超过大小上限 ${formatSize(config.value.maxFileSize)}`)
    } else if (status === 415) {
      ElMessage.error(`${file.name} 的文件类型不在允许范围内`)
    } else {
      ElMessage.error(`${file.name} 上传失败：${error?.message || '未知错误'}`)
    }
    syncRows()
  })
  // 提示统一交给 startUpload 汇总：Uppy 在「没有文件被调度」时也会 emit complete（成功 0 个），
  // 这里再弹一次会让人误以为上传正常结束
  uppy.on('complete', (result) => {
    busy.value = false
    syncRows()
    const added = result?.successful?.length ?? 0
    if (added > 0) {
      // 不立即信任后端已落库，轮询等到新记录出现（详见 waitForServerFiles）
      waitForServerFiles(added)
    } else {
      // 没有文件真正上传成功（例如全是重试/跳过），只做一次普通刷新
      loadServerFiles()
    }
  })
  uppy.on('restored', () => {
    resetRestoredProgress()
    requeueStuckFiles()
    syncRows()
    if (rows.value.length) {
      ElMessage.info('已恢复上次未完成的上传，点击「开始上传」可继续')
    }
  })
  uppy.on('error', (error) => ElMessage.error(error.message))

  // 仅开发期：便于在控制台核对内部状态（生产构建会被移除）
  if (import.meta.env.DEV) {
    window.__uppy = uppy
  }

  syncRows()
}

async function ensureToken(force = false) {
  const stillValid = credential.token && Date.now() < credential.expiresAt - 60 * 1000
  if (!force && stillValid) {
    return credential.token
  }
  if (!tokenPromise) {
    tokenPromise = createUploadToken()
      .then((res) => {
        const data = res.data
        credential = { token: data.token, expiresAt: new Date(data.expiresAt).getTime() }
        uppy.setMeta({ studentId: String(data.studentId), uploadToken: data.token })
        rebindFileIdentity(data.studentId, data.token)
        return data.token
      })
      .finally(() => {
        tokenPromise = null
      })
  }
  return tokenPromise
}

/**
 * 把当前会话身份写回每个未完成文件。
 *
 * <p>GoldenRetriever 恢复的文件带着**上一次会话**的 studentId/uploadToken（浏览器里可能还留着
 * 旧版本硬编码的 studentId=1）。服务端 pre-create 会校验「元数据 studentId 与凭证一致」，
 * 身份对不上就直接拒绝——表现是「刷新后继续上传」永远失败。所以拿到新凭证后必须逐个纠正，
 * 不能只依赖全局 setMeta（文件级 meta 的优先级高于全局 meta）。
 */
function rebindFileIdentity(studentId, token) {
  if (!uppy) return
  const sid = String(studentId)
  Object.values(uppy.getState().files).forEach((file) => {
    if (file.progress?.uploadComplete) return
    if (file.meta?.studentId === sid && file.meta?.uploadToken === token) return
    uppy.setFileMeta(file.id, { studentId: sid, uploadToken: token })
  })
}

function pickFiles() {
  fileInput.value?.click()
}

function onInputChange(event) {
  addFiles(event.target.files)
  event.target.value = ''
}

function onDrop(event) {
  dragOver.value = false
  addFiles(event.dataTransfer?.files)
}

function addFiles(fileList) {
  const files = Array.from(fileList || [])
  if (!files.length || !uppy) {
    return
  }
  for (const file of files) {
    if (!file.size) {
      ElMessage.warning(`${file.name} 是空文件，已跳过`)
      continue
    }
    // 小文件走批量直传：不进 uppy，省掉每个文件 3 次 tus 往返（建上传/写数据/等落库）。
    // 用户看到的仍是同一个队列，只有「开始上传」之后的实现方式不同。
    if (file.size <= config.value.batchMaxFileSize) {
      if (!extensionAllowed(file.name)) {
        ElMessage.warning(`${file.name}：不支持的文件类型`)
        continue
      }
      smallFiles.value.push({ id: `batch-${++smallSeq}`, file, status: 'queued', error: '' })
      continue
    }
    // 重新选择同名同大小文件时，清掉上次遗留的 ghost 占位，避免队列里重复两行
    rows.value
      .filter((r) => r.status === 'ghost' && r.name === file.name && r.size === file.size)
      .forEach((r) => {
        uppy.removeFile(r.id)
        speedTracker.delete(r.id)
      })
    try {
      uppy.addFile({ name: file.name, type: file.type, data: file, source: 'local' })
    } catch (error) {
      if (error.isRestriction) {
        ElMessage.warning(`${file.name}：${error.message}`)
      } else {
        ElMessage.error(`${file.name}：${error.message}`)
      }
    }
  }
  syncRows()
}

/**
 * 恢复后重置未完成文件的进度。
 * GoldenRetriever 会把 uploadStarted/bytesUploaded 一起恢复，而 upload() 只调度
 * 「未开始」的文件，导致刷新后再点「开始上传」完全没反应。
 * 重置后由 @uppy/tus 用 file.tus.uploadUrl 去 HEAD 真实偏移，从断点继续。
 */
function resetRestoredProgress() {
  if (!uppy) {
    return
  }
  Object.values(uppy.getState().files).forEach((file) => {
    // ghost = 浏览器没能缓存文件内容（超过 IndexedDB 上限），只能重新选择文件续传
    if (file.progress?.uploadComplete || file.isGhost) {
      return
    }
    uppy.setFileState(file.id, {
      progress: {
        ...file.progress,
        uploadStarted: null,
        bytesUploaded: 0,
        percentage: 0
      }
    })
  })
}

/**
 * 把「看起来在排队、实际已经卡住」的文件重新变回可调度状态。
 *
 * Uppy 的 upload() 只会调度满足 `!file.progress.uploadStarted && !currentUploads[*].fileIDs.includes(id)`
 * 的文件；一旦上次上传异常中断（刷新、HMR、请求被取消），这两个标记会残留下来。
 * 此时 upload() 拿到的 waitingFileIDs 是空数组 —— 而它对此不报错，而是创建一个空批次、
 * 静默跑完并 emit complete，表现就是「点开始上传毫无反应，进度永远 0%，服务端零请求」。
 */
function requeueStuckFiles() {
  if (!uppy) {
    return
  }
  const files = Object.values(uppy.getState().files)
  // 点「开始上传」时按钮处于 loading 态，不可能有批次正在跑，
  // 所以 currentUploads 里的一切都是历史残留，整体丢弃最安全
  uppy.setState({ currentUploads: {}, allowNewUpload: true, error: null })
  files.forEach((file) => {
    if (file.progress?.uploadComplete) {
      return
    }
    const patch = { error: null, isPaused: false }
    if (file.progress?.uploadStarted) {
      // 僵尸标记：上次中断遗留。不清掉的话 upload() 认为「已开始」而直接跳过，
      // 既不报错也不发请求，界面就停在「等待中」。真实偏移由 @uppy/tus 用 HEAD 取回。
      patch.progress = { ...file.progress, uploadStarted: null, bytesUploaded: 0, percentage: 0 }
    }
    uppy.setFileState(file.id, patch)
  })
}

/**
 * upload() 之后的兜底。
 * Uppy 在 waitingFileIDs 为空时不报错，而是建空批次静默跑完并 emit complete。
 * 所以这里在 upload() 返回后重新检查：仍未完成的文件一律用 retryUpload 定向启动
 * （retryUpload 内部 forceAllowNewUpload + 直接按 fileID 建批次，绕开所有残留判断）。
 */
async function rescueSkippedFiles() {
  if (!uppy) {
    return
  }
  const stuck = Object.values(uppy.getState().files).filter(
    (f) => !f.progress?.uploadComplete && !f.isGhost && f.data
  )
  if (!stuck.length) {
    return
  }
  await Promise.allSettled(stuck.map((file) => uppy.retryUpload(file.id)))
}

function reportOutcome() {
  if (!uppy) {
    return
  }
  const files = Object.values(uppy.getState().files)
  const failed = files.filter((f) => f.error).length
  const done = files.filter((f) => f.progress?.uploadComplete).length
  if (failed) {
    ElMessage.warning(`上传结束：成功 ${done} 个，失败 ${failed} 个`)
  } else if (done) {
    ElMessage.success(`本次上传结束：成功 ${done} 个`)
  }
}

async function startUpload() {
  if (!uppy || !rows.value.length) {
    return
  }
  if (hasPaused.value) {
    uppy.resumeAll()
    ElMessage.info('已继续暂停中的上传')
    return
  }

  // 队列里可能同时有两类文件：小文件走批量直传，大文件走 tus 分片
  const pendingSmall = smallFiles.value.filter(
    (item) => item.status === 'queued' || item.status === 'error'
  )
  const pendingBig = Object.values(uppy.getState().files).filter(
    (f) => !f.progress?.uploadComplete && !f.isGhost
  )
  if (!pendingSmall.length && !pendingBig.length) {
    if (rows.value.some((r) => r.status === 'ghost')) {
      ElMessage.warning('刷新后浏览器已丢弃大文件内容，请重新选择该文件（会按指纹从断点续传）')
    } else {
      ElMessage.info('队列中的文件都已上传完成')
    }
    return
  }

  busy.value = true
  try {
    // 先传小文件：一次请求就结束，通常几百毫秒，没必要让用户等大文件
    if (pendingSmall.length) {
      await uploadSmallBatch(pendingSmall)
    }
    if (pendingBig.length) {
      await ensureToken(true)
      requeueStuckFiles()
      await uppy.upload()
      await rescueSkippedFiles()
    }
    reportOutcome()
  } catch (error) {
    ElMessage.error(error?.message || '上传启动失败')
  } finally {
    busy.value = false
    syncRows()
  }
}

function pauseAll() {
  uppy?.pauseAll()
  syncRows()
}

function resumeAll() {
  uppy?.resumeAll()
}

function togglePause(row) {
  uppy?.pauseResume(row.id)
  syncRows()
}

function retry(row) {
  uppy?.retryUpload(row.id)
}

function remove(row) {
  if (row.batch) {
    // 小文件行不在 uppy 里，直接从本地队列摘掉
    smallFiles.value = smallFiles.value.filter((item) => item.id !== row.id)
    syncRows()
    return
  }
  uppy?.removeFile(row.id)
  speedTracker.delete(row.id)
  syncRows()
}

function clearQueue() {
  if (!uppy) {
    return
  }
  // 先暂停再逐个移除：避免 cancelAll 触发 tus 的终止请求把服务端分片删掉
  uppy.pauseAll()
  Object.keys(uppy.getState().files).forEach((id) => uppy.removeFile(id))
  smallFiles.value = []
  speedTracker.clear()
  syncRows()
}

function trackSpeed(id, uploaded) {
  const now = performance.now()
  const prev = speedTracker.get(id)
  if (!prev) {
    speedTracker.set(id, { at: now, bytes: uploaded, speed: 0 })
    return
  }
  const seconds = (now - prev.at) / 1000
  if (seconds < 0.5) {
    return
  }
  speedTracker.set(id, {
    at: now,
    bytes: uploaded,
    speed: Math.max(0, (uploaded - prev.bytes) / seconds)
  })
}

function syncRows() {
  if (!uppy) {
    return
  }
  const batchRows = smallFiles.value.map((item) => ({
    id: item.id,
    name: item.file.name,
    size: item.file.size,
    // 批量写是一次请求，没有细粒度进度：等待 0、上传中 55、完成 100
    progress: item.status === 'done' ? 100 : item.status === 'uploading' ? 55 : 0,
    uploaded: item.status === 'done' ? item.file.size : 0,
    total: item.file.size,
    speed: 0,
    status: item.status,
    error: item.error || '',
    batch: true
  }))
  const tusRows = Object.values(uppy.getState().files).map((file) => {
    const progress = file.progress || {}
    const uploaded = progress.bytesUploaded || 0
    const total = progress.bytesTotal || file.size || 0
    let status = 'queued'
    if (file.isGhost) {
      status = 'ghost'
    } else if (file.error) {
      status = 'error'
    } else if (progress.uploadComplete) {
      status = 'done'
    } else if (file.isPaused) {
      status = 'paused'
    } else if (progress.uploadStarted) {
      status = 'uploading'
    }
    return {
      id: file.id,
      name: file.name,
      size: file.size,
      progress: progress.percentage || 0,
      uploaded,
      total,
      speed: speedTracker.get(file.id)?.speed || 0,
      status,
      error: file.error || ''
    }
  })
  rows.value = [...batchRows, ...tusRows]
}

/** 扩展名是否在白名单内（服务端还会再校验一次，这里只为提前拦）。 */
function extensionAllowed(name) {
  const list = config.value.allowedExtensions || []
  if (!list.length) {
    return true
  }
  const dot = name.lastIndexOf('.')
  return dot >= 0 && list.includes(name.slice(dot + 1).toLowerCase())
}

/**
 * 把队列里的小文件合并成一次请求上传。
 *
 * <p>一次 multipart 请求 → 服务端逐个写对象 → 一次批量 INSERT。相比让 tus 逐个传，
 * 20 个文件能从 60 次往返降到 1 次。
 */
async function uploadSmallBatch(items) {
  items.forEach((item) => {
    item.status = 'uploading'
    item.error = ''
  })
  syncRows()

  const form = new FormData()
  items.forEach((item) => form.append('files', item.file))

  try {
    const res = await uploadBatch(form)
    const data = res.data
    const failedNames = new Set(
      (data.items || []).filter((x) => !x.success).map((x) => x.fileName)
    )
    items.forEach((item) => {
      if (failedNames.has(item.file.name)) {
        item.status = 'error'
        item.error = '上传失败'
      } else {
        item.status = 'done'
      }
    })
    if (data.succeeded > 0) {
      ElMessage.success(`已添加 ${data.succeeded} 个文件`)
    }
    if (data.failed > 0) {
      const detail = (data.items || [])
        .filter((i) => !i.success)
        .map((i) => `${i.fileName}：${i.message}`)
        .join('；')
      ElMessage.warning(`${data.failed} 个未添加 —— ${detail}`)
    }
    await loadServerFiles()
  } catch (error) {
    items.forEach((item) => {
      item.status = 'error'
      item.error = error.message || '上传失败'
    })
  } finally {
    syncRows()
    // 成功的从队列移除：它们已经出现在下方「已上传文件」里，留着只会越堆越多
    setTimeout(() => {
      smallFiles.value = smallFiles.value.filter((item) => item.status !== 'done')
      syncRows()
    }, 1500)
  }
}

async function loadServerFiles(options = {}) {
  const { silent = false } = options
  const seq = ++loadSeq
  if (!silent) loadingServer.value = true
  try {
    const res = await listUploadFiles()
    // 轮询与手动刷新可能并发，只认最后一次发出的请求，避免旧结果覆盖新结果
    if (seq !== loadSeq) return
    serverFiles.value = res.data || []
  } finally {
    if (!silent) loadingServer.value = false
  }
}

/**
 * 上传完成后把新文件「等」出来。
 *
 * <p>tusd 的 post-finish 是在 PATCH 响应之后**异步**回调的：前端收到「上传完成」时，
 * 后端可能还没把记录置为 COMPLETED，而列表只展示 COMPLETED。所以立刻刷一次往往
 * 什么都看不到，用户只能手动再点刷新。这里按短间隔轮询到目标条数出现为止。
 *
 * @param expected 本次成功上传的个数，用于推算目标条数
 * @return 是否在超时前全部出现
 */
async function waitForServerFiles(expected, timeoutMs = 8000) {
  const target = serverFiles.value.length + expected
  const deadline = Date.now() + timeoutMs
  syncing.value = true
  try {
    while (Date.now() < deadline) {
      // 400ms 起步：1MB 文件实测约 0.5 秒落库，大文件合并分片会更久
      await new Promise((resolve) => setTimeout(resolve, 400))
      await loadServerFiles({ silent: true })
      if (serverFiles.value.length >= target) {
        return true
      }
    }
    return false
  } finally {
    syncing.value = false
    // 超时也补一次可见刷新，把「没等到」的状态如实呈现出来
    await loadServerFiles({ silent: true })
  }
}

function download(row) {
  window.open(downloadUrl(row.id), '_blank')
}

async function removeServerFile(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.fileName}」？将同时清理对象存储中的文件。`,
      '删除文件',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await deleteUploadFile(row.id)
  ElMessage.success('已删除')
  await loadServerFiles()
}

function progressStatus(row) {
  if (row.status === 'done') return 'success'
  if (row.status === 'error') return 'exception'
  return undefined
}

function statusTag(status) {
  return {
    queued: 'info',
    uploading: 'primary',
    paused: 'warning',
    done: 'success',
    error: 'danger',
    ghost: 'warning'
  }[status] || 'info'
}

function statusText(status) {
  return {
    queued: '等待中',
    uploading: '上传中',
    paused: '已暂停',
    done: '已完成',
    error: '失败',
    ghost: '需重选'
  }[status] || status
}

function formatSize(bytes) {
  const value = Number(bytes) || 0
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

function formatSpeed(bytesPerSecond) {
  if (!bytesPerSecond) return '0 B/s'
  return `${formatSize(bytesPerSecond)}/s`
}
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.syncing {
  font-size: 12px;
  color: #909399;
}

.hint {
  font-size: 12px;
  color: #909399;
}


.hint {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.waiting-tip {
  margin-right: 10px;
  color: #e6a23c;
}

.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.dropzone {
  border: 1px dashed #c0c4cc;
  border-radius: 8px;
  padding: 28px 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;
}

.dropzone:hover,
.dropzone.over {
  border-color: #409eff;
  background: #ecf5ff;
}

.dz-title {
  font-size: 14px;
  color: #303133;
}

.dz-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.limits {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
</style>
