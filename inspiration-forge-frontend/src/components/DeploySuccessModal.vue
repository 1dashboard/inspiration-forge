<template>
  <a-modal v-model:open="visible" title="部署成功" :footer="null" width="600px">
    <div class="deploy-success">
      <div class="success-icon">
        <CheckCircleOutlined style="color: #52c41a; font-size: 48px" />
      </div>
      <h3>网站部署成功！</h3>
      <p>你的网站已经成功部署，可以通过以下链接访问：</p>
      <div class="deploy-url">
        <div class="deploy-link-field">
          <input
            ref="deployUrlInput"
            :value="deployUrl"
            aria-label="部署后的网站链接"
            aria-describedby="deploy-copy-hint"
            readonly
            spellcheck="false"
            @click="selectDeployUrl"
          />
          <a-button
            type="text"
            aria-label="复制部署链接"
            title="复制链接"
            :loading="copying"
            :disabled="!deployUrl"
            @click="handleCopyUrl"
          >
            <CopyOutlined />
          </a-button>
        </div>
        <p id="deploy-copy-hint" class="copy-hint" role="status" aria-live="polite">
          {{ manualCopyRequired
            ? '链接已选中，请按 Ctrl+C（Mac：⌘C），或长按链接选择“复制”。'
            : '也可点击链接全选后手动复制，手机可长按链接。' }}
        </p>
      </div>
      <div class="deploy-actions">
        <a-button type="primary" @click="handleOpenSite">访问网站</a-button>
        <a-button @click="handleClose">关闭</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CheckCircleOutlined, CopyOutlined } from '@ant-design/icons-vue'
import { copyInputValue } from '@/utils/clipboard'

interface Props {
  open: boolean
  deployUrl: string
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'open-site'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const deployUrlInput = ref<HTMLInputElement | null>(null)
const copying = ref(false)
const manualCopyRequired = ref(false)

watch(() => [props.open, props.deployUrl], () => {
  manualCopyRequired.value = false
})

const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

const selectDeployUrl = () => {
  deployUrlInput.value?.select()
}

const handleCopyUrl = async () => {
  const input = deployUrlInput.value
  if (copying.value || !input || !props.deployUrl) return
  copying.value = true
  try {
    const copied = await copyInputValue(input)
    manualCopyRequired.value = !copied
    if (copied) {
      message.success({ content: '链接已复制到剪贴板', key: 'deploy-copy' })
    } else {
      message.warning({ content: '浏览器限制了自动复制，请手动复制已选中的链接', key: 'deploy-copy' })
    }
  } finally {
    copying.value = false
  }
}

const handleOpenSite = () => {
  emit('open-site')
}

const handleClose = () => {
  visible.value = false
}
</script>

<style scoped>
.deploy-success {
  text-align: center;
  padding: 24px;
}

.success-icon {
  margin-bottom: 16px;
}

.deploy-success h3 {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
}

.deploy-success p {
  margin: 0 0 24px;
  color: #666;
}

.deploy-url {
  margin-bottom: 24px;
}

.deploy-link-field {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 12px;
  border: 1px solid #b8cec8;
  border-radius: 6px;
  background: #fff;
}

.deploy-link-field:focus-within {
  border-color: #176b5b;
  box-shadow: 0 0 0 2px #176b5b14;
}

.deploy-link-field input {
  flex: 1;
  min-width: 0;
  width: 100%;
  padding: 6px 0;
  color: #243b35;
  font: inherit;
  background: transparent;
  border: 0;
  outline: none;
}

.deploy-success .copy-hint {
  margin: 8px 0 0;
  color: #73847e;
  font-size: 12px;
  text-align: left;
}

.deploy-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
