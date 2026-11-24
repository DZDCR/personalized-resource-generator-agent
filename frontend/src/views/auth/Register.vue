<template>
  <div class="register-page">
    <div class="register-card">
      <header class="head">
        <div class="brand"><span class="mark">AI</span> 个性化学习平台</div>
        <h1>创建学习账号</h1>
        <p>注册即加入学生角色；教师/审核等角色由管理员授予。</p>
      </header>

      <el-form label-position="top" class="form" @submit.prevent="submit">
        <div class="grid-2">
          <el-form-item label="用户名">
            <el-input v-model="form.username" size="large" placeholder="字母开头，4-32 位" />
          </el-form-item>
          <el-form-item label="昵称（可选）">
            <el-input v-model="form.displayName" size="large" placeholder="展示名称" />
          </el-form-item>
        </div>

        <el-form-item label="邮箱">
          <el-input v-model="form.email" size="large" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item label="邮箱验证码">
          <div class="row">
            <el-input v-model="form.emailCode" size="large" maxlength="6" placeholder="6 位验证码" />
            <el-button size="large" :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <div v-if="debugCode" class="debug">开发模式验证码：<b>{{ debugCode }}</b></div>

        <el-form-item label="手机号（可选）">
          <el-input v-model="form.phone" size="large" maxlength="11" placeholder="用于短信登录/找回密码" />
        </el-form-item>

        <div class="grid-2">
          <el-form-item label="密码">
            <el-input v-model="form.password" size="large" type="password" show-password
              placeholder="至少 10 位，三类字符" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPassword" size="large" type="password" show-password
              placeholder="再次输入" />
          </el-form-item>
        </div>

        <el-form-item label="图形验证码">
          <div class="row">
            <el-input v-model="form.captchaCode" size="large" placeholder="输入右侧字符" @keyup.enter="submit" />
            <img v-if="captcha.image" :src="captcha.image" class="captcha" title="点击刷新" @click="loadCaptcha" />
            <el-button v-else size="large" text @click="loadCaptcha">加载</el-button>
          </div>
        </el-form-item>

        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
          注册并登录
        </el-button>
      </el-form>

      <div class="foot">
        已有账号？<router-link to="/login">返回登录</router-link>
        <span class="sep">·</span>
        <router-link to="/forgot">忘记密码</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, getProviders, sendOtp } from '../../api/auth'
import { useAuthStore } from '../../stores/authStore'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const providers = ref({})
const captcha = reactive({ captchaId: '', image: '' })
const debugCode = ref('')
const countdown = ref(0)
let timer = null

const form = reactive({
  username: '',
  displayName: '',
  email: '',
  phone: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
  captchaCode: ''
})

async function loadCaptcha() {
  try {
    const result = await getCaptcha()
    captcha.captchaId = result.data.captchaId
    captcha.image = result.data.image
  } catch {
    // 静默
  }
}

async function loadProviders() {
  try {
    const result = await getProviders()
    providers.value = result.data || {}
  } catch {
    providers.value = {}
  }
}

function startCountdown() {
  countdown.value = 60
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendCode() {
  if (!form.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  try {
    const result = await sendOtp({ scene: 'REGISTER', channel: 'EMAIL', target: form.email })
    debugCode.value = result.data.debugCode || ''
    startCountdown()
    ElMessage.success(result.data.message || '验证码已发送')
  } catch {
    // 拦截器提示
  }
}

async function submit() {
  if (!form.username || !form.email || !form.password) {
    ElMessage.warning('请填写用户名、邮箱和密码')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (providers.value.captcha !== false && !form.captchaCode) {
    ElMessage.warning('请输入图形验证码')
    return
  }
  loading.value = true
  try {
    await auth.register({ ...form, captchaId: captcha.captchaId })
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } catch (error) {
    if (error.code !== 40901) {
      form.captchaCode = ''
      loadCaptcha()
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCaptcha()
  loadProviders()
})

onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 42px 20px;
  background: radial-gradient(circle at 15% 10%, #12224a 0%, #070b14 48%, #0b1220 100%);
}

.register-card {
  width: min(560px, 100%);
  background: #fff;
  border-radius: 20px;
  padding: 32px 34px 26px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.45);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}

.mark {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #08111f;
  font-weight: 800;
  background: linear-gradient(135deg, #7dd3fc, #a78bfa);
}

.head h1 {
  font-size: 25px;
  margin: 18px 0 6px;
  color: #0f172a;
}

.head p {
  color: #64748b;
  font-size: 13px;
  margin-bottom: 18px;
}

.form :deep(.el-form-item) {
  margin-bottom: 15px;
}

.form :deep(.el-form-item__label) {
  font-size: 12px;
  color: #64748b;
  padding-bottom: 4px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha {
  width: 122px;
  height: 40px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
}

.submit {
  width: 100%;
  height: 44px;
  border: none;
  font-weight: 600;
  margin-top: 4px;
  background: linear-gradient(135deg, #2563eb, #6d28d9);
}

.debug {
  margin: -6px 0 12px;
  padding: 7px 10px;
  font-size: 12px;
  border-radius: 8px;
  color: #9a6700;
  background: #fff8c5;
  border: 1px solid #f0e6b2;
}

.foot {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
}

.foot a {
  color: #2563eb;
  text-decoration: none;
}

.sep {
  margin: 0 7px;
}

@media (max-width: 560px) {
  .grid-2 {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
