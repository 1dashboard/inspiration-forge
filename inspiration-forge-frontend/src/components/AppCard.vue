<template>
  <article class="app-card" :class="{ selected }">
    <div class="app-preview">
      <img v-if="app.cover" :src="app.cover" :alt="`${app.appName || '应用'}预览`" loading="lazy" />
      <div v-else class="app-placeholder">
        <img :src="logo" alt="" />
        <span>{{ app.deployKey ? '封面生成中' : '等待部署预览' }}</span>
      </div>
      <span class="status-badge" :class="statusClass">
        <span class="status-dot"></span>
        {{ statusText }}
      </span>
      <span v-if="featured" class="featured-badge">精选</span>
      <label v-if="selectable" class="selection-control" @click.stop>
        <a-checkbox
          :checked="selected"
          :aria-label="`选择${app.appName || '应用'}`"
          @change="handleToggleSelected"
        />
      </label>
    </div>

    <div class="app-body">
      <div class="app-heading">
        <div class="title-block">
          <h3>{{ app.appName || '未命名应用' }}</h3>
          <p>{{ typeLabel }}</p>
        </div>
        <a-tooltip v-if="app.deployKey" title="打开已部署应用">
          <a-button class="preview-button" type="text" aria-label="打开已部署应用" @click="handleViewWork">
            <template #icon><EyeOutlined /></template>
          </a-button>
        </a-tooltip>
      </div>

      <div class="app-footer">
        <div class="author">
          <a-avatar :src="app.user?.userAvatar" :size="28">
            {{ app.user?.userName?.charAt(0) || 'U' }}
          </a-avatar>
          <span>{{ app.user?.userName || (featured ? '社区用户' : '我的项目') }}</span>
        </div>
        <div class="card-actions">
          <a-popconfirm
            v-if="deletable"
            title="删除后代码、版本和对话数据将无法恢复，确定删除？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="handleDelete"
          >
            <a-button
              type="text"
              danger
              class="delete-button"
              :loading="deleting"
              :aria-label="`删除${app.appName || '应用'}`"
            >
              <template #icon><DeleteOutlined /></template>
            </a-button>
          </a-popconfirm>
          <a-button type="link" class="continue-button" @click="handleViewChat">
            {{ featured ? '查看详情' : '继续编辑' }}
            <ArrowRightOutlined />
          </a-button>
        </div>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRightOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons-vue'
import logo from '@/assets/logo-doraemon.jpg'
import { formatCodeGenType } from '@/utils/codeGenTypes'

interface Props {
  app: API.AppVO
  featured?: boolean
  selectable?: boolean
  selected?: boolean
  deletable?: boolean
  deleting?: boolean
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
  (e: 'toggle-selected', app: API.AppVO, checked: boolean): void
  (e: 'delete', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
  selectable: false,
  selected: false,
  deletable: false,
  deleting: false,
})
const emit = defineEmits<Emits>()

const statusText = computed(() => {
  if (props.app.generationStatus === 'GENERATING') return '生成中'
  if (props.app.generationStatus === 'FAILED') return '生成失败'
  if (props.app.deployStatus === 'PAUSED') return '已暂停'
  if (props.app.deployKey) return '已部署'
  return '草稿'
})

const statusClass = computed(() => ({
  generating: props.app.generationStatus === 'GENERATING',
  error: props.app.generationStatus === 'FAILED',
  paused: props.app.deployStatus === 'PAUSED',
  deployed: Boolean(props.app.deployKey) && props.app.deployStatus !== 'PAUSED',
}))

const typeLabel = computed(() => {
  return props.app.codeGenType ? formatCodeGenType(props.app.codeGenType) : '网页应用'
})

const handleViewChat = () => emit('view-chat', props.app.id)
const handleViewWork = () => emit('view-work', props.app)
const handleToggleSelected = (event: { target?: { checked?: boolean } }) => {
  emit('toggle-selected', props.app, Boolean(event.target?.checked))
}
const handleDelete = () => emit('delete', props.app)
</script>

<style scoped>
.app-card {
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #dce7e4;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(20, 49, 43, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.app-card:hover {
  border-color: #9bcbbf;
  box-shadow: 0 12px 28px rgba(20, 49, 43, 0.1);
  transform: translateY(-2px);
}

.app-card.selected {
  border-color: #2f8f7b;
  box-shadow: 0 0 0 2px rgba(47, 143, 123, 0.13);
}

.app-preview {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: #edf3f1;
  border-bottom: 1px solid #e5ecea;
}

.app-preview > img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  object-position: top center;
  transition: transform 0.25s ease;
}

.app-card:hover .app-preview > img {
  transform: scale(1.015);
}

.app-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #98a2b3;
  font-size: 12px;
}

.app-placeholder img {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  opacity: 0.72;
}

.status-badge,
.featured-badge {
  position: absolute;
  top: 10px;
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  color: #475467;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(208, 213, 221, 0.9);
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  backdrop-filter: blur(8px);
}

.selection-control {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(208, 213, 221, 0.92);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.94);
  cursor: pointer;
  backdrop-filter: blur(8px);
}

.status-badge {
  left: 10px;
  gap: 6px;
}

.featured-badge {
  right: 10px;
  color: #9a6700;
  background: #fffaeb;
  border-color: #fedf89;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #98a2b3;
}

.status-badge.deployed .status-dot {
  background: #12b76a;
}

.status-badge.generating .status-dot {
  background: #2e90fa;
}

.status-badge.error .status-dot {
  background: #f04438;
}

.status-badge.paused .status-dot {
  background: #f79009;
}

.app-body {
  padding: 16px 17px 13px;
}

.app-heading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.title-block {
  min-width: 0;
  flex: 1;
}

.title-block h3 {
  margin: 0;
  overflow: hidden;
  color: #14211f;
  font-size: 16px;
  font-weight: 680;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-block p {
  margin: 5px 0 0;
  color: #84948f;
  font-size: 12px;
}

.preview-button {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  color: #667085;
}

.app-footer {
  min-height: 38px;
  margin-top: 12px;
  padding-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-top: 1px solid #edf2f0;
}

.author {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #667a75;
  font-size: 12px;
}

.author span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.continue-button {
  flex: 0 0 auto;
  height: 30px;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
}

.card-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
}

.delete-button {
  width: 30px;
  height: 30px;
}
</style>
