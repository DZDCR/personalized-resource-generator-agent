import request from './request'

/** 上传能力配置（tus 端点、分片大小、大小与类型限制）。 */
export function getUploadConfig() {
  return request.get('/upload/config')
}

/** 申请上传凭证。身份由服务端会话决定，响应回传该会话的 studentId。 */
export function createUploadToken() {
  return request.post('/upload/token')
}

/** 已上传文件列表（按服务端会话归属，不接受前端指定学生）。 */
export function listUploadFiles() {
  return request.get('/upload/files')
}

/** 删除（tusd 终止上传 + 业务记录标记删除）。 */
export function deleteUploadFile(id) {
  return request.delete(`/upload/files/${id}`)
}

/** 下载地址（浏览器直接打开即可，后端流式透传）。 */
export function downloadUrl(id) {
  return `/api/upload/files/${id}/download`
}

/**
 * 小文件批量导入。
 *
 * <p>不设 Content-Type：FormData 需要浏览器自动带上 boundary，手动写死会丢 boundary
 * 导致服务端解析失败。超时放宽到 5 分钟，一批 50MB 在慢网下可能要一会儿。
 */
export function uploadBatch(formData) {
  return request.post('/upload/batch', formData, { timeout: 300000 })
}

