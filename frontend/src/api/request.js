import axios from 'axios'
import { ElMessage } from 'element-plus'
import { tokenStore } from './token'

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
  withCredentials: true
})

let refreshing = null

function redirectToLogin() {
  tokenStore.clear()
  const current = window.location.pathname + window.location.search
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign('/login?redirect=' + encodeURIComponent(current))
  }
}

async function refreshAccessToken() {
  if (!refreshing) {
    refreshing = request
      .post('/auth/refresh', null, { skipAuthRefresh: true, silent: true })
      .then((res) => {
        const data = res.data
        tokenStore.set(data.accessToken)
        window.dispatchEvent(new CustomEvent('auth:refreshed', { detail: data }))
        return data.accessToken
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

request.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
      const error = new Error(body.message || '请求失败')
      error.code = body.code
      error.data = body.data
      if (!response.config.silent) {
        ElMessage.error(body.message || '请求失败')
      }
      return Promise.reject(error)
    }
    return body
  },
  async (error) => {
    const config = error.config || {}
    const response = error.response
    const body = response?.data
    error.code = body?.code
    error.data = body?.data

    if (response?.status === 401 && !config.skipAuthRefresh && !config._retried) {
      config._retried = true
      try {
        await refreshAccessToken()
        return request(config)
      } catch (refreshError) {
        redirectToLogin()
        return Promise.reject(refreshError)
      }
    }

    const message = body?.message || error.message || '网络错误'
    if (!config.silent && response?.status !== 401) {
      ElMessage.error(message)
    }
    error.message = message
    return Promise.reject(error)
  }
)

export default request
