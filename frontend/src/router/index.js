import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'chat', component: () => import('../views/Chat.vue') },
  { path: '/resources', name: 'resources', component: () => import('../views/Resources.vue') },
  { path: '/path', name: 'path', component: () => import('../views/PathMap.vue') },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue') }
]

export default createRouter({
  history: createWebHistory(),
  routes
})