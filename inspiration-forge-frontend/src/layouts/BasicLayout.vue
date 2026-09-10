<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader v-if="!isLoginRoute" />
    <!-- 主要内容区域 -->
    <a-layout-content
      class="main-content"
      :class="{ 'workspace-content': isWorkspaceRoute, 'login-content': isLoginRoute }"
    >
      <router-view />
    </a-layout-content>
    <!-- 底部版权信息 -->
    <GlobalFooter v-if="!isWorkspaceRoute && !isLoginRoute" />
  </a-layout>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isWorkspaceRoute = computed(() => route.path.startsWith('/app/'))
const isLoginRoute = computed(() => route.path === '/user/login')
</script>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: #eef5f4;
}

.main-content {
  width: 100%;
  min-height: calc(100vh - 68px);
  padding: 0;
  background: #eef5f4;
  margin: 0;
}

.workspace-content {
  min-height: calc(100vh - 68px);
  background: #f2f4f7;
}

.login-content {
  min-height: 100vh;
  background: #ffffff;
}

@media (max-width: 760px) {
  .main-content,
  .workspace-content,
  .login-content {
    min-height: calc(100vh - 58px);
  }
}
</style>
