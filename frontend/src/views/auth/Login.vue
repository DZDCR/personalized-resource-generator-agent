<template>
  <div class="auth-page">
    <div class="auth-bg">
      <div class="glow glow-a" />
      <div class="glow glow-b" />
    </div>

    <section class="auth-hero">
      <div class="hero-brand">
        <div class="hero-mark">AI</div>
        <span>个性化资源生成与学习多智能体系统</span>
      </div>
      <h1>让每一次学习<br /><span>都有权限边界</span></h1>
      <p class="hero-copy">
        基于 Spring Security 的 RBAC3 权限体系：角色继承、静态/动态职责分离、慢哈希口令、
        多因子登录与全链路安全审计。
      </p>
      <ul class="hero-list">
        <li><b>4</b> 种登录方式：账号密码 / 邮箱验证码 / 短信验证码 / 统一身份认证</li>
        <li><b>3</b> 层权限模型：RBAC0 授予 + RBAC1 继承 + RBAC2 约束</li>
        <li><b>1</b> 条审计链：登录、改密、授权、约束变更全部留痕</li>
      </ul>
      <div class="hero-badges">
        <span>PBKDF2 慢哈希 + 随机盐</span>
        <span>JWT 短期凭证</span>
        <span>PKCE 授权码</span>
      </div>
    </section>

    <section class="auth-card">
      <header class="card-head">
        <h2>登录学习平台</h2>
        <p>使用任一方式进入你的个性化学习空间</p>
      </header>

      <el-tabs v-model="activeTab" class="auth-tabs" @tab-change="onTabChange">
        <el-tab-pane label="账号密码" name="password">
          <el-form label-position="top" @submit.prevent="submitPassword()">
            <el-form-item label="账号">
              <el-input v-model="passwordForm.account" size="large" placeholder="用户名 / 邮箱 / 手机号" clearable />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="passwordForm.password" size="large" type="password" show-password
                placeholder="请输入登录密码" @keyup.enter="submitPassword()" />
            </el-form-item>
            <el-form-item label="图形验证码">
              <div class="captcha-row">
                <el-input v-model="passwordForm.captchaCode" size="large" placeholder="输入右侧字符"
                  @keyup.enter="submitPassword()" />
                <img v-if="captcha.image" :src="captcha.image" class="captcha-img" alt="图形验证码"
                  title="点击刷新" @click="loadCaptcha" />
                <el-button v-else size="large" text @click="loadCaptcha">加载</el-button>
              </div>
            </el-form-item>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="submitPassword()">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="邮箱验证码" name="email">
          <el-form label-position="top">
            <el-form-item label="邮箱">
              <el-input v-model="emailForm.target" size="large" placeholder="name@example.com" />
            </el-form-item>
            <el-form-item label="验证码">
              <div class="code-row">
                <el-input v-model="emailForm.code" size="large" placeholder="6 位验证码" maxlength="6"
                  @keyup.enter="submitOtp('EMAIL')" />
                <el-button size="large" :disabled="countdown.email > 0" @click="sendCode('EMAIL')">
                  {{ countdown.email > 0 ? `${countdown.email}s` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <div v-if="debug.email" class="debug-code">开发模式验证码：<b>{{ debug.email }}</b></div>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading"
              @click="submitOtp('EMAIL')">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="短信验证码" name="sms">
          <el-form label-position="top">
            <el-form-item label="手机号">
              <el-input v-model="smsForm.target" size="large" placeholder="11 位手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="验证码">
              <div class="code-row">
                <el-input v-model="smsForm.code" size="large" placeholder="6 位验证码" maxlength="6"
                  @keyup.enter="submitOtp('SMS')" />
                <el-button size="large" :disabled="countdown.sms > 0" @click="sendCode('SMS')">
                  {{ countdown.sms > 0 ? `${countdown.sms}s` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <div v-if="debug.sms" class="debug-code">开发模式验证码：<b>{{ debug.sms }}</b></div>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading"
              @click="submitOtp('SMS')">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="统一身份认证" name="sso">
          <div class="sso-panel">
            <div class="sso-icon">SSO</div>
            <h3>{{ sso.provider || '统一身份认证' }}</h3>
            <p>通过学校/企业 IdP 的 OIDC 授权码 + PKCE 完成单点登录，本地不保存 IdP 密码。</p>
            <el-button type="primary" size="large" class="submit-btn" :loading="ssoLoading"
              @click="startSso">使用统一身份认证登录</el-button>
            <div class="sso-flow">
              <span>1. 跳转 IdP</span><i>→</i><span>2. 授权码</span><i>→</i><span>3. 本地会话</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="card-links">
        <router-link to="/register">注册新账号</router-link>
        <span class="dot">·</span>
        <router-link to="/forgot">忘记密码？</router-link>
      </div>

      <div v-if="providers.devMode" class="demo-box">
        <div class="demo-title">演示账号（开发模式）</div>
        <div class="demo-grid">
          <button type="button" @click="fillDemo('admin', 'Admin@12345')">admin / Admin@12345</button>
          <button type="button" @click="fillDemo('reviewer', 'Reviewer@12345')">reviewer / Reviewer@12345</button>
          <button type="button" @click="fillDemo('student', 'Student@12345')">student / Student@12345</button>
        </div>
        <p>reviewer 拥有审核员+发布员角色，登录时会触发 DSD 角色选择。</p>
      </div>
    </section>

    <el-dialog v-model="roleDialog" title="请选择本次会话激活的角色" width="480px" :close-on-click-modal="false">
      <p class="role-hint">{{ roleReason }}</p>
      <el-checkbox-group v-model="selectedRoles" class="role-choice-list">
        <el-checkbox v-for="role in roleOptions" :key="role.code" :value="role.code" border>
          {{ role.name }} <span class="role-code">{{ role.code }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="confirmRoles">确认并登录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, getProviders, getSsoConfig, loginOtp, loginPassword, sendOtp } from '../../api/auth'
import { useAuthStore } from '../../stores/authStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('password')
const loading = ref(false)
const ssoLoading = ref(false)
const providers = ref({})
const sso = ref({ provider: 'OIDC-DEMO', clientId: 'learning-web' })
const captcha = reactive({ captchaId: '', image: '' })
const debug = reactive({ email: '', sms: '' })
const countdown = reactive({ email: 0, sms: 0 })
const timers = {}

const passwordForm = reactive({ account: '', password: '', captchaId: '', captchaCode: '' })
const emailForm = reactive({ target: '', code: '' })
const smsForm = reactive({ target: '', code: '' })

const roleDialog = ref(false)
const roleOptions = ref([])
const selectedRoles = ref([])
const roleReason = ref('')
let pendingAction = null

function targetAfterLogin() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/'
}

async function loadCaptcha() {
  try {
    const result = await getCaptcha()
    captcha.captchaId = result.data.captchaId
    captcha.image = result.data.image
  } catch {
    // 验证码服务异常时保持静默，由登录接口给出统一提示
  }
}

async function loadProviders() {
  try {
    const result = await getProviders()
    providers.value = result.data || {}
  } catch {
    providers.value = {}
  }
  try {
    const result = await getSsoConfig()
    sso.value = result.data || sso.value
  } catch {
    // 保持默认配置
  }
}

function onTabChange(name) {
  if (name === 'password' && !captcha.image) loadCaptcha()
}

function fillDemo(account, password) {
  passwordForm.account = account
  passwordForm.password = password
  activeTab.value = 'password'
  loadCaptcha()
}

function startCountdown(channel) {
  const key = channel === 'EMAIL' ? 'email' : 'sms'
  countdown[key] = 60
  clearInterval(timers[key])
  timers[key] = setInterval(() => {
    countdown[key] -= 1
    if (countdown[key] <= 0) {
      clearInterval(timers[key])
    }
  }, 1000)
}

async function sendCode(channel) {
  const target = channel === 'EMAIL' ? emailForm.target : smsForm.target
  if (!target) {
    ElMessage.warning(channel === 'EMAIL' ? '请输入邮箱' : '请输入手机号')
    return
  }
  try {
    const result = await sendOtp({ scene: 'LOGIN', channel, target })
    if (channel === 'EMAIL') debug.email = result.data.debugCode || ''
    else debug.sms = result.data.debugCode || ''
    startCountdown(channel)
    ElMessage.success(result.data.message || '验证码已发送')
  } catch {
    // request 拦截器已提示
  }
}

async function submitPassword(activeRoleCodes = null) {
  if (!passwordForm.account || !passwordForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  if (providers.value.captcha !== false && !passwordForm.captchaCode) {
    ElMessage.warning('请输入图形验证码')
    return
  }
  loading.value = true
  try {
    await auth.loginPassword({
      account: passwordForm.account,
      password: passwordForm.password,
      captchaId: captcha.captchaId,
      captchaCode: passwordForm.captchaCode,
      activeRoleCodes
    })
    ElMessage.success('登录成功')
    router.push(targetAfterLogin())
  } catch (error) {
    if (error.code === 40901) {
      handleRoleChoice(error, (codes) => submitPassword(codes))
    } else {
      ElMessage.error(error.message || '登录失败')
      passwordForm.captchaCode = ''
      loadCaptcha()
    }
  } finally {
    loading.value = false
  }
}

async function submitOtp(channel, activeRoleCodes = null) {
  const form = channel === 'EMAIL' ? emailForm : smsForm
  if (!form.target || !form.code) {
    ElMessage.warning('请输入邮箱/手机号和验证码')
    return
  }
  loading.value = true
  try {
    await auth.loginOtp({
      channel,
      target: form.target,
      code: form.code,
      activeRoleCodes
    })
    ElMessage.success('登录成功')
    router.push(targetAfterLogin())
  } catch (error) {
    if (error.code === 40901) {
      handleRoleChoice(error, (codes) => submitOtp(channel, codes))
    } else {
      ElMessage.error(error.message || '登录失败')
    }
  } finally {
    loading.value = false
  }
}

function handleRoleChoice(error, retry) {
  if (error.code === 40901 && error.data?.options?.length) {
    roleOptions.value = error.data.options
    roleReason.value = error.data.reason || '请选择本次会话要激活的角色'
    const preferred = error.data.conflictingRoles?.[0]?.code || error.data.options[0].code
    selectedRoles.value = [preferred]
    pendingAction = retry
    roleDialog.value = true
  }
}

async function confirmRoles() {
  if (!selectedRoles.value.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  roleDialog.value = false
  if (pendingAction) {
    await pendingAction([...selectedRoles.value])
  }
}

async function randomVerifier() {
  const bytes = new Uint8Array(48)
  crypto.getRandomValues(bytes)
  return base64Url(bytes)
}

function base64Url(bytes) {
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

async function sha256Base64Url(value) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return base64Url(new Uint8Array(digest))
}

async function startSso() {
  ssoLoading.value = true
  try {
    const verifier = await randomVerifier()
    const state = await randomVerifier()
    sessionStorage.setItem('sso_pkce_verifier', verifier)
    sessionStorage.setItem('sso_state', state)
    const redirectUri = `${window.location.origin}/sso/callback`
    const clientId = sso.value.clientId || 'learning-web'
    const challenge = await sha256Base64Url(verifier)
    const url = `/api/auth/sso/authorize?client_id=${encodeURIComponent(clientId)}`
      + `&redirect_uri=${encodeURIComponent(redirectUri)}`
      + `&state=${encodeURIComponent(state)}`
      + `&response_type=code`
      + `&code_challenge=${encodeURIComponent(challenge)}`
      + `&code_challenge_method=S256`
    window.location.href = url
  } catch (error) {
    ElMessage.error('统一身份认证跳转失败：' + (error.message || '未知错误'))
    ssoLoading.value = false
  }
}

onMounted(() => {
  loadProviders()
  loadCaptcha()
})

onBeforeUnmount(() => {
  Object.values(timers).forEach((timer) => clearInterval(timer))
})
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(420px, 520px);
  gap: 56px;
  align-items: center;
  padding: 48px 7vw;
  background: #070b14;
  color: #e2e8f0;
  position: relative;
  overflow: hidden;
}

.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.45;
}

.glow-a {
  width: 560px;
  height: 560px;
  background: #1d4ed8;
  top: -180px;
  left: -120px;
}

.glow-b {
  width: 480px;
  height: 480px;
  background: #7c3aed;
  bottom: -200px;
  right: 12%;
  opacity: 0.3;
}

.auth-hero {
  position: relative;
  z-index: 1;
  max-width: 700px;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #cbd5e1;
  font-size: 13px;
  letter-spacing: 0.4px;
}

.hero-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-weight: 800;
  color: #08111f;
  background: linear-gradient(135deg, #7dd3fc, #a78bfa);
}

.auth-hero h1 {
  margin: 44px 0 20px;
  font-size: clamp(38px, 4.4vw, 62px);
  line-height: 1.18;
  letter-spacing: -1.5px;
  color: #f8fafc;
  padding-bottom: 6px;
}

.auth-hero h1 span {
  display: inline-block;
  padding-bottom: 4px;
  background: linear-gradient(90deg, #7dd3fc, #c4b5fd);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-copy {
  max-width: 560px;
  color: #94a3b8;
  line-height: 1.9;
  font-size: 15px;
}

.hero-list {
  list-style: none;
  margin: 30px 0;
  display: grid;
  gap: 12px;
  color: #cbd5e1;
  font-size: 14px;
}

.hero-list li {
  padding-left: 20px;
  position: relative;
  line-height: 1.7;
}

.hero-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #38bdf8, #a78bfa);
}

.hero-list b {
  color: #fff;
  font-size: 15px;
  margin-right: 4px;
}

.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-badges span {
  border: 1px solid rgba(148, 163, 184, 0.25);
  color: #94a3b8;
  font-size: 11px;
  padding: 5px 10px;
  border-radius: 999px;
}

.auth-card {
  position: relative;
  z-index: 1;
  background: #ffffff;
  color: #0f172a;
  border-radius: 20px;
  padding: 30px 32px 26px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.45);
}

.card-head h2 {
  font-size: 24px;
  margin-bottom: 6px;
}

.card-head p {
  color: #64748b;
  font-size: 13px;
  margin-bottom: 8px;
}

.auth-tabs :deep(.el-tabs__item) {
  font-size: 13px;
  padding: 0 12px;
}

.auth-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #eef2f7;
}

.auth-tabs :deep(.el-form-item) {
  margin-bottom: 14px;
}

.auth-tabs :deep(.el-form-item__label) {
  font-size: 12px;
  color: #64748b;
  padding-bottom: 4px;
}

.captcha-row,
.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-img {
  height: 40px;
  width: 122px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  flex-shrink: 0;
}

.code-row :deep(.el-button) {
  flex-shrink: 0;
  width: 118px;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  height: 44px;
  font-weight: 600;
  background: linear-gradient(135deg, #2563eb, #6d28d9);
  border: none;
}

.card-links {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: #cbd5e1;
}

.card-links a {
  color: #2563eb;
  text-decoration: none;
}

.card-links .dot {
  margin: 0 8px;
  color: #cbd5e1;
}

.debug-code {
  margin: -4px 0 12px;
  font-size: 12px;
  color: #9a6700;
  background: #fff8c5;
  border: 1px solid #f0e6b2;
  border-radius: 8px;
  padding: 7px 10px;
}

.demo-box {
  margin-top: 18px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px dashed #dbe3ef;
}

.demo-title {
  font-size: 12px;
  color: #475569;
  font-weight: 600;
  margin-bottom: 8px;
}

.demo-grid {
  display: grid;
  gap: 6px;
}

.demo-grid button {
  text-align: left;
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 7px;
  padding: 6px 9px;
  font-size: 12px;
  color: #334155;
  cursor: pointer;
}

.demo-grid button:hover {
  border-color: #93c5fd;
  color: #1d4ed8;
}

.demo-box p {
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.6;
}

.sso-panel {
  text-align: center;
  padding: 10px 6px 4px;
}

.sso-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #0ea5e9, #6366f1);
  letter-spacing: 1px;
}

.sso-panel h3 {
  font-size: 18px;
  margin-bottom: 8px;
}

.sso-panel p {
  color: #64748b;
  font-size: 13px;
  line-height: 1.8;
  margin-bottom: 18px;
}

.sso-flow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  color: #94a3b8;
  font-size: 11px;
}

.sso-flow i {
  font-style: normal;
  color: #cbd5e1;
}

.role-hint {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 14px;
}

.role-choice-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-choice-list :deep(.el-checkbox) {
  margin-right: 0;
}

.role-code {
  color: #94a3b8;
  font-size: 11px;
  margin-left: 6px;
}

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 30px 20px;
  }

  .auth-hero {
    display: none;
  }

  .auth-card {
    max-width: 520px;
    margin: 0 auto;
    width: 100%;
  }
}
</style>
