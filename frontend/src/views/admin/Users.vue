<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="keyword" placeholder="搜索用户名 / 邮箱 / 手机号" clearable class="search"
          @keyup.enter="reload" @clear="reload" />
        <el-button @click="reload">查询</el-button>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建用户</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="data-table">
      <el-table-column label="用户" min-width="170">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="avatar">{{ (row.displayName || row.username).slice(0, 1).toUpperCase() }}</span>
            <div>
              <div class="strong">{{ row.displayName || row.username }}</div>
              <div class="muted">@{{ row.username }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="联系方式" min-width="210">
        <template #default="{ row }">
          <div class="strong contact">{{ row.email || '-' }}</div>
          <div class="muted">{{ row.phone || '未绑定手机' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="有效角色（含继承）" min-width="210">
        <template #default="{ row }">
          <div class="role-list">
            <el-tag v-for="role in row.roles" :key="role.code" size="small" effect="plain">{{ role.name }}</el-tag>
            <span v-if="!row.roles?.length" class="muted">未分配</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.locked" type="warning" size="small">锁定</el-tag>
          <el-tag v-else-if="row.status === 'DISABLED'" type="info" size="small">禁用</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="155">
        <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRoles(row)">分配角色</el-button>
          <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
          <el-button v-if="row.locked" link type="warning" @click="unlock(row)">解锁</el-button>
          <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
        :page-size="size" :current-page="page + 1" :page-sizes="[10, 20, 50]"
        @current-change="onPage" @size-change="onSize" />
    </div>

    <el-dialog v-model="createDialog" title="新建用户" width="580px">
      <el-form label-position="top">
        <div class="grid-2">
          <el-form-item label="用户名">
            <el-input v-model="createForm.username" placeholder="字母开头，4-32 位" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="createForm.displayName" placeholder="展示名称" />
          </el-form-item>
        </div>
        <div class="grid-2">
          <el-form-item label="邮箱">
            <el-input v-model="createForm.email" placeholder="name@example.com" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="createForm.phone" maxlength="11" placeholder="可选" />
          </el-form-item>
        </div>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.password" type="password" show-password
            placeholder="至少 10 位，含大写/小写/数字/特殊字符三类" />
        </el-form-item>
        <el-form-item label="初始角色">
          <el-select v-model="createForm.roleIds" multiple filterable placeholder="可选择多个角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.name}（${role.code}）`" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="620px">
      <div class="dialog-user">
        <b>{{ roleTarget?.displayName || roleTarget?.username }}</b>
        <span class="muted">直接授予的角色；父角色继承的权限自动生效</span>
      </div>
      <el-checkbox-group v-model="selectedRoleIds" class="role-checkboxes">
        <el-checkbox v-for="role in roles" :key="role.id" :value="role.id" border>
          <div class="role-option">
            <div>
              <b>{{ role.name }}</b>
              <span class="role-code">{{ role.code }}</span>
            </div>
            <div class="role-meta">
              {{ role.description || '无描述' }}
              <span v-if="role.maxMembers"> · 上限 {{ role.maxMembers }} 人</span>
              <span v-if="role.requiredRoleName"> · 前置：{{ role.requiredRoleName }}</span>
            </div>
          </div>
        </el-checkbox>
      </el-checkbox-group>
      <p class="constraint-tip">保存时会校验静态职责分离（SSD）、角色基数与前置角色；冲突会明确提示。</p>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialog" title="重置密码" width="440px">
      <el-form label-position="top">
        <el-form-item :label="`为 ${resetTarget?.username} 设置新密码`">
          <el-input v-model="newPassword" type="password" show-password
            placeholder="至少 10 位，含三类字符" />
        </el-form-item>
      </el-form>
      <p class="constraint-tip">重置后该用户全部刷新令牌立即失效，需重新登录。</p>
      <template #footer>
        <el-button @click="resetDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignUserRoles,
  createUser,
  listRoles,
  listUsers,
  resetUserPassword,
  unlockUser,
  updateUserStatus
} from '../../api/rbac'
import { useAuthStore } from '../../stores/authStore'

const auth = useAuthStore()
const canWrite = computed(() => auth.hasPerm('rbac:user:write'))

const rows = ref([])
const roles = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)

const createDialog = ref(false)
const roleDialog = ref(false)
const resetDialog = ref(false)
const roleTarget = ref(null)
const resetTarget = ref(null)
const selectedRoleIds = ref([])
const newPassword = ref('')
const createForm = reactive({
  username: '', displayName: '', email: '', phone: '', password: '', roleIds: []
})

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  try {
    const result = await listUsers({ keyword: keyword.value, page: page.value, size: size.value })
    rows.value = result.data.items
    total.value = result.data.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const result = await listRoles()
    roles.value = result.data || []
  } catch {
    roles.value = []
  }
}

function reload() {
  page.value = 0
  load()
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

function openCreate() {
  Object.assign(createForm, {
    username: '', displayName: '', email: '', phone: '', password: '', roleIds: []
  })
  createDialog.value = true
}

async function submitCreate() {
  if (!createForm.username || !createForm.password) {
    ElMessage.warning('请填写用户名和初始密码')
    return
  }
  saving.value = true
  try {
    await createUser({ ...createForm })
    createDialog.value = false
    ElMessage.success('用户已创建')
    await load()
  } finally {
    saving.value = false
  }
}

function openRoles(row) {
  roleTarget.value = row
  selectedRoleIds.value = (row.roles || []).map((role) => role.id)
  roleDialog.value = true
}

async function submitRoles() {
  saving.value = true
  try {
    await assignUserRoles(roleTarget.value.id, selectedRoleIds.value)
    roleDialog.value = false
    ElMessage.success('角色已更新，该用户旧会话已失效')
    await load()
  } finally {
    saving.value = false
  }
}

function openReset(row) {
  resetTarget.value = row
  newPassword.value = ''
  resetDialog.value = true
}

async function submitReset() {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  saving.value = true
  try {
    await resetUserPassword(resetTarget.value.id, newPassword.value)
    resetDialog.value = false
    ElMessage.success('密码已重置')
  } finally {
    saving.value = false
  }
}

async function unlock(row) {
  await unlockUser(row.id)
  ElMessage.success('已解锁')
  await load()
}

async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(
    `确认${next === 'DISABLED' ? '禁用' : '启用'}用户「${row.username}」？`,
    '账号状态',
    { type: 'warning' }
  )
  await updateUserStatus(row.id, next)
  ElMessage.success('状态已更新')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadRoles()])
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.search {
  width: 280px;
}

.data-table {
  width: 100%;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  flex-shrink: 0;
}

.strong {
  color: #0f172a;
  font-weight: 600;
  font-size: 13px;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
}

.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.dialog-user {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 14px;
}

.role-checkboxes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.role-checkboxes :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
  padding: 9px 11px;
}

.role-option {
  display: flex;
  flex-direction: column;
  gap: 3px;
  white-space: normal;
  line-height: 1.4;
}

.role-code {
  color: #94a3b8;
  font-size: 10px;
  margin-left: 5px;
}

.role-meta {
  color: #94a3b8;
  font-size: 11px;
}

.constraint-tip {
  margin-top: 14px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 720px) {
  .grid-2,
  .role-checkboxes {
    grid-template-columns: 1fr;
  }
}
</style>
