<template>
  <div class="sso-page">
    <div class="sso-card">
      <div class="spinner" v-if="!error" />
      <div class="status" v-else>!</div>
      <h1>{{ error ? '统一身份认证失败' : '正在完成统一身份认证…' }}</h1>
      <p v-if="!error">正在校验授权码与 PKCE，并签发本地会话凭证。</p>
      <p v-else class="error">{{ error }}</p>
      <el-button v-if="error" type="primary" @click="$router.push('/login')">返回登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSsoConfig } from '../../api/auth'
import { useAuthStore } from '../../stores/authStore'

const router = useRouter()
const auth = useAuthStore()
const error = ref('')

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const state = params.get('state')
  const expectedState = sessionStorage.getItem('sso_state')
  const verifier = sessionStorage.getItem('sso_pkce_verifier')

  if (!code) {
    error.value = '回调缺少授权码 code'
    return
  }
  if (!state || !expectedState || state !== expectedState) {
    error.value = 'state 校验失败，可能存在 CSRF 风险，请重新登录'
    return
  }
  if (!verifier) {
    error.value = '缺少 PKCE code_verifier，请重新发起登录'
    return
  }

  try {
    const config = await getSsoConfig()
    const clientId = config.data?.clientId || 'learning-web'
    const redirectUri = `${window.location.origin}/sso/callback`
    await auth.exchangeSso({ code, clientId, redirectUri, codeVerifier: verifier })
    sessionStorage.removeItem('sso_state')
    sessionStorage.removeItem('sso_pkce_verifier')
    router.replace('/')
  } catch (e) {
    error.value = e.message || '授权码交换失败'
  }
})
</script>

<style scoped>
.sso-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: radial-gradient(circle at 20% 20%, #12224a 0%, #070b14 50%, #0b1220 100%);
}

.sso-card {
  width: min(430px, calc(100% - 40px));
  background: #fff;
  border-radius: 20px;
  padding: 44px 36px;
  text-align: center;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.45);
}

.spinner {
  width: 42px;
  height: 42px;
  margin: 0 auto 18px;
  border-radius: 50%;
  border: 3px solid #e2e8f0;
  border-top-color: #2563eb;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.status {
  width: 44px;
  height: 44px;
  margin: 0 auto 18px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 24px;
  font-weight: 800;
  color: #b91c1c;
  background: #fee2e2;
}

h1 {
  font-size: 20px;
  color: #0f172a;
  margin-bottom: 8px;
}

p {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

p.error {
  color: #b91c1c;
  margin-bottom: 18px;
}
</style>
