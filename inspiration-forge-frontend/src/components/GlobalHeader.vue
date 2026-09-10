<template>
  <header class="global-header">
    <div class="header-inner">
      <RouterLink class="brand" to="/" aria-label="灵感工坊首页">
        <img class="brand-logo" src="@/assets/logo-doraemon.jpg" alt="" />
        <span class="brand-copy">
          <strong>灵感工坊</strong>
          <small>让想法成为应用</small>
        </span>
      </RouterLink>

      <nav class="desktop-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navigationItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: route.path === item.path }"
        >
          <span>{{ item.label }}</span>
        </RouterLink>
        <a
          class="nav-link"
          href="https://github.com/1dashboard/inspiration-forge"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span>开源项目</span>
        </a>
      </nav>

      <div class="header-actions">
        <template v-if="loginUserStore.loginUser.id">
          <a-dropdown trigger="click" placement="bottomRight">
            <button class="profile-trigger" type="button">
              <a-avatar :src="loginUserStore.loginUser.userAvatar" :size="32">
                {{ userInitial }}
              </a-avatar>
              <span class="profile-name">{{ loginUserStore.loginUser.userName || '未命名用户' }}</span>
              <DownOutlined class="profile-chevron" />
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="logout" @click="doLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <a-button v-else type="primary" @click="router.push('/user/login')">登录</a-button>

        <a-button
          class="mobile-menu-button"
          type="text"
          aria-label="打开导航"
          @click="mobileMenuOpen = true"
        >
          <template #icon><MenuOutlined /></template>
        </a-button>
      </div>
    </div>

    <a-drawer v-model:open="mobileMenuOpen" title="导航" placement="right" :width="280">
      <nav class="mobile-nav" aria-label="移动端导航">
        <RouterLink
          v-for="item in navigationItems"
          :key="item.path"
          :to="item.path"
          class="mobile-nav-link"
          :class="{ active: route.path === item.path }"
          @click="mobileMenuOpen = false"
        >
          <component :is="item.icon" />
          {{ item.label }}
        </RouterLink>
        <a
          class="mobile-nav-link"
          href="https://github.com/1dashboard/inspiration-forge"
          target="_blank"
          rel="noopener noreferrer"
        >
          <CompassOutlined />
          开源项目
        </a>
      </nav>
    </a-drawer>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  CompassOutlined,
  DownOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { userLogout } from '@/api/userController'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const mobileMenuOpen = ref(false)

const navigationItems = computed(() => {
  const items = [{ path: '/', label: '首页', icon: HomeOutlined }]
  if (loginUserStore.loginUser.userRole === 'admin') {
    items.push(
      { path: '/admin/userManage', label: '用户管理', icon: TeamOutlined },
      { path: '/admin/appManage', label: '应用管理', icon: AppstoreOutlined },
    )
  }
  return items
})

const userInitial = computed(() => loginUserStore.loginUser.userName?.trim().charAt(0) || 'U')

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('已退出登录')
    await router.push('/user/login')
    return
  }
  message.error(`退出失败：${res.data.message || '请稍后重试'}`)
}
</script>

<style scoped>
.global-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 68px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid #e4ecea;
  backdrop-filter: blur(16px);
}

.header-inner {
  width: min(100% - 48px, 1200px);
  height: 100%;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 42px;
}

.brand {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #14211f;
  text-decoration: none;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: cover;
}

.brand-copy {
  display: flex;
  align-items: baseline;
  gap: 7px;
  line-height: 1;
}

.brand-copy strong {
  font-size: 18px;
  font-weight: 740;
  letter-spacing: 0;
}

.brand-copy small {
  color: #7d8d89;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0;
}

.desktop-nav {
  flex: 1;
  align-self: stretch;
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  position: relative;
  height: 100%;
  display: inline-flex;
  align-items: center;
  padding: 0 13px;
  color: #52635f;
  font-size: 14px;
  text-decoration: none;
  transition: color 0.2s ease, background 0.2s ease;
}

.nav-link:hover,
.nav-link.active {
  color: #176b5b;
  background: #f0f8f6;
}

.nav-link.active::after {
  content: '';
  position: absolute;
  right: 14px;
  bottom: 0;
  left: 14px;
  height: 2px;
  background: #2f8f7b;
}

.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-trigger {
  height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 0 10px 0 5px;
  color: #344b46;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
}

.profile-trigger:hover {
  background: #f4f8f7;
  border-color: #dfe9e6;
}

.profile-name {
  max-width: 110px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-chevron {
  color: #98a2b3;
  font-size: 10px;
}

.mobile-menu-button {
  display: none;
}

.mobile-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mobile-nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 12px;
  color: #344054;
  border-radius: 6px;
  text-decoration: none;
}

.mobile-nav-link:hover,
.mobile-nav-link.active {
  color: #176b5b;
  background: #edf7f4;
}

@media (max-width: 760px) {
  .global-header {
    height: 58px;
  }

  .header-inner {
    width: calc(100% - 24px);
  }

  .desktop-nav,
  .profile-name,
  .profile-chevron,
  .brand-copy small {
    display: none;
  }

  .brand-logo {
    width: 34px;
    height: 34px;
  }

  .brand-copy strong {
    font-size: 15px;
  }

  .mobile-menu-button {
    display: inline-flex;
  }
}
</style>
