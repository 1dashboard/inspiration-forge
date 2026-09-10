<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  BankOutlined,
  BulbOutlined,
  PictureOutlined,
  ReadOutlined,
  SendOutlined,
  ShopOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  addApp,
  batchDeleteApps,
  deleteApp,
  getAppVoById,
  listGoodAppVoByPage,
  listMyAppVoByPage,
} from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'
import { createCoverPoller } from '@/utils/coverPolling'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const promptInput = ref<any>()
const userPrompt = ref('')
const ragEnabled = ref(false)
const creating = ref(false)
const loadingMyApps = ref(false)
const loadingFeaturedApps = ref(false)
const selectionMode = ref(false)
const selectedAppIds = ref<Set<string>>(new Set())
const deletingAppIds = ref<Set<string>>(new Set())

const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({ current: 1, pageSize: 6, total: 0 })
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({ current: 1, pageSize: 6, total: 0 })

const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser.id))
const promptLength = computed(() => userPrompt.value.length)
const selectedCount = computed(() => selectedAppIds.value.size)
const currentPageIds = computed(() => myApps.value
  .map((app) => app.id == null ? '' : String(app.id))
  .filter(Boolean))
const allCurrentSelected = computed(() => {
  return currentPageIds.value.length > 0
    && currentPageIds.value.every((id) => selectedAppIds.value.has(id))
})
const currentSelectionIndeterminate = computed(() => {
  const selectedOnPage = currentPageIds.value.filter((id) => selectedAppIds.value.has(id)).length
  return selectedOnPage > 0 && selectedOnPage < currentPageIds.value.length
})

const promptTemplates = [
  {
    label: '个人作品集',
    icon: ReadOutlined,
    prompt:
      '制作一个极简个人作品集网站，包含项目画廊、项目详情、个人介绍和联系入口。突出作品图片，排版有呼吸感，适配手机和桌面端。',
  },
  {
    label: '品牌官网',
    icon: BankOutlined,
    prompt:
      '设计一个现代品牌官网，包含品牌主张、核心产品、客户案例、团队介绍和联系入口。视觉简洁有质感，适配手机和桌面端。',
  },
  {
    label: '电商店铺',
    icon: ShopOutlined,
    prompt:
      '构建一个在线商城，包含商品分类、搜索筛选、商品详情、购物车和订单确认界面。商品信息清晰，交互完整，适配手机和桌面端。',
  },
  {
    label: '活动落地页',
    icon: PictureOutlined,
    prompt:
      '创建一个产品发布活动落地页，包含活动主题、核心亮点、嘉宾阵容、日程安排和报名入口。视觉鲜明，信息层级清楚，适配手机和桌面端。',
  },
]

const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
  promptInput.value?.focus?.()
}

const focusComposer = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
  setTimeout(() => promptInput.value?.focus?.(), 250)
}

const handlePromptKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    void createApp()
  }
}

const createApp = async () => {
  const prompt = userPrompt.value.trim()
  if (!prompt) {
    message.warning('请输入应用描述')
    promptInput.value?.focus?.()
    return
  }
  if (!isLoggedIn.value) {
    message.info('登录后即可创建应用')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({ initPrompt: prompt, ragEnabled: ragEnabled.value })
    if (res.data.code === 0 && res.data.data) {
      await router.push(`/app/chat/${String(res.data.data)}`)
      return
    }
    message.error(`创建失败：${res.data.message || '请稍后重试'}`)
  } catch (error) {
    console.error('创建应用失败', error)
    message.error('创建失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

const loadMyApps = async () => {
  if (!isLoggedIn.value) {
    myApps.value = []
    myAppsPage.total = 0
    return
  }
  loadingMyApps.value = true
  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的项目失败', error)
  } finally {
    loadingMyApps.value = false
  }
}

const loadFeaturedApps = async () => {
  loadingFeaturedApps.value = true
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败', error)
  } finally {
    loadingFeaturedApps.value = false
  }
}

const coverPoller = createCoverPoller({
  getApps: () => [...myApps.value, ...featuredApps.value],
  isVisible: () => document.visibilityState !== 'hidden',
  fetchApp: async (id, signal) => {
    const res = await getAppVoById({ id }, { signal, timeout: 10000 })
    return res.data.code === 0 ? res.data.data : undefined
  },
})

// Pagination/deletion can introduce new pending covers. Only watch the fields
// relevant to polling; the poller mutates cover in place and never toggles loading.
watch(
  () => [...myApps.value, ...featuredApps.value].map((app) => [app.id, app.deployKey, app.cover]),
  () => coverPoller.sync(),
)

const viewChat = (appId: string | number | undefined) => {
  if (appId) void router.push(`/app/chat/${appId}?view=1`)
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) window.open(getDeployUrl(app.deployKey), '_blank')
}

const setSelectionMode = (enabled: boolean) => {
  selectionMode.value = enabled
  if (!enabled) selectedAppIds.value = new Set()
}

const toggleAppSelected = (app: API.AppVO, checked: boolean) => {
  if (app.id == null) return
  const next = new Set(selectedAppIds.value)
  const id = String(app.id)
  if (checked) next.add(id)
  else next.delete(id)
  selectedAppIds.value = next
}

const toggleCurrentPage = (event: { target?: { checked?: boolean } }) => {
  const next = new Set(selectedAppIds.value)
  currentPageIds.value.forEach((id) => {
    if (event.target?.checked) next.add(id)
    else next.delete(id)
  })
  selectedAppIds.value = next
}

const refreshAfterDelete = async (deletedCount: number) => {
  const remainingTotal = Math.max(0, myAppsPage.total - deletedCount)
  const lastPage = Math.max(1, Math.ceil(remainingTotal / myAppsPage.pageSize))
  myAppsPage.current = Math.min(myAppsPage.current, lastPage)
  await Promise.all([loadMyApps(), loadFeaturedApps()])
}

const markDeleting = (ids: string[], deleting: boolean) => {
  const next = new Set(deletingAppIds.value)
  ids.forEach((id) => deleting ? next.add(id) : next.delete(id))
  deletingAppIds.value = next
}

const deleteOneApp = async (app: API.AppVO) => {
  if (app.id == null) return
  const id = String(app.id)
  markDeleting([id], true)
  try {
    const res = await deleteApp({ id: app.id })
    if (res.data.code !== 0) throw new Error(res.data.message || '删除失败')
    const next = new Set(selectedAppIds.value)
    next.delete(id)
    selectedAppIds.value = next
    await refreshAfterDelete(1)
    message.success('应用及关联数据已删除')
  } catch (error: any) {
    message.error(error?.message || '删除失败')
  } finally {
    markDeleting([id], false)
  }
}

const confirmBatchDelete = () => {
  const ids = [...selectedAppIds.value]
  if (!ids.length) return
  Modal.confirm({
    title: `删除选中的 ${ids.length} 个应用？`,
    content: '应用代码、版本、对话记录、部署文件和封面将一并删除，且无法恢复。',
    okText: '全部删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      markDeleting(ids, true)
      try {
        const res = await batchDeleteApps({ ids })
        if (res.data.code !== 0) throw new Error(res.data.message || '批量删除失败')
        const deletedCount = res.data.data || ids.length
        selectedAppIds.value = new Set()
        selectionMode.value = false
        await refreshAfterDelete(deletedCount)
        message.success(`已删除 ${deletedCount} 个应用及关联数据`)
      } catch (error: any) {
        message.error(error?.message || '批量删除失败')
        throw error
      } finally {
        markDeleting(ids, false)
      }
    },
  })
}

onMounted(() => {
  document.addEventListener('visibilitychange', coverPoller.sync)
  void Promise.all([loadMyApps(), loadFeaturedApps()])
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', coverPoller.sync)
  coverPoller.dispose()
})
</script>

<template>
  <div id="homePage">
    <section class="generator-band">
      <div class="page-container generator-inner">
        <div class="hero-copy">
          <p class="product-kicker"><BulbOutlined /> 从一个想法开始</p>
          <h1>一句话，生成你的网页应用</h1>
          <p class="hero-description">描述你想做的网站，AI 为你完成设计、代码与实时预览</p>
        </div>

        <div class="composer">
          <a-textarea
            ref="promptInput"
            v-model:value="userPrompt"
            class="prompt-input"
            placeholder="例如：做一个极简摄影作品集，使用深色背景，包含项目展示、关于我和预约联系……"
            :rows="5"
            :maxlength="1000"
            @keydown="handlePromptKeydown"
          />
          <div class="composer-footer">
            <div class="composer-preferences">
              <a-tooltip title="开启后，AI 会检索这个项目的历史需求与代码，为后续生成补充相关上下文">
                <span class="memory-option-label"><ReadOutlined /> RAG</span>
              </a-tooltip>
              <a-switch v-model:checked="ragEnabled" size="small" />
              <span class="memory-option-state">{{ ragEnabled ? '已开启' : '未开启' }}</span>
            </div>
            <div class="composer-actions">
              <span class="prompt-count">{{ promptLength }}/1000</span>
              <a-button class="create-button" type="primary" size="large" :loading="creating" @click="createApp">
                <template #icon><SendOutlined /></template>
                开始生成
              </a-button>
            </div>
          </div>
        </div>

        <div class="template-area">
          <span>试试这些灵感</span>
          <div class="template-row" aria-label="应用模板">
            <button
              v-for="template in promptTemplates"
              :key="template.label"
              type="button"
              class="template-button"
              @click="setPrompt(template.prompt)"
            >
              <component :is="template.icon" />
              {{ template.label }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <main class="page-container content-area">
      <section class="project-section">
        <div class="section-header">
          <div>
            <h2>我的作品</h2>
            <p v-if="isLoggedIn">继续创作，或打开已经完成的应用</p>
            <p v-else>登录后查看、编辑和管理你的作品</p>
          </div>
          <div v-if="isLoggedIn && myAppsPage.total" class="project-controls">
            <template v-if="selectionMode">
              <a-checkbox
                :checked="allCurrentSelected"
                :indeterminate="currentSelectionIndeterminate"
                @change="toggleCurrentPage"
              >
                全选当前页
              </a-checkbox>
              <span class="selected-count">已选 {{ selectedCount }} 项</span>
              <a-button type="text" @click="setSelectionMode(false)">取消</a-button>
              <a-button type="primary" danger :disabled="!selectedCount" @click="confirmBatchDelete">
                <template #icon><DeleteOutlined /></template>
                批量删除
              </a-button>
            </template>
            <template v-else>
              <span class="section-count">{{ myAppsPage.total }} 个项目</span>
              <a-button type="text" @click="setSelectionMode(true)">
                <template #icon><DeleteOutlined /></template>
                批量管理
              </a-button>
            </template>
          </div>
        </div>

        <div v-if="loadingMyApps" class="app-grid">
          <div v-for="index in 3" :key="index" class="app-skeleton">
            <a-skeleton active :paragraph="{ rows: 3 }" />
          </div>
        </div>
        <div v-else-if="myApps.length" class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            :selectable="selectionMode"
            :selected="app.id != null && selectedAppIds.has(String(app.id))"
            deletable
            :deleting="app.id != null && deletingAppIds.has(String(app.id))"
            @view-chat="viewChat"
            @view-work="viewWork"
            @toggle-selected="toggleAppSelected"
            @delete="deleteOneApp"
          />
        </div>
        <div v-else class="empty-state">
          <AppstoreOutlined />
          <h3>{{ isLoggedIn ? '还没有项目' : '登录后管理项目' }}</h3>
          <p>{{ isLoggedIn ? '创建第一个应用后，它会出现在这里。' : '项目、版本和部署记录会统一保存在账号中。' }}</p>
          <a-button v-if="isLoggedIn" type="primary" @click="focusComposer">创建应用</a-button>
          <a-button v-else type="primary" @click="router.push('/user/login')">去登录</a-button>
        </div>

        <div v-if="myAppsPage.total > myAppsPage.pageSize" class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            @change="loadMyApps"
          />
        </div>
      </section>

      <section class="project-section featured-section">
        <div class="section-header">
          <div>
            <h2>灵感广场</h2>
            <p>看看大家正在创造什么</p>
          </div>
          <ArrowRightOutlined class="section-arrow" />
        </div>

        <div v-if="loadingFeaturedApps" class="app-grid">
          <div v-for="index in 3" :key="index" class="app-skeleton">
            <a-skeleton active :paragraph="{ rows: 3 }" />
          </div>
        </div>
        <div v-else-if="featuredApps.length" class="app-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            featured
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div v-else class="empty-state compact">
          <PictureOutlined />
          <h3>暂无精选应用</h3>
        </div>

        <div v-if="featuredAppsPage.total > featuredAppsPage.pageSize" class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            @change="loadFeaturedApps"
          />
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
#homePage {
  min-height: 100vh;
  color: #14211f;
  background: #eef5f4;
}

.page-container {
  width: min(100% - 48px, 1200px);
  margin: 0 auto;
}

.generator-band {
  position: relative;
  min-height: 520px;
  overflow: hidden;
  background: #e9f8f4;
  border-bottom: 1px solid #dcebe7;
}

.generator-inner {
  position: relative;
  z-index: 1;
  padding-top: 64px;
  padding-bottom: 44px;
}

.hero-copy {
  max-width: 820px;
  margin: 0 auto 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.product-kicker {
  margin: 0 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #176b5b;
  font-size: 13px;
  font-weight: 650;
}

.hero-copy h1 {
  margin: 0;
  color: #101d1b;
  font-size: 44px;
  font-weight: 760;
  letter-spacing: 0;
  line-height: 1.2;
}

.hero-description {
  margin: 13px 0 0;
  color: #5b6f6b;
  font-size: 16px;
  line-height: 1.7;
}

.composer {
  width: min(100%, 820px);
  margin: 0 auto;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #c4d7d2;
  border-radius: 8px;
  box-shadow: 0 18px 48px rgba(27, 79, 68, 0.1);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.composer:focus-within {
  border-color: #48a892;
  box-shadow: 0 0 0 3px #d8f1eb, 0 18px 48px rgba(27, 79, 68, 0.12);
}

.prompt-input {
  min-height: 142px;
  padding: 20px 22px 10px;
  color: #14211f;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  font-size: 16px;
  line-height: 1.7;
  resize: none;
}

.prompt-input::placeholder {
  color: #93a39f;
}

.prompt-input:focus {
  border: 0;
  box-shadow: none;
}

.composer-footer {
  min-height: 62px;
  padding: 9px 11px 11px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-top: 1px solid #edf2f0;
}

.composer-hint {
  color: #8b9a96;
  font-size: 12px;
}

.composer-preferences {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.memory-option-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #405a54;
  font-size: 13px;
  cursor: help;
}

.memory-option-state {
  color: #8b9a96;
  font-size: 12px;
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.prompt-count {
  color: #8b9a96;
  font-size: 12px;
}

.create-button {
  min-width: 122px;
  height: 42px;
  border-radius: 7px;
  background: #152522;
  border-color: #152522;
  box-shadow: none;
}

.create-button:hover,
.create-button:focus {
  background: #23453f !important;
  border-color: #23453f !important;
}

.template-area {
  margin-top: 17px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.template-area > span {
  color: #728681;
  font-size: 12px;
}

.template-row {
  display: flex;
  justify-content: center;
  gap: 8px;
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.template-row::-webkit-scrollbar {
  display: none;
}

.template-button {
  flex: 0 0 auto;
  height: 36px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #405a54;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid #d3e2de;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.template-button:hover {
  color: #176b5b;
  background: #ffffff;
  border-color: #8bc7b8;
}

.content-area {
  padding-top: 42px;
  padding-bottom: 64px;
}

.project-section + .project-section {
  margin-top: 64px;
}

.section-header {
  min-height: 52px;
  margin-bottom: 18px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.section-header h2 {
  margin: 0;
  color: #14211f;
  font-size: 23px;
  font-weight: 720;
  letter-spacing: 0;
}

.section-header p {
  margin: 7px 0 0;
  color: #6b7d79;
  font-size: 14px;
}

.section-count {
  height: 30px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  color: #475467;
  background: #ffffff;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  font-size: 12px;
}

.project-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.selected-count {
  color: #475467;
  font-size: 12px;
  white-space: nowrap;
}

.section-arrow {
  margin-top: 6px;
  color: #98a2b3;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.app-skeleton {
  min-height: 285px;
  padding: 24px;
  background: #ffffff;
  border: 1px solid #dce7e4;
  border-radius: 8px;
}

.empty-state {
  min-height: 250px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8a9b97;
  background: #ffffff;
  border: 1px dashed #c9d9d5;
  border-radius: 8px;
  text-align: center;
}

.empty-state > :first-child {
  font-size: 30px;
}

.empty-state h3 {
  margin: 13px 0 0;
  color: #344054;
  font-size: 15px;
}

.empty-state p {
  margin: 7px 0 16px;
  color: #98a2b3;
  font-size: 13px;
}

.empty-state.compact {
  min-height: 170px;
}

.pagination-wrapper {
  margin-top: 26px;
  display: flex;
  justify-content: center;
}

@media (max-width: 980px) {
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .page-container {
    width: calc(100% - 24px);
  }

  .generator-inner {
    padding-top: 42px;
    padding-bottom: 36px;
  }

  .generator-band {
    min-height: 500px;
  }

  .hero-copy h1 {
    font-size: 32px;
  }

  .prompt-input {
    min-height: 132px;
    padding: 17px 16px 8px;
    font-size: 15px;
  }

  .composer-footer {
    min-height: 58px;
    padding-left: 14px;
  }

  .composer-hint {
    display: none;
  }

  .composer-actions {
    width: auto;
    margin-left: auto;
    justify-content: space-between;
  }

  .memory-option-label {
    white-space: nowrap;
  }

  .memory-option-state {
    display: none;
  }

  .create-button {
    min-width: 112px;
  }

  .content-area {
    padding-top: 28px;
  }

  .project-section + .project-section {
    margin-top: 42px;
  }

  .app-grid {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .section-header h2 {
    font-size: 19px;
  }

  .section-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .project-controls {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
