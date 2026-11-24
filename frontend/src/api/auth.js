import request from './request'

export const getProviders = () => request.get('/auth/providers', { silent: true })
export const getCaptcha = () => request.get('/auth/captcha', { silent: true })

export const loginPassword = (data) => request.post('/auth/login/password', data, { silent: true })
export const loginOtp = (data) => request.post('/auth/login/otp', data, { silent: true })
export const sendOtp = (data) => request.post('/auth/otp/send', data)

export const register = (data) => request.post('/auth/register', data)
export const forgotPassword = (data) => request.post('/auth/forgot', data, { silent: true })
export const resetPassword = (data) => request.post('/auth/reset', data)

export const refreshSession = () => request.post('/auth/refresh', null, { skipAuthRefresh: true, silent: true })
export const logout = () => request.post('/auth/logout', null, { silent: true })
export const getMe = () => request.get('/auth/me', { skipAuthRefresh: true, silent: true })
export const changePassword = (data) => request.put('/auth/password', data)
export const switchRoles = (roleCodes) => request.post('/auth/session/roles', { roleCodes })

export const getSsoConfig = () => request.get('/auth/sso/config', { silent: true })
export const exchangeSsoCode = (data) => request.post('/auth/sso/token', data)

export const getDevOutbox = () => request.get('/auth/dev/outbox', { silent: true })
