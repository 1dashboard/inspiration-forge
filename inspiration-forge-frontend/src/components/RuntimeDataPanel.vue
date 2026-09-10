<template>
  <section class="runtime-panel">
    <header class="runtime-toolbar">
      <div class="runtime-environment">
        <a-segmented v-model:value="environment" :options="environmentOptions" />
        <a-tag :color="environment === 'PREVIEW' ? 'blue' : 'green'">
          {{ environment === 'PREVIEW' ? '预览数据' : '生产数据' }}
        </a-tag>
      </div>
      <div class="runtime-actions">
        <a-button @click="openUsers">
          <template #icon><UserOutlined /></template>
          应用用户
        </a-button>
        <a-button @click="loadModels" :loading="loadingModels">
          <template #icon><ReloadOutlined /></template>
        </a-button>
        <a-button type="primary" :disabled="environment !== 'PREVIEW'" @click="openModelEditor()">
          <template #icon><PlusOutlined /></template>
          新建模型
        </a-button>
      </div>
    </header>

    <div class="runtime-body">
      <aside class="model-list">
        <div class="model-list-heading">
          <span>数据模型</span><small>{{ models.length }}</small>
        </div>
        <button v-for="model in models" :key="model.id" type="button" class="model-item"
                :class="{ active: activeModel?.id === model.id }" @click="selectModel(model)">
          <DatabaseOutlined />
          <span><strong>{{ model.definition.displayName }}</strong><small>{{ model.definition.modelKey }}</small></span>
          <a-tag :color="model.status === 'PUBLISHED' ? 'green' : 'default'">v{{ model.schemaVersion }}</a-tag>
        </button>
        <a-empty v-if="!loadingModels && !models.length" :image="simpleImage" description="暂无数据模型" />
      </aside>

      <main class="record-workspace">
        <template v-if="activeModel">
          <div class="record-heading">
            <div>
              <h4>{{ activeModel.definition.displayName }}</h4>
              <span>{{ activeModel.definition.fields.length }} 个字段 · {{ total }} 条记录</span>
            </div>
            <div class="record-actions">
              <a-button v-if="environment === 'PREVIEW'" @click="openModelEditor(activeModel)">
                <template #icon><SettingOutlined /></template>模型设置
              </a-button>
              <a-button v-if="environment === 'PREVIEW'" :loading="publishing" @click="publishModel">
                <template #icon><CloudUploadOutlined /></template>发布模型
              </a-button>
              <a-button type="primary" @click="openRecordEditor()">
                <template #icon><PlusOutlined /></template>新增数据
              </a-button>
            </div>
          </div>

          <div v-if="activeModel.definition.publicOperations.length" class="public-access-note">
            <SafetyCertificateOutlined />
            匿名开放：{{ activeModel.definition.publicOperations.map(operationLabel).join('、') }}
          </div>

          <a-table :columns="recordColumns" :data-source="records" :loading="loadingRecords"
                   :pagination="pagination" row-key="id" size="middle" @change="handleTableChange">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'actions'">
                <a-space>
                  <a-button type="link" size="small" @click="openRecordEditor(record)">编辑</a-button>
                  <a-popconfirm title="确定删除这条数据吗？" @confirm="removeRecord(record)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
              <template v-else-if="column.key === 'version'">v{{ record.version }}</template>
              <template v-else-if="column.dataIndex">
                <span class="cell-value">{{ formatCell(record.data[column.dataIndex]) }}</span>
              </template>
            </template>
          </a-table>
        </template>
        <div v-else class="runtime-empty">
          <DatabaseOutlined />
          <h4>为应用添加托管数据</h4>
          <p>创建模型后，可在这里维护预览数据并发布到生产环境。</p>
          <a-button v-if="environment === 'PREVIEW'" type="primary" @click="openModelEditor()">新建数据模型</a-button>
        </div>
      </main>
    </div>

    <a-modal v-model:open="modelEditorOpen" :title="editingModelId ? '编辑数据模型' : '新建数据模型'"
             width="820px" ok-text="保存模型" :confirm-loading="savingModel" @ok="saveModel">
      <a-form layout="vertical" class="model-form">
        <div class="form-grid">
          <a-form-item label="模型名称" required><a-input v-model:value="modelDraft.displayName" placeholder="例如：任务" /></a-form-item>
          <a-form-item label="模型标识" required><a-input v-model:value="modelDraft.modelKey" :disabled="Boolean(editingModelId)" placeholder="例如：tasks" /></a-form-item>
        </div>
        <a-form-item label="匿名访问权限">
          <a-checkbox-group v-model:value="modelDraft.publicOperations" :options="operationOptions" />
          <div class="permission-hint">未勾选时仅应用所有者可访问。修改和删除建议保持关闭。</div>
        </a-form-item>
        <div class="field-heading"><span>字段</span><a-button size="small" @click="addField"><PlusOutlined />添加字段</a-button></div>
        <div class="field-table">
          <div v-for="(field, index) in modelDraft.fields" :key="index" class="field-row">
            <a-input v-model:value="field.name" placeholder="显示名称" />
            <a-input v-model:value="field.key" placeholder="字段标识" />
            <a-select v-model:value="field.type" :options="fieldTypeOptions" />
            <a-checkbox v-model:checked="field.required">必填</a-checkbox>
            <a-input v-if="field.type === 'ENUM'" v-model:value="field.optionsText" placeholder="选项用逗号分隔" />
            <a-input-number v-else-if="field.type === 'STRING' || field.type === 'TEXT'"
                            v-model:value="field.maxLength" :min="1" :max="100000" placeholder="最大长度" />
            <span v-else class="field-no-option">-</span>
            <a-button type="text" danger aria-label="删除字段" @click="modelDraft.fields.splice(index, 1)"><DeleteOutlined /></a-button>
          </div>
        </div>
      </a-form>
    </a-modal>

    <a-modal v-model:open="recordEditorOpen" :title="editingRecord ? '编辑数据' : '新增数据'"
             ok-text="保存" :confirm-loading="savingRecord" @ok="saveRecord">
      <a-form v-if="activeModel" layout="vertical">
        <a-form-item v-for="field in activeModel.definition.fields" :key="field.key"
                     :label="field.name" :required="field.required">
          <a-switch v-if="field.type === 'BOOLEAN'" v-model:checked="recordDraft[field.key]" />
          <a-input-number v-else-if="field.type === 'INTEGER' || field.type === 'DECIMAL'"
                          v-model:value="recordDraft[field.key]" :precision="field.type === 'INTEGER' ? 0 : undefined" style="width: 100%" />
          <a-date-picker v-else-if="field.type === 'DATE'" v-model:value="recordDraft[field.key]" value-format="YYYY-MM-DD" style="width: 100%" />
          <a-date-picker v-else-if="field.type === 'DATETIME'" v-model:value="recordDraft[field.key]" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          <a-select v-else-if="field.type === 'ENUM'" v-model:value="recordDraft[field.key]"
                    :options="(field.options || []).map(value => ({ label: value, value }))" />
          <a-textarea v-else-if="field.type === 'TEXT'" v-model:value="recordDraft[field.key]" :maxlength="field.maxLength" :rows="4" />
          <a-input v-else v-model:value="recordDraft[field.key]" :maxlength="field.maxLength" />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="usersModalOpen" title="应用用户与角色" :footer="null" width="760px">
      <a-table :columns="userColumns" :data-source="runtimeUsers" :loading="loadingUsers" row-key="userId" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <a-select v-model:value="roleDraft[record.userId]" :options="roleOptions" style="width: 150px"
                      @change="(value: string) => changeRole(record.userId, value)" />
          </template>
          <template v-else-if="column.key === 'roles'">
            <a-space wrap><a-tag v-for="role in record.roles" :key="role">{{ role }}</a-tag></a-space>
          </template>
        </template>
      </a-table>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { CloudUploadOutlined, DatabaseOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined,
  SafetyCertificateOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
import {
  createRuntimeRecord, deleteRuntimeRecord, listRuntimeModels, publishRuntimeModel, queryRuntimeRecords,
  updateRuntimeRecord, upsertRuntimeModel, listRuntimeUsers, assignRuntimeRole, type RuntimeEnvironment,
  type RuntimeFieldType, type RuntimeModel, type RuntimeModelDefinition, type RuntimeOperation, type RuntimeRecord,
  type RuntimeAuthUser,
} from '@/api/runtimeController'

const props = defineProps<{ appId: string }>()
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE
const environment = ref<RuntimeEnvironment>('PREVIEW')
const environmentOptions = [{ label: '预览', value: 'PREVIEW' }, { label: '生产', value: 'PRODUCTION' }]
const models = ref<RuntimeModel[]>([])
const activeModel = ref<RuntimeModel | null>(null)
const records = ref<RuntimeRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loadingModels = ref(false)
const loadingRecords = ref(false)
const publishing = ref(false)
const modelEditorOpen = ref(false)
const editingModelId = ref('')
const savingModel = ref(false)
const recordEditorOpen = ref(false)
const editingRecord = ref<RuntimeRecord | null>(null)
const savingRecord = ref(false)
const usersModalOpen = ref(false)
const loadingUsers = ref(false)
const runtimeUsers = ref<RuntimeAuthUser[]>([])
const roleDraft = reactive<Record<string, string>>({})
const roleOptions = [{ value: 'admin', label: 'admin' }, { value: 'user', label: 'user' }, { value: 'editor', label: 'editor' }]
const userColumns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '当前角色', key: 'roles' },
  { title: '分配角色', key: 'role', width: 180 },
]
const recordDraft = reactive<Record<string, any>>({})

interface FieldDraft { key: string; name: string; type: RuntimeFieldType; required: boolean; maxLength?: number; optionsText?: string }
const modelDraft = reactive<{ modelKey: string; displayName: string; publicOperations: RuntimeOperation[]; fields: FieldDraft[] }>({
  modelKey: '', displayName: '', publicOperations: [], fields: [],
})
const fieldTypeOptions = [
  ['STRING', '短文本'], ['TEXT', '长文本'], ['INTEGER', '整数'], ['DECIMAL', '小数'], ['BOOLEAN', '开关'],
  ['DATE', '日期'], ['DATETIME', '日期时间'], ['ENUM', '枚举'],
].map(([value, label]) => ({ value, label }))
const operationOptions = [
  { label: '查询', value: 'READ' }, { label: '新增', value: 'CREATE' },
  { label: '修改', value: 'UPDATE' }, { label: '删除', value: 'DELETE' },
]

const recordColumns = computed(() => [
  ...((activeModel.value?.definition.fields || []).map(field => ({ title: field.name, dataIndex: field.key, key: field.key, ellipsis: true }))),
  { title: '版本', key: 'version', width: 72 }, { title: '操作', key: 'actions', width: 120, fixed: 'right' as const },
])
const pagination = computed(() => ({ current: pageNum.value, pageSize: pageSize.value, total: total.value, showSizeChanger: true }))

const apiData = <T>(response: any): T => {
  if (response.data?.code !== 0) throw new Error(response.data?.message || '操作失败')
  return response.data.data as T
}

const loadModels = async () => {
  loadingModels.value = true
  try {
    const selectedKey = activeModel.value?.definition.modelKey
    models.value = apiData<RuntimeModel[]>(await listRuntimeModels(props.appId, environment.value)) || []
    activeModel.value = models.value.find(model => model.definition.modelKey === selectedKey) || models.value[0] || null
    if (activeModel.value) await loadRecords()
    else { records.value = []; total.value = 0 }
  } catch (error: any) { message.error(error.message || '加载数据模型失败') }
  finally { loadingModels.value = false }
}

const loadRecords = async () => {
  if (!activeModel.value) return
  loadingRecords.value = true
  try {
    const page = apiData<any>(await queryRuntimeRecords(props.appId, environment.value,
      activeModel.value.definition.modelKey, pageNum.value, pageSize.value))
    records.value = page?.records || []; total.value = Number(page?.total || 0)
  } catch (error: any) { message.error(error.message || '加载数据失败') }
  finally { loadingRecords.value = false }
}

const selectModel = async (model: RuntimeModel) => { activeModel.value = model; pageNum.value = 1; await loadRecords() }
const operationLabel = (operation: RuntimeOperation) => ({ READ: '查询', CREATE: '新增', UPDATE: '修改', DELETE: '删除' }[operation])
const formatCell = (value: unknown) => value == null ? '-' : typeof value === 'object' ? JSON.stringify(value) : String(value)
const addField = () => modelDraft.fields.push({ key: '', name: '', type: 'STRING', required: false, maxLength: 255 })

const openModelEditor = (model?: RuntimeModel | null) => {
  editingModelId.value = model?.id || ''
  modelDraft.modelKey = model?.definition.modelKey || ''
  modelDraft.displayName = model?.definition.displayName || ''
  modelDraft.publicOperations = [...(model?.definition.publicOperations || [])]
  modelDraft.fields = (model?.definition.fields || []).map(field => ({ ...field, optionsText: (field.options || []).join(', ') }))
  if (!modelDraft.fields.length) addField()
  modelEditorOpen.value = true
}

const saveModel = async () => {
  const fields = modelDraft.fields.map(field => ({
    key: field.key.trim(), name: field.name.trim(), type: field.type, required: field.required,
    maxLength: field.type === 'STRING' || field.type === 'TEXT' ? field.maxLength : undefined,
    options: field.type === 'ENUM' ? (field.optionsText || '').split(/[,，]/).map(value => value.trim()).filter(Boolean) : [],
  }))
  if (!modelDraft.displayName.trim() || !modelDraft.modelKey.trim() || fields.some(field => !field.key || !field.name)) {
    message.warning('请完整填写模型和字段信息'); return
  }
  savingModel.value = true
  try {
    const definition: RuntimeModelDefinition = { modelKey: modelDraft.modelKey.trim().toLowerCase(),
      displayName: modelDraft.displayName.trim(), fields, publicOperations: modelDraft.publicOperations }
    await upsertRuntimeModel(props.appId, definition)
    message.success('数据模型已保存'); modelEditorOpen.value = false; await loadModels()
    activeModel.value = models.value.find(model => model.definition.modelKey === definition.modelKey) || activeModel.value
  } catch (error: any) { message.error(error.response?.data?.message || error.message || '保存模型失败') }
  finally { savingModel.value = false }
}

const publishModel = async () => {
  if (!activeModel.value) return
  publishing.value = true
  try { apiData(await publishRuntimeModel(props.appId, activeModel.value.definition.modelKey)); message.success('模型已发布到生产环境') }
  catch (error: any) { message.error(error.message || '发布模型失败') }
  finally { publishing.value = false }
}

const openRecordEditor = (record?: RuntimeRecord) => {
  editingRecord.value = record || null
  Object.keys(recordDraft).forEach(key => delete recordDraft[key])
  Object.assign(recordDraft, record?.data || {})
  recordEditorOpen.value = true
}

const saveRecord = async () => {
  if (!activeModel.value) return
  const data: Record<string, unknown> = {}
  activeModel.value.definition.fields.forEach(field => {
    const value = recordDraft[field.key]
    if (value !== undefined && value !== null && value !== '') data[field.key] = value
  })
  savingRecord.value = true
  try {
    if (editingRecord.value) await updateRuntimeRecord(props.appId, environment.value,
      activeModel.value.definition.modelKey, editingRecord.value.id, editingRecord.value.version, data)
    else await createRuntimeRecord(props.appId, environment.value, activeModel.value.definition.modelKey, data)
    message.success(editingRecord.value ? '数据已更新' : '数据已创建'); recordEditorOpen.value = false; await loadRecords()
  } catch (error: any) { message.error(error.response?.data?.message || error.message || '保存数据失败') }
  finally { savingRecord.value = false }
}

const removeRecord = async (record: RuntimeRecord) => {
  if (!activeModel.value) return
  try { await deleteRuntimeRecord(props.appId, environment.value, activeModel.value.definition.modelKey, record.id, record.version); message.success('数据已删除'); await loadRecords() }
  catch (error: any) { message.error(error.response?.data?.message || error.message || '删除数据失败') }
}

const openUsers = async () => {
  usersModalOpen.value = true
  loadingUsers.value = true
  try {
    runtimeUsers.value = apiData<RuntimeAuthUser[]>(await listRuntimeUsers(props.appId)) || []
    Object.keys(roleDraft).forEach(key => delete roleDraft[key])
    runtimeUsers.value.forEach(user => { roleDraft[user.userId] = user.roles?.[0] || 'user' })
  } catch (error: any) { message.error(error.response?.data?.message || error.message || '加载应用用户失败') }
  finally { loadingUsers.value = false }
}

const changeRole = async (userId: string, roleKey: string) => {
  try {
    await assignRuntimeRole(props.appId, userId, roleKey)
    const user = runtimeUsers.value.find(item => item.userId === userId)
    if (user) user.roles = [roleKey]
    message.success('角色已更新')
  } catch (error: any) { message.error(error.response?.data?.message || error.message || '角色更新失败') }
}

const handleTableChange = (pagination: any) => { pageNum.value = pagination.current || 1; pageSize.value = pagination.pageSize || 20; loadRecords() }
watch(environment, () => { pageNum.value = 1; activeModel.value = null; loadModels() })
onMounted(loadModels)
</script>

<style scoped>
.runtime-panel { height: 100%; min-height: 0; display: flex; flex-direction: column; background: #fff; }
.runtime-toolbar { min-height: 54px; padding: 10px 16px; border-bottom: 1px solid #e8ebf0; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.runtime-environment,.runtime-actions,.record-actions { display: flex; align-items: center; gap: 8px; }
.runtime-body { flex: 1; min-height: 0; display: grid; grid-template-columns: 220px minmax(0,1fr); }
.model-list { border-right: 1px solid #e8ebf0; overflow: auto; padding: 12px 8px; background: #f8f9fb; }
.model-list-heading { padding: 2px 8px 10px; display: flex; justify-content: space-between; color: #666; font-size: 13px; }
.model-item { width: 100%; min-height: 58px; border: 0; background: transparent; display: grid; grid-template-columns: 20px 1fr auto; align-items: center; gap: 9px; text-align: left; padding: 8px 10px; border-radius: 6px; cursor: pointer; color: #535862; }
.model-item:hover { background: #eef1f5; }.model-item.active { background: #e8f0ff; color: #175cd3; }
.model-item > span { min-width: 0; display: flex; flex-direction: column; }.model-item strong { font-size: 14px; color: inherit; }.model-item small { color: #8a909a; overflow: hidden; text-overflow: ellipsis; }
.record-workspace { min-width: 0; min-height: 0; overflow: auto; padding: 18px; }
.record-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 14px; }.record-heading h4 { margin: 0 0 2px; font-size: 18px; }.record-heading span { color: #888; }
.public-access-note { padding: 9px 12px; margin-bottom: 12px; border: 1px solid #c8ead8; background: #f2fbf6; color: #227a4d; border-radius: 6px; display: flex; gap: 8px; align-items: center; }
.runtime-empty { height: 100%; min-height: 300px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #8a909a; text-align: center; }.runtime-empty > span { font-size: 36px; }.runtime-empty h4 { margin: 12px 0 4px; color: #30343b; font-size: 17px; }.runtime-empty p { margin: 0 0 16px; }
.cell-value { display: block; max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }.permission-hint { margin-top: 6px; color: #8a909a; font-size: 12px; }
.field-heading { display: flex; justify-content: space-between; align-items: center; margin: 4px 0 10px; font-weight: 600; }
.field-table { display: flex; flex-direction: column; gap: 8px; max-height: 340px; overflow: auto; }.field-row { display: grid; grid-template-columns: 1fr 1fr 120px 58px minmax(130px,1fr) 32px; gap: 8px; align-items: center; }.field-no-option { text-align: center; color: #aaa; }
@media (max-width: 900px) { .runtime-body { grid-template-columns: 1fr; }.model-list { max-height: 150px; border-right: 0; border-bottom: 1px solid #e8ebf0; }.record-heading { align-items: flex-start; flex-direction: column; }.field-row { grid-template-columns: 1fr 1fr; }.runtime-toolbar { align-items: flex-start; }.runtime-environment { flex-wrap: wrap; } }
</style>
