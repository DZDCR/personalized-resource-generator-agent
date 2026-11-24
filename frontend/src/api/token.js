const KEY = 'rbac_access_token'

export const tokenStore = {
  get() {
    return sessionStorage.getItem(KEY) || ''
  },
  set(token) {
    if (token) sessionStorage.setItem(KEY, token)
    else sessionStorage.removeItem(KEY)
  },
  clear() {
    sessionStorage.removeItem(KEY)
  }
}
