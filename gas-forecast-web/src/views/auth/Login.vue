<template>
  <main class="login-page">
    <div class="mobile-bg" aria-hidden="true">
      <img :src="mobileBgUrl" alt="" />
    </div>
    <section class="login-shell">
      <div class="brand-area">
        <img :src="logoUrl" alt="" />
        <div>
          <h1>天然气预测平台</h1>
          <p>GAS FORECAST PLATFORM</p>
        </div>
      </div>

      <div class="illustration">
        <img :src="loginHomeUrl" alt="" />
      </div>

      <div class="mobile-illustration">
        <img :src="mobileHomeUrl" alt="" />
      </div>

      <section class="login-panel">
        <div class="panel-title">
          <span>用户登录</span>
          <small>统一身份认证</small>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="验证码" prop="captchaCode">
            <div class="captcha-row">
              <el-input
                v-model="form.captchaCode"
                size="large"
                maxlength="8"
                autocomplete="off"
                @keyup.enter="submit"
              />
              <button class="captcha-image" type="button" @click="loadCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="" />
                <span v-else>刷新</span>
              </button>
            </div>
          </el-form-item>
          <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
          <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { setProfile, setToken } from '@/utils/auth'
import { encryptWithRsaPublicKey } from '@/utils/rsa'
import logoUrl from '@/assets/logo.png'
import loginHomeUrl from '@/assets/login-home.png'
import mobileBgUrl from '@/assets/login-mobile-bg.png'
import mobileHomeUrl from '@/assets/login-mobile-home.png'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMessage = ref('')
const captchaImage = ref('')
const rsaPublicKey = ref('')
const form = reactive({
  username: 'admin',
  password: 'admin123',
  captchaId: '',
  captchaCode: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const loadRsaPublicKey = async () => {
  try {
    const response = await fetch('/auth/rsa-public-key')
    const result = await response.json()
    if (response.ok && result.code === '0000') {
      rsaPublicKey.value = result.data.publicKey
    }
  } catch {
    rsaPublicKey.value = ''
  }
}

const loadCaptcha = async () => {
  try {
    const response = await fetch('/auth/captcha')
    const result = await response.json()
    if (response.ok && result.code === '0000') {
      form.captchaId = result.data.captchaId
      form.captchaCode = ''
      captchaImage.value = result.data.image
    }
  } catch {
    captchaImage.value = ''
  }
}

const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    if (!rsaPublicKey.value) {
      await loadRsaPublicKey()
    }
    let loginPayload: Record<string, string> = { ...form }
    if (rsaPublicKey.value) {
      try {
        loginPayload = {
          ...form,
          username: await encryptWithRsaPublicKey(form.username, rsaPublicKey.value),
          password: await encryptWithRsaPublicKey(form.password, rsaPublicKey.value),
          rsaPublicKey: rsaPublicKey.value
        }
      } catch {
        errorMessage.value = '登录参数加密失败，请刷新页面后重试'
        await loadRsaPublicKey()
        return
      }
    }
    const response = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(loginPayload)
    })
    const result = await response.json()
    if (!response.ok || result.code !== '0000') {
      errorMessage.value = result.message || '登录失败'
      await loadCaptcha()
      await loadRsaPublicKey()
      return
    }
    setToken(result.data.token)
    setProfile({
      user: result.data.user,
      roles: result.data.roles,
      permissions: result.data.permissions,
      menus: result.data.menus
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch {
    errorMessage.value = '登录服务不可用'
    await loadCaptcha()
    await loadRsaPublicKey()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCaptcha()
  loadRsaPublicKey()
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background: #4DAAFF;
}

.login-shell {
  position: relative;
  width: 100%;
  min-height: 100vh;
  padding: 42px clamp(32px, 5vw, 76px);
}

.mobile-bg,
.mobile-illustration {
  display: none;
}

.illustration {
  position: absolute;
  inset: 120px 46% 0 4%;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.illustration img {
  width: min(760px, 100%);
  max-height: 72vh;
  object-fit: contain;
}

.login-panel {
  position: absolute;
  top: 50%;
  right: clamp(52px, 10vw, 170px);
  width: min(390px, calc(100vw - 48px));
  padding: 32px 34px 34px;
  border-radius: 6px;
  background: #FFFFFF;
  box-shadow: 0 22px 58px rgba(17, 78, 138, 0.22);
  transform: translateY(-46%);
}

.brand-area {
  display: flex;
  align-items: center;
  gap: 14px;
  position: relative;
  z-index: 2;
}

.brand-area img {
  width: 58px;
  height: 58px;
  object-fit: contain;
}

.brand-area h1 {
  margin: 0;
  color: #111827;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 3px;
}

.brand-area p {
  margin: 5px 0 0;
  color: #1F2937;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 1px;
}

.panel-title {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 24px;
}

.panel-title span {
  color: #172033;
  font-size: 22px;
  font-weight: 700;
}

.panel-title small {
  color: #94A3B8;
  font-size: 13px;
}

.login-panel :deep(.el-form-item__label) {
  color: #475569;
  font-weight: 600;
}

.login-panel :deep(.el-input__wrapper) {
  border-radius: 4px;
  box-shadow: 0 0 0 1px #DDE5EF inset;
}

.login-button {
  width: 100%;
  margin-top: 18px;
  border: none;
  border-radius: 4px;
  background: #2196F3;
  font-weight: 700;
}

.login-button:hover,
.login-button:focus {
  background: #1687E0;
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr 128px;
  gap: 10px;
  width: 100%;
}

.captcha-image {
  height: 40px;
  overflow: hidden;
  border: 1px solid #DCDFE6;
  border-radius: 4px;
  background: #F6F8FB;
  color: #64748B;
  cursor: pointer;
}

.captcha-image img {
  width: 100%;
  height: 100%;
  display: block;
}

@media (max-width: 900px) {
  .login-page {
    background: #F0F2F5;
  }

  .mobile-bg,
  .mobile-illustration {
    display: block;
    position: absolute;
    pointer-events: none;
  }

  .mobile-bg {
    top: -18px;
    left: 0;
    width: 100%;
  }

  .mobile-bg img {
    width: 100%;
    display: block;
  }

  .mobile-illustration {
    left: -68px;
    bottom: -48px;
    width: 78%;
    opacity: 0.45;
  }

  .mobile-illustration img {
    width: 100%;
    display: block;
  }

  .login-shell {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 86px 24px 32px;
  }

  .brand-area {
    flex-direction: column;
    align-items: center;
    gap: 8px;
    margin-bottom: 28px;
    text-align: center;
  }

  .brand-area img {
    width: 64px;
    height: 64px;
  }

  .brand-area h1 {
    font-size: 24px;
  }

  .brand-area p {
    font-size: 12px;
  }

  .illustration {
    display: none;
  }

  .login-panel {
    position: relative;
    top: auto;
    right: auto;
    z-index: 1;
    width: min(390px, 100%);
    transform: none;
  }
}
</style>
