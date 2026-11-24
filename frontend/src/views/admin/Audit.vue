<template>
  <div>
    <div class="filters">
      <el-select v-model="filters.eventType" clearable placeholder="事件类型" class="filter">
        <el-option v-for="item in eventTypes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="filters.username" clearable placeholder="用户名" class="filter" />
      <el-select v-model="filters.success" clearable placeholder="结果" class="filter small">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-date-picker v-model="filters.range" type="datetimerange" range-separator="至"
        start-placeholder="开始时间" end-placeholder="结束时间" class="range" />
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" class="data-table">
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="事件" min-width="180">
        <template #default="{ row }">
          <div class="event">
            <span class="strong">{{ eventLabel(row.eventType) }}</span>
            <code>{{ row.eventType }}</code>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="主体" width="140">
        <template #default="{ row }">{{ row.username || '—' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="IP" width="130" prop="ip" />
      <el-table-column label="详情" min-width="280">
        <template #default="{ row }">
          <span class="detail" :title="row.detail">{{ row.detail || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="User-Agent" width="180">
        <template #default="{ row }">
          <span class="muted" :title="row.userAgent">{{ shorten(row.userAgent) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
        :page-size="size" :current-page="page + 1" :page-sizes="[20, 50, 100]"
        @current-change="onPage" @size-change="onSize" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { searchAudit } from '../../api/rbac'

const eventTypes = [
  { value: 'LOGIN_PASSWORD_SUCCESS', label: '账号密码登录成功' },
  { value: 'LOGIN_PASSWORD_FAIL', label: '账号密码登录失败' },
  { value: 'LOGIN_OTP_SUCCESS', label: '验证码登录成功' },
  { value: 'LOGIN_OTP_FAIL', label: '验证码登录失败' },
  { value: 'LOGIN_SSO_SUCCESS', label: '统一身份认证' },
  { value: 'LOGIN_LOCKED', label: '账号锁定' },
  { value: 'LOGOUT', label: '退出登录' },
  { value: 'TOKEN_REFRESH', label: '刷新令牌' },
  { value: 'REGISTER', label: '注册' },
  { value: 'PASSWORD_RESET', label: '找回密码' },
  { value: 'PASSWORD_CHANGE', label: '修改密码' },
  { value: 'ROLE_ASSIGN', label: '角色授予' },
  { value: 'ROLE_CREATE', label: '创建角色' },
  { value: 'ROLE_UPDATE', label: '更新角色' },
  { value: 'ROLE_DELETE', label: '删除角色' },
  { value: 'ROLE_PERMISSION_ASSIGN', label: '角色授权' },
  { value: 'CONSTRAINT_CREATE', label: '创建约束' },
  { value: 'CONSTRAINT_UPDATE', label: '更新约束' },
  { value: 'CONSTRAINT_DELETE', label: '删除约束' },
  { value: 'USER_CREATE', label: '创建用户' },
  { value: 'USER_STATUS', label: '账号状态' },
  { value: 'USER_UNLOCK', label: '解锁账号' },
  { value: 'USER_PASSWORD_RESET', label: '管理员重置密码' },
  { value: 'ROLE_SWITCH', label: '切换激活角色' }
]

const rows = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const loading = ref(false)
const filters = reactive({ eventType: '', username: '', success: '', range: null })

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function eventLabel(type) {
  return eventTypes.find((item) => item.value === type)?.label || type
}

function shorten(value) {
  if (!value) return '—'
  return value.length > 32 ? value.slice(0, 32) + '…' : value
}

async function load() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      eventType: filters.eventType || undefined,
      username: filters.username || undefined,
      success: filters.success === '' ? undefined : filters.success
    }
    if (filters.range?.length === 2) {
      params.from = new Date(filters.range[0]).toISOString()
      params.to = new Date(filters.range[1]).toISOString()
    }
    const result = await searchAudit(params)
    rows.value = result.data.items
    total.value = result.data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 0
  load()
}

function reset() {
  Object.assign(filters, { eventType: '', username: '', success: '', range: null })
  reload()
}

function onPage(value) {
  page.value = value - 1
  load()
}

function onSize(value) {
  size.value = value
  page.value = 0
  load()
}

onMounted(load)
</script>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.filter {
  width: 190px;
}

.filter.small {
  width: 110px;
}

.range {
  max-width: 360px;
}

.data-table {
  width: 100%;
}

.event {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.strong {
  color: #0f172a;
  font-weight: 600;
  font-size: 12px;
}

.event code {
  color: #94a3b8;
  font-size: 10px;
}

.detail {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #475569;
  font-size: 12px;
}

.muted {
  color: #94a3b8;
  font-size: 11px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
