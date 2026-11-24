<template>
  <div class="admin-page">
    <div class="admin-hero">
      <div>
        <div class="eyebrow">RBAC3 · ROLE-BASED ACCESS CONTROL</div>
        <h1>权限中心</h1>
        <p>用户 → 角色 → 权限，叠加角色继承（RBAC1）与职责分离约束（RBAC2）。</p>
      </div>
      <div class="model-chain">
        <span>RBAC0 授予</span><i>→</i><span>RBAC1 继承</span><i>→</i><span>RBAC2 约束</span>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat">
        <div class="stat-label">用户</div>
        <div class="stat-value">{{ overview.users }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">角色</div>
        <div class="stat-value">{{ overview.roles }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">权限点</div>
        <div class="stat-value">{{ overview.permissions }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">职责分离约束</div>
        <div class="stat-value">{{ overview.constraints }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">今日登录成功</div>
        <div class="stat-value success">{{ overview.loginSuccessToday }}</div>
      </div>
      <div class="stat">
        <div class="stat-label">今日登录失败</div>
        <div class="stat-value danger">{{ overview.loginFailureToday }}</div>
      </div>
    </div>

    <div class="admin-panel">
      <el-tabs :model-value="activeTab" class="admin-tabs" @tab-change="onTabChange">
        <el-tab-pane v-if="auth.hasPerm('rbac:user:read')" label="用户管理" name="/admin/users" />
        <el-tab-pane v-if="auth.hasPerm('rbac:role:read')" label="角色管理" name="/admin/roles" />
        <el-tab-pane v-if="auth.hasPerm('rbac:permission:read')" label="权限管理" name="/admin/permissions" />
        <el-tab-pane v-if="auth.hasPerm('rbac:constraint:read')" label="职责分离约束" name="/admin/constraints" />
        <el-tab-pane v-if="auth.hasPerm('rbac:audit:read')" label="审计日志" name="/admin/audit" />
      </el-tabs>

      <div class="tab-body">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOverview } from '../../api/rbac'
import { useAuthStore } from '../../stores/authStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const overview = ref({
  users: 0,
  roles: 0,
  permissions: 0,
  constraints: 0,
  loginSuccessToday: 0,
  loginFailureToday: 0
})

const activeTab = computed(() => route.path)

async function loadOverview() {
  try {
    const result = await getOverview()
    overview.value = result.data || overview.value
  } catch {
    // 无概览权限时保持默认
  }
}

function onTabChange(name) {
  if (name !== route.path) router.push(name)
}

onMounted(loadOverview)
watch(() => route.path, loadOverview)
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px;
  border-radius: 16px;
  color: #e2e8f0;
  background: linear-gradient(115deg, #0f172a 0%, #172554 55%, #312e81 100%);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.16);
}

.eyebrow {
  font-size: 10px;
  letter-spacing: 1.6px;
  color: #93c5fd;
  margin-bottom: 8px;
}

.admin-hero h1 {
  font-size: 26px;
  margin-bottom: 6px;
}

.admin-hero p {
  color: #cbd5e1;
  font-size: 13px;
}

.model-chain {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #c7d2fe;
  border: 1px solid rgba(165, 180, 252, 0.35);
  background: rgba(99, 102, 241, 0.16);
  padding: 9px 13px;
  border-radius: 999px;
  white-space: nowrap;
}

.model-chain i {
  font-style: normal;
  color: #818cf8;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.stat {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  padding: 14px 16px;
}

.stat-label {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1;
}

.stat-value.success {
  color: #15803d;
}

.stat-value.danger {
  color: #b91c1c;
}

.admin-panel {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 14px;
  padding: 6px 20px 20px;
}

.admin-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.admin-tabs :deep(.el-tabs__item) {
  font-size: 13px;
}

.tab-body {
  min-height: 420px;
}

@media (max-width: 1280px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .admin-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .model-chain {
    display: none;
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
