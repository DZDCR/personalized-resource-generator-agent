<template>
  <router-view v-if="isPublic" />

  <el-container v-else class="app-shell">
    <el-aside width="236px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">AI</div>
        <div>
          <div class="brand-title">个性化学习</div>
          <div class="brand-sub">多智能体平台</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="nav-menu" background-color="transparent">
        <el-menu-item index="/">
          <span class="nav-dot" />对话学习
        </el-menu-item>
        <el-menu-item index="/im">
          <span class="nav-dot" />班级聊天
        </el-menu-item>
        <el-menu-item index="/resources">
          <span class="nav-dot" />学习资源
        </el-menu-item>
        <el-menu-item index="/path">
          <span class="nav-dot" />学习路径
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <span class="nav-dot" />效果评估
        </el-menu-item>

        <el-sub-menu v-if="auth.hasPerm('rbac:admin')" index="admin">
          <template #title>
            <span class="nav-dot accent" />权限中心
          </template>
          <el-menu-item v-if="auth.hasPerm('rbac:user:read')" index="/admin/users">用户管理</el-menu-item>
          <el-menu-item v-if="auth.hasPerm('rbac:role:read')" index="/admin/roles">角色管理</el-menu-item>
          <el-menu-item v-if="auth.hasPerm('rbac:permission:read')" index="/admin/permissions">权限管理</el-menu-item>
          <el-menu-item v-if="auth.hasPerm('rbac:constraint:read')" index="/admin/constraints">职责分离约束</el-menu-item>
          <el-menu-item v-if="auth.hasPerm('rbac:audit:read')" index="/admin/audit">审计日志</el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="sidebar-footer">
        <div>RBAC3 · Spring Security</div>
        <div class="muted">JWT + 慢哈希 + 职责分离</div>
      </div>
    </el-aside>

    <el-container class="main-wrap">
      <el-header class="topbar">
        <div class="page-title">{{ title }}</div>
        <div class="top-actions">
          <div class="role-tags">
            <el-tag v-for="code in auth.activeRoles" :key="code" size="small" effect="plain" class="role-tag">
              {{ auth.roleNameByCode[code] || code }}
            </el-tag>
          </div>
          <el-button text class="switch-btn" @click="openRoleDialog">切换角色</el-button>
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="user-chip">
              <span class="avatar">{{ avatarText }}</span>
              {{ auth.displayName }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="page-body">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="roleDialog" title="切换本次会话激活角色" width="520px">
      <p class="dialog-tip">
        角色继承会自动生效；若角色间存在动态职责分离（DSD）约束，同一次会话只能激活其中一个。
      </p>
      <el-checkbox-group v-model="selectedRoles" class="role-options">
        <el-checkbox v-for="role in auth.assignedRoles" :key="role.code" :value="role.code" border>
          {{ role.name }}
          <span class="role-code">{{ role.code }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :loading="switching" @click="applyRoles">应用并重新签发令牌</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialog" title="修改密码" width="460px">
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="原密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 10 位，含三类字符" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="changingPassword" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from './stores/authStore'
import { changePassword } from './api/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isPublic = computed(() => Boolean(route.meta.public))
const title = computed(() => route.meta.title || '对话学习')
const activeMenu = computed(() => (route.path.startsWith('/admin') ? route.path : route.path))
const avatarText = computed(() => (auth.displayName || 'U').slice(0, 1).toUpperCase())

const roleDialog = ref(false)
const selectedRoles = ref([])
const switching = ref(false)

const passwordDialog = ref(false)
const changingPassword = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

function openRoleDialog() {
  selectedRoles.value = [...auth.activeRoles]
  roleDialog.value = true
}

async function applyRoles() {
  if (!selectedRoles.value.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  switching.value = true
  try {
    await auth.switchActiveRoles(selectedRoles.value)
    roleDialog.value = false
    ElMessage.success('已重新签发会话凭证')
  } catch (error) {
    if (error.code === 40901) {
      ElMessage.warning(error.message || '角色组合违反动态职责分离约束')
    }
  } finally {
    switching.value = false
  }
}

async function onUserCommand(command) {
  if (command === 'password') {
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    passwordDialog.value = true
    return
  }
  if (command === 'logout') {
    await ElMessageBox.confirm('退出后需要重新登录，是否继续？', '退出登录', { type: 'warning' })
    await auth.logout()
    router.push('/login')
  }
}

async function submitPassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    await changePassword({ ...passwordForm })
    passwordDialog.value = false
    ElMessage.success('密码已修改，请重新登录')
    await auth.logout()
    router.push('/login')
  } finally {
    changingPassword.value = false
  }
}
</script>

<style scoped>
.app-shell {
  height: 100vh;
  background: #f5f7fb;
}

.sidebar {
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f172a 0%, #111c33 60%, #0b1220 100%);
  color: #cbd5e1;
  border-right: 1px solid rgba(148, 163, 184, 0.12);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 20px 18px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-weight: 800;
  color: #0b1220;
  background: linear-gradient(135deg, #7dd3fc, #a78bfa);
}

.brand-title {
  color: #f8fafc;
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.5px;
}

.brand-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}

.nav-menu {
  border-right: none;
  flex: 1;
  padding: 6px 10px;
}

.nav-menu :deep(.el-menu-item),
.nav-menu :deep(.el-sub-menu__title) {
  color: #cbd5e1;
  border-radius: 8px;
  margin: 4px 0;
  height: 42px;
}

.nav-menu :deep(.el-menu-item:hover),
.nav-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(148, 163, 184, 0.12);
  color: #fff;
}

.nav-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.22), rgba(167, 139, 250, 0.14));
  color: #fff;
}

.nav-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #64748b;
  margin-right: 10px;
}

.nav-dot.accent {
  background: #a78bfa;
}

.sidebar-footer {
  padding: 18px 20px;
  font-size: 11px;
  color: #64748b;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.sidebar-footer .muted {
  color: #475569;
  margin-top: 4px;
}

.main-wrap {
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8edf5;
  height: 62px;
}

.page-title {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-tags {
  display: flex;
  gap: 4px;
  max-width: 360px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.role-tag {
  border-color: #dbeafe;
  color: #1d4ed8;
  background: #eff6ff;
}

.switch-btn {
  color: #475569;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #0f172a;
  font-size: 14px;
  padding: 6px 8px;
  border-radius: 8px;
}

.user-chip:hover {
  background: #f1f5f9;
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
}

.page-body {
  padding: 22px;
  overflow: auto;
}

.dialog-tip {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 14px;
}

.role-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.role-options :deep(.el-checkbox) {
  margin-right: 0;
  width: 100%;
}

.role-code {
  color: #94a3b8;
  font-size: 11px;
  margin-left: 8px;
}
</style>
