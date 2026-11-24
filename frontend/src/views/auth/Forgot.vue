<template>
  <div class="forgot-page">
    <div class="forgot-card">
      <div class="brand"><span class="mark">AI</span> 账号安全</div>

      <template v-if="step === 1">
        <h1>找回密码</h1>
        <p class="sub">输入账号，我们会向绑定的邮箱或手机号发送一次性验证码。</p>
        <el-alert type="info" :closable="false" show-icon class="alert"
          title="为保护账号安全，无论账号是否存在，提示信息完全一致。" />

        <el-form label-position="top" class="form" @submit.prevent="requestCode">
          <el-form-item label="账号">
            <el-input v-model="form.account" size="large" placeholder="用户名 / 邮箱 / 手机号" />
          </el-form-item>
          <el-form-item label="图形验证码">
            <div class="row">
              <el-input v-model="form.captchaCode" size="large" placeholder="输入右侧字符" @keyup.enter="requestCode" />
              <img v-if="captcha.image" :src="captcha.image" class="captcha" title="点击刷新" @click="loadCaptcha" />
              <el-button v-else size="large" text @click="loadCaptcha">加载</el-button>
            </div>
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="requestCode">
            发送验证码
          </el-button>
        </el-form>
      </template>

      <template v-else>
        <h1>设置新密码</h1>
        <el-alert type="success" :closable="false" show-icon class="alert" :title="notice" />
        <div v-if="debugCode" class="debug">开发模式验证码：<b>{{ debugCode }}</b></div>

        <el-form label-position="top" class="form" @submit.prevent="reset">
          <el-form-item label="验证码">
            <el-input v-model="form.code" size="large" maxlength="6" placeholder="6 位验证码" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="form.newPassword" size="large" type="password" show-password
              placeholder="至少 10 位，包含三类字符" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="form.confirmPassword" size="large" type="password" show-password
              placeholder="再次输入新密码" />
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="reset">
            重置密码
          </el-button>
        </el-form>

        <div class="resend">
          <el-button text @click="backToRequest">重新获取验证码</el-button>
        </div>
      </template>

      <div class="foot">
        <router-link to="/login">返回登录</router-link>
        <span class="sep">·</span>
        <router-link to="/register">注册账号</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { forgotPassword, getCaptcha, resetPassword } from '../../api/auth'

const router = useRouter()
const step = ref(1)
const loading = ref(false)
const notice = ref('')
const debugCode = ref('')
const captcha = reactive({ captchaId: '', image: '' })

const form = reactive({
  account: '',
  captchaCode: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
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

async function requestCode() {
  if (!form.account) {
    ElMessage.warning('请输入账号')
    return
  }
  if (!form.captchaCode) {
    ElMessage.warning('请输入图形验证码')
    return
  }
  loading.value = true
  try {
    const result = await forgotPassword({
      account: form.account,
      captchaId: captcha.captchaId,
      captchaCode: form.captchaCode
    })
    notice.value = result.data.message
    debugCode.value = result.data.debugCode || ''
    step.value = 2
    ElMessage.success('如果账号存在，验证码已发送')
  } catch (error) {
    ElMessage.error(error.message || '请求失败，请稍后再试')
    form.captchaCode = ''
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

async function reset() {
  if (!form.code || !form.newPassword) {
    ElMessage.warning('请输入验证码和新密码')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await resetPassword({
      account: form.account,
      code: form.code,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}

function backToRequest() {
  step.value = 1
  form.captchaCode = ''
  loadCaptcha()
}

onMounted(loadCaptcha)
</script>

<style scoped>
.forgot-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 40px 20px;
  background: radial-gradient(circle at 80% 15%, #1d2b56 0%, #070b14 50%, #0b1220 100%);
}

.forgot-card {
  width: min(480px, 100%);
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

h1 {
  font-size: 25px;
  margin: 18px 0 6px;
  color: #0f172a;
}

.sub {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.alert {
  margin: 14px 0 18px;
}

.form :deep(.el-form-item) {
  margin-bottom: 15px;
}

.form :deep(.el-form-item__label) {
  font-size: 12px;
  color: #64748b;
  padding-bottom: 4px;
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
  margin-bottom: 12px;
  padding: 8px 10px;
  font-size: 12px;
  border-radius: 8px;
  color: #9a6700;
  background: #fff8c5;
  border: 1px solid #f0e6b2;
}

.resend {
  text-align: center;
  margin-top: 8px;
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
</style>
