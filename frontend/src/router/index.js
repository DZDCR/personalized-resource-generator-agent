import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/authStore'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/auth/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('../views/auth/Register.vue'), meta: { public: true } },
  { path: '/forgot', name: 'forgot', component: () => import('../views/auth/Forgot.vue'), meta: { public: true } },
  { path: '/sso/callback', name: 'sso-callback', component: () => import('../views/auth/SsoCallback.vue'), meta: { public: true } },

  { path: '/', name: 'chat', component: () => import('../views/Chat.vue'), meta: { requiresAuth: true, title: '对话学习' } },
  { path: '/resources', name: 'resources', component: () => import('../views/Resources.vue'), meta: { requiresAuth: true, title: '学习资源' } },
  { path: '/path', name: 'path', component: () => import('../views/PathMap.vue'), meta: { requiresAuth: true, title: '学习路径' } },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { requiresAuth: true, title: '效果评估' } },

  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    redirect: '/admin/users',
    meta: { requiresAuth: true, permission: 'rbac:admin', title: '权限中心' },
    children: [
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../views/admin/Users.vue'),
        meta: { permission: 'rbac:user:read', title: '用户管理' }
      },
      {
        path: 'roles',
        name: 'admin-roles',
        component: () => import('../views/admin/Roles.vue'),
        meta: { permission: 'rbac:role:read', title: '角色管理' }
      },
      {
        path: 'permissions',
        name: 'admin-permissions',
        component: () => import('../views/admin/Permissions.vue'),
        meta: { permission: 'rbac:permission:read', title: '权限管理' }
      },
      {
        path: 'constraints',
        name: 'admin-constraints',
        component: () => import('../views/admin/Constraints.vue'),
        meta: { permission: 'rbac:constraint:read', title: '职责分离约束' }
      },
      {
        path: 'audit',
        name: 'admin-audit',
        component: () => import('../views/admin/Audit.vue'),
        meta: { permission: 'rbac:audit:read', title: '审计日志' }
      }
    ]
  },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.bootstrap()

  if (to.meta.public) {
    if (auth.isLoggedIn && (to.name === 'login' || to.name === 'register')) {
      return { path: '/' }
    }
    return true
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  for (const record of to.matched) {
    const permission = record.meta?.permission
    if (permission && !auth.hasPerm(permission)) {
      ElMessage.error('没有访问该页面的权限')
      return { path: '/' }
    }
  }
  return true
})

export default router
