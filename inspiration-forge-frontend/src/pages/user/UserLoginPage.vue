<template>
  <main id="userLoginPage">
    <section class="brand-panel" aria-label="灵感工坊 · 让想法成为应用">
      <div class="brand-content">
        <div class="brand-mark">
          <img :src="logo" alt="" />
          <span>灵感工坊</span>
        </div>

        <div class="brand-message">
          <p class="eyebrow">AI 应用生成平台</p>
          <h1>让每一个想法，<br />都有成为作品的可能</h1>
          <p>从需求描述到代码与预览，在一个工作区持续完成你的应用。</p>
        </div>

        <div class="workspace-visual" aria-hidden="true">
          <div class="visual-toolbar">
            <span></span><span></span><span></span>
            <div class="visual-address"></div>
          </div>
          <div class="visual-body">
            <div class="visual-sidebar">
              <div class="visual-line short"></div>
              <div class="visual-line active"></div>
              <div class="visual-line"></div>
              <div class="visual-line medium"></div>
            </div>
            <div class="visual-preview">
              <div class="visual-nav"></div>
              <div class="visual-title"></div>
              <div class="visual-copy"></div>
              <div class="visual-copy short"></div>
              <div class="visual-button"></div>
              <div class="visual-grid"><i></i><i></i><i></i></div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-container">
        <div class="login-heading">
          <p class="welcome">欢迎回来</p>
          <h2>登录你的账号</h2>
          <p>继续创建和管理你的应用</p>
        </div>

        <a-form
          :model="formState"
          name="login"
          layout="vertical"
          autocomplete="on"
          class="login-form"
          @finish="handleSubmit"
        >
          <a-form-item
            label="账号"
            name="userAccount"
            :rules="[{ required: true, message: '请输入账号' }]"
          >
            <a-input
              v-model:value="formState.userAccount"
              size="large"
              placeholder="请输入账号"
              autocomplete="username"
              :disabled="submitting"
            >
              <template #prefix><UserOutlined /></template>
            </a-input>
          </a-form-item>

          <a-form-item
            label="密码"
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码长度不能小于 8 位' },
            ]"
          >
            <a-input-password
              v-model:value="formState.userPassword"
              size="large"
              placeholder="请输入密码"
              autocomplete="current-password"
              :disabled="submitting"
            >
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>

          <a-button
            class="login-button"
            type="primary"
            size="large"
            html-type="submit"
            :loading="submitting"
          >
            登录
            <ArrowRightOutlined v-if="!submitting" />
          </a-button>
        </a-form>

        <p class="register-tip">
          还没有账号？
          <RouterLink to="/user/register">免费注册</RouterLink>
        </p>

        <RouterLink class="home-link" to="/">
          <ArrowLeftOutlined />
          返回首页
        </RouterLink>
      </div>
    </section>
  </main>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  LockOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import logo from '@/assets/logo-doraemon.jpg'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const submitting = ref(false)

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const handleSubmit = async (values: API.UserLoginRequest) => {
  submitting.value = true
  try {
    const res = await userLogin(values)
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser()
      message.success('登录成功')
      await router.replace('/')
      return
    }
    message.error(`登录失败：${res.data.message || '账号或密码错误'}`)
  } catch (error) {
    console.error('登录失败', error)
    message.error('暂时无法登录，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
#userLoginPage {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(440px, 1.05fr) minmax(460px, 0.95fr);
  background: #ffffff;
}

.brand-panel {
  min-height: inherit;
  overflow: hidden;
  background: #e6f6f2;
  border-right: 1px solid #dcebe7;
}

.brand-content {
  width: min(100% - 72px, 620px);
  min-height: inherit;
  margin: 0 auto;
  padding: 54px 0 50px;
  display: flex;
  flex-direction: column;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  color: #14211f;
  font-size: 18px;
  font-weight: 740;
}

.brand-mark img {
  width: 38px;
  height: 38px;
  border-radius: 8px;
}

.brand-message {
  margin-top: 64px;
}

.eyebrow,
.welcome {
  margin: 0 0 14px;
  color: #247564;
  font-size: 13px;
  font-weight: 680;
}

.brand-message h1 {
  margin: 0;
  color: #10211d;
  font-size: 38px;
  font-weight: 760;
  letter-spacing: 0;
  line-height: 1.35;
}

.brand-message > p:last-child {
  max-width: 500px;
  margin: 18px 0 0;
  color: #5c736d;
  font-size: 15px;
  line-height: 1.8;
}

.workspace-visual {
  width: min(100%, 560px);
  margin-top: auto;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #bfd8d2;
  border-radius: 8px;
  box-shadow: 0 24px 56px rgba(25, 77, 66, 0.13);
}

.visual-toolbar {
  height: 36px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f8fbfa;
  border-bottom: 1px solid #e5eeeb;
}

.visual-toolbar > span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c8d8d4;
}

.visual-toolbar > span:first-child {
  background: #61bba7;
}

.visual-address {
  width: 42%;
  height: 8px;
  margin-left: 10px;
  background: #e8efed;
  border-radius: 4px;
}

.visual-body {
  height: 224px;
  display: grid;
  grid-template-columns: 31% 1fr;
}

.visual-sidebar {
  padding: 25px 18px;
  background: #162622;
}

.visual-line {
  width: 78%;
  height: 7px;
  margin-bottom: 17px;
  background: #49615b;
  border-radius: 4px;
}

.visual-line.short {
  width: 42%;
  background: #85b9ad;
}

.visual-line.medium {
  width: 62%;
}

.visual-line.active {
  width: 100%;
  height: 28px;
  margin-left: -8px;
  background: #285e52;
}

.visual-preview {
  padding: 25px 28px;
  background: #fbfdfc;
}

.visual-nav {
  width: 100%;
  height: 7px;
  margin-bottom: 30px;
  background: #e1eae7;
  border-radius: 4px;
}

.visual-title {
  width: 62%;
  height: 17px;
  margin-bottom: 15px;
  background: #1f3a34;
  border-radius: 4px;
}

.visual-copy {
  width: 78%;
  height: 7px;
  margin-bottom: 9px;
  background: #c9d8d4;
  border-radius: 4px;
}

.visual-copy.short {
  width: 48%;
}

.visual-button {
  width: 74px;
  height: 24px;
  margin-top: 18px;
  background: #26816d;
  border-radius: 5px;
}

.visual-grid {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
}

.visual-grid i {
  height: 40px;
  background: #dbe9e5;
  border-radius: 5px;
}

.login-panel {
  min-height: inherit;
  padding: 64px 56px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-container {
  width: min(100%, 420px);
}

.login-heading {
  margin-bottom: 34px;
}

.login-heading h2 {
  margin: 0;
  color: #14211f;
  font-size: 31px;
  font-weight: 740;
  letter-spacing: 0;
  line-height: 1.25;
}

.login-heading > p:last-child {
  margin: 10px 0 0;
  color: #778782;
  font-size: 14px;
}

.login-form :deep(.ant-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.ant-form-item-label) {
  padding-bottom: 8px;
}

.login-form :deep(.ant-form-item-label > label) {
  height: auto;
  color: #344b45;
  font-size: 14px;
  font-weight: 620;
}

.login-form :deep(.ant-input-affix-wrapper) {
  min-height: 48px;
  padding: 0 14px;
  border-color: #d4dfdc;
  border-radius: 7px;
  box-shadow: none;
}

.login-form :deep(.ant-input-affix-wrapper:hover) {
  border-color: #78ad9f;
}

.login-form :deep(.ant-input-affix-wrapper-focused) {
  border-color: #378c78;
  box-shadow: 0 0 0 3px #e1f2ee;
}

.login-form :deep(.ant-input-prefix) {
  margin-right: 10px;
  color: #8a9a96;
}

.login-form :deep(.ant-input) {
  font-size: 15px;
}

.login-button {
  width: 100%;
  height: 48px;
  margin-top: 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-radius: 7px;
  font-size: 15px;
  font-weight: 650;
  box-shadow: none;
}

.register-tip {
  margin: 25px 0 0;
  color: #758580;
  font-size: 14px;
  text-align: center;
}

.register-tip a {
  color: #176b5b;
  font-weight: 650;
}

.home-link {
  width: max-content;
  margin: 34px auto 0;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #7b8b87;
  font-size: 13px;
}

.home-link:hover {
  color: #176b5b;
}

@media (max-width: 980px) {
  #userLoginPage {
    grid-template-columns: minmax(320px, 0.85fr) minmax(430px, 1.15fr);
  }

  .brand-content {
    width: calc(100% - 48px);
  }

  .brand-message h1 {
    font-size: 31px;
  }

  .workspace-visual {
    display: none;
  }
}

@media (max-width: 760px) {
  #userLoginPage {
    min-height: 100vh;
    display: block;
    background: #f4faf8;
  }

  .brand-panel {
    min-height: auto;
    background: transparent;
    border-right: 0;
  }

  .brand-content {
    width: calc(100% - 40px);
    min-height: auto;
    padding: 28px 0 0;
    align-items: center;
  }

  .brand-mark {
    font-size: 16px;
  }

  .brand-mark img {
    width: 34px;
    height: 34px;
  }

  .brand-message {
    margin-top: 32px;
    text-align: center;
  }

  .brand-message h1 {
    font-size: 27px;
  }

  .brand-message > p:last-child {
    display: none;
  }

  .login-panel {
    min-height: auto;
    padding: 31px 20px 50px;
  }

  .login-container {
    padding: 28px 22px 24px;
    background: #ffffff;
    border: 1px solid #dfebe8;
    border-radius: 8px;
    box-shadow: 0 16px 40px rgba(25, 77, 66, 0.08);
  }

  .login-heading {
    margin-bottom: 28px;
    text-align: center;
  }

  .login-heading h2 {
    font-size: 26px;
  }
}

@media (max-width: 390px) {
  .brand-message h1 {
    font-size: 24px;
  }

  .login-panel {
    padding-right: 14px;
    padding-left: 14px;
  }

  .login-container {
    padding-right: 17px;
    padding-left: 17px;
  }
}
</style>
