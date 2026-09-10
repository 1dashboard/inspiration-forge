<template>
  <div ref="container" class="monaco-code-editor"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import JsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import CssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import HtmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import TsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

(self as any).MonacoEnvironment = {
  getWorker(_: string, label: string) {
    if (label === 'json') return new JsonWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new CssWorker()
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new HtmlWorker()
    if (label === 'typescript' || label === 'javascript') return new TsWorker()
    return new EditorWorker()
  },
}

const props = withDefaults(defineProps<{
  modelValue: string
  language?: string
  path?: string
  readOnly?: boolean
}>(), {
  language: 'plaintext',
  path: 'untitled.txt',
  readOnly: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
  save: []
}>()

const container = ref<HTMLElement>()
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let model: monaco.editor.ITextModel | null = null
let resizeObserver: ResizeObserver | null = null
let syncingExternalValue = false

const normalizeLanguage = (language: string) => {
  const value = language.toLowerCase()
  if (value === 'js' || value === 'jsx') return 'javascript'
  if (value === 'ts' || value === 'tsx') return 'typescript'
  if (value === 'vue') return 'html'
  if (value === 'md') return 'markdown'
  if (value === 'yml') return 'yaml'
  if (value === 'text') return 'plaintext'
  return value || 'plaintext'
}

const createModel = () => {
  model?.dispose()
  const safePath = props.path.replace(/[^a-zA-Z0-9._/-]/g, '_')
  model = monaco.editor.createModel(
    props.modelValue,
    normalizeLanguage(props.language),
    monaco.Uri.parse(`inmemory://generated/${safePath}`),
  )
  editor?.setModel(model)
}

onMounted(() => {
  if (!container.value) return
  editor = monaco.editor.create(container.value, {
    automaticLayout: false,
    minimap: { enabled: true, maxColumn: 100 },
    fontSize: 14,
    lineHeight: 22,
    fontFamily: "'Cascadia Code', 'JetBrains Mono', Consolas, monospace",
    scrollBeyondLastLine: false,
    smoothScrolling: true,
    tabSize: 2,
    insertSpaces: true,
    wordWrap: 'off',
    renderWhitespace: 'selection',
    bracketPairColorization: { enabled: true },
    guides: { bracketPairs: true, indentation: true },
    padding: { top: 12, bottom: 16 },
    readOnly: props.readOnly,
    theme: 'vs',
  })
  createModel()
  editor.onDidChangeModelContent(() => {
    if (syncingExternalValue || !model) return
    const value = model.getValue()
    emit('update:modelValue', value)
    emit('change', value)
  })
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => emit('save'))
  resizeObserver = new ResizeObserver(() => editor?.layout())
  resizeObserver.observe(container.value)
})

watch(() => props.modelValue, (value) => {
  if (!model || model.getValue() === value) return
  const position = editor?.getPosition()
  syncingExternalValue = true
  model.setValue(value)
  syncingExternalValue = false
  if (position) editor?.setPosition(position)
})

watch(() => props.language, (language) => {
  if (model) monaco.editor.setModelLanguage(model, normalizeLanguage(language))
})

watch(() => props.path, () => {
  if (!editor) return
  syncingExternalValue = true
  createModel()
  syncingExternalValue = false
})

watch(() => props.readOnly, (readOnly) => editor?.updateOptions({ readOnly }))

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  editor?.dispose()
  model?.dispose()
})
</script>

<style scoped>
.monaco-code-editor {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}
</style>
