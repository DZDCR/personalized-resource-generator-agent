import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  exchangeSsoCode,
  getMe,
  loginOtp as apiLoginOtp,
  loginPassword as apiLoginPassword,
  logout as apiLogout,
  refreshSession,
  register as apiRegister,
  switchRoles as apiSwitchRoles
} from '../api/auth'
import { tokenStore } from '../api/token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(tokenStore.get())
  const user = ref(null)
  const permissions = ref([])
  const activeRoles = ref([])
  const assignedRoles = ref([])
  const initialized = ref(false)
  let bootPromise = null

  const isLoggedIn = computed(() => Boolean(token.value && user.value))
  const displayName = computed(() => user.value?.displayName || user.value?.username || '未登录')
  const roleNameByCode = computed(() => {
    const map = {}
    for (const role of assignedRoles.value) map[role.code] = role.name
    return map
  })

  function applySession(data) {
    if (!data) return
    if (data.accessToken) {
      token.value = data.accessToken
      tokenStore.set(data.accessToken)
    }
    if (data.user) user.value = data.user
    if (Array.isArray(data.permissions)) permissions.value = data.permissions
    if (Array.isArray(data.activeRoleCodes)) activeRoles.value = data.activeRoleCodes
    if (Array.isArray(data.assignedRoles)) assignedRoles.value = data.assignedRoles
  }

  function applyMe(data) {
    if (!data) return
    user.value = data.user
    permissions.value = data.permissions || []
    activeRoles.value = data.activeRoleCodes || []
    assignedRoles.value = data.assignedRoles || []
  }

  function clear() {
    token.value = ''
    tokenStore.clear()
    user.value = null
    permissions.value = []
    activeRoles.value = []
    assignedRoles.value = []
    initialized.value = true
  }

  async function bootstrap() {
    if (initialized.value) return
    if (bootPromise) return bootPromise
    bootPromise = (async () => {
      try {
        if (!token.value) {
          const refreshed = await refreshSession()
          applySession(refreshed.data)
        } else {
          try {
            const me = await getMe()
            applyMe(me.data)
          } catch {
            const refreshed = await refreshSession()
            applySession(refreshed.data)
          }
        }
      } catch {
        clear()
      } finally {
        initialized.value = true
        bootPromise = null
      }
    })()
    return bootPromise
  }

  async function loginPassword(payload) {
    const result = await apiLoginPassword(payload)
    applySession(result.data)
    initialized.value = true
    return result.data
  }

  async function loginOtp(payload) {
    const result = await apiLoginOtp(payload)
    applySession(result.data)
    initialized.value = true
    return result.data
  }

  async function exchangeSso(payload) {
    const result = await exchangeSsoCode(payload)
    applySession(result.data)
    initialized.value = true
    return result.data
  }

  async function register(payload) {
    const result = await apiRegister(payload)
    applySession(result.data)
    initialized.value = true
    return result.data
  }

  async function switchActiveRoles(roleCodes) {
    const result = await apiSwitchRoles(roleCodes)
    applySession(result.data)
    return result.data
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      clear()
    }
  }

  function hasPerm(code) {
    return permissions.value.includes(code)
  }

  function hasAnyPerm(...codes) {
    return codes.flat().some((code) => permissions.value.includes(code))
  }

  window.addEventListener('auth:refreshed', (event) => applySession(event.detail))

  return {
    token,
    user,
    permissions,
    activeRoles,
    assignedRoles,
    initialized,
    isLoggedIn,
    displayName,
    roleNameByCode,
    bootstrap,
    loginPassword,
    loginOtp,
    exchangeSso,
    register,
    switchActiveRoles,
    logout,
    hasPerm,
    hasAnyPerm
  }
})
