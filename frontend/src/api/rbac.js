import request from './request'

export const getOverview = () => request.get('/admin/overview')

export const listUsers = (params) => request.get('/admin/users', { params })
export const createUser = (data) => request.post('/admin/users', data)
export const updateUserStatus = (id, status) => request.put(`/admin/users/${id}/status`, { status })
export const assignUserRoles = (id, roleIds) => request.put(`/admin/users/${id}/roles`, { roleIds })
export const unlockUser = (id) => request.post(`/admin/users/${id}/unlock`)
export const resetUserPassword = (id, newPassword) => request.put(`/admin/users/${id}/password`, { newPassword })

export const listRoles = () => request.get('/admin/roles')
export const getRoleTree = () => request.get('/admin/roles/tree')
export const createRole = (data) => request.post('/admin/roles', data)
export const updateRole = (id, data) => request.put(`/admin/roles/${id}`, data)
export const deleteRole = (id) => request.delete(`/admin/roles/${id}`)
export const getRolePermissions = (id) => request.get(`/admin/roles/${id}/permissions`)
export const assignRolePermissions = (id, permissionIds) =>
  request.put(`/admin/roles/${id}/permissions`, { permissionIds })

export const listPermissions = () => request.get('/admin/permissions')
export const createPermission = (data) => request.post('/admin/permissions', data)
export const updatePermission = (id, data) => request.put(`/admin/permissions/${id}`, data)
export const deletePermission = (id) => request.delete(`/admin/permissions/${id}`)

export const listConstraints = () => request.get('/admin/constraints')
export const createConstraint = (data) => request.post('/admin/constraints', data)
export const updateConstraint = (id, data) => request.put(`/admin/constraints/${id}`, data)
export const deleteConstraint = (id) => request.delete(`/admin/constraints/${id}`)

export const searchAudit = (params) => request.get('/admin/audit', { params })
