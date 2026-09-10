import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import { parse, compileScript } from '@vue/compiler-sfc'
import ts from 'typescript'
import * as Vue from 'vue'
import * as coverPolling from '../src/utils/coverPolling.ts'

const require = createRequire(import.meta.url)
const flush = async () => { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
const { descriptor } = parse(fs.readFileSync(new URL('../src/pages/HomePage.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, { id: 'home-cover-regression', inlineTemplate: true })
const componentCode = ts.transpileModule(compiled.content, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, esModuleInterop: true },
}).outputText

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}
function node(type, text = '') { return { type, text, props: {}, children: [], parent: null } }
function remove(child) {
  if (!child.parent) return
  const children = child.parent.children
  children.splice(children.indexOf(child), 1)
  child.parent = null
}
function insert(child, parent, anchor = null) {
  remove(child)
  const index = anchor ? parent.children.indexOf(anchor) : -1
  parent.children.splice(index < 0 ? parent.children.length : index, 0, child)
  child.parent = parent
}
function collect(root, type) {
  return [root, ...root.children.flatMap(child => collect(child, type))].filter(item => item.type === type)
}

function mountHome(t, { delayedLists = false } = {}) {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const listeners = new Map()
  const previousDocument = Object.getOwnPropertyDescriptor(globalThis, 'document')
  Object.defineProperty(globalThis, 'document', { configurable: true, value: {
    visibilityState: 'visible',
    addEventListener: (name, handler) => listeners.set(name, handler),
    removeEventListener: (name, handler) => { if (listeners.get(name) === handler) listeners.delete(name) },
  } })
  const calls = { mine: 0, featured: 0, details: 0, mounted: 0, unmounted: 0 }
  const coverRequest = deferred(), listsReady = deferred()
  const card = { id: 101, deployKey: 'test', appName: 'Pending cover' }
  const readyCard = { id: 102, deployKey: 'ready', appName: 'Existing cover', cover: '/existing.png' }
  const response = records => ({ data: { code: 0, data: { records, totalRow: records.length } } })
  const api = {
    async listMyAppVoByPage() { calls.mine++; if (delayedLists) await listsReady.promise; return response([{ ...card }, { ...readyCard }]) },
    async listGoodAppVoByPage() { calls.featured++; if (delayedLists) await listsReady.promise; return response([{ ...card }]) },
    async getAppVoById({ id }, { signal }) {
      calls.details++
      calls.lastId = id
      calls.signal = signal
      return coverRequest.promise
    },
  }
  const CardStub = Vue.defineComponent({
    props: ['app'],
    setup(props) {
      Vue.onMounted(() => calls.mounted++)
      Vue.onUnmounted(() => calls.unmounted++)
      return () => Vue.h('card', { id: props.app.id, cover: props.app.cover, appName: props.app.appName })
    },
  })
  const icon = () => Vue.h('icon')
  const module = { exports: {} }
  const mocks = {
    vue: Vue,
    'vue-router': { useRouter: () => ({ push: async () => {} }) },
    'ant-design-vue': { message: { error() {}, warning() {}, info() {}, success() {} }, Modal: { confirm() {} } },
    '@ant-design/icons-vue': new Proxy({}, { get: () => icon }),
    '@/stores/loginUser': { useLoginUserStore: () => ({ loginUser: { id: 1 } }) },
    '@/api/appController': api,
    '@/config/env': { getDeployUrl: key => `/dist/${key}/` },
    '@/components/AppCard.vue': CardStub,
    '@/utils/coverPolling': coverPolling,
  }
  new Function('require', 'module', 'exports', componentCode)(name => mocks[name] ?? require(name), module, module.exports)
  const renderer = Vue.createRenderer({
    createElement: type => node(type), createText: text => node('text', text), createComment: text => node('comment', text),
    insert, remove,
    setText: (item, value) => { item.text = value },
    setElementText: (item, value) => { item.text = value; item.children = [] },
    parentNode: item => item.parent,
    nextSibling: item => item.parent?.children[item.parent.children.indexOf(item) + 1] ?? null,
    patchProp: (item, key, previous, value) => { item.props[key] = value },
    insertStaticContent(content, parent, anchor) { const item = node('static', content); insert(item, parent, anchor); return [item, item] },
  })
  const mounted = renderer.createApp(module.exports.default)
  for (const type of ['button', 'textarea', 'tooltip', 'switch', 'checkbox', 'pagination', 'skeleton']) {
    mounted.component(`a-${type}`, Vue.defineComponent({
      inheritAttrs: false,
      setup(_props, { attrs, slots }) { return () => Vue.h(type, { ...attrs }, slots.default?.()) },
    }))
  }
  const root = node('root')
  mounted.mount(root)
  let unmounted = false
  const unmount = () => { if (!unmounted) { mounted.unmount(); unmounted = true } }
  t.after(() => {
    unmount()
    if (previousDocument) Object.defineProperty(globalThis, 'document', previousDocument)
    else delete globalThis.document
  })
  return { root, calls, coverRequest, listsReady, listeners, unmount,
    tick: async ms => { t.mock.timers.tick(ms); await flush() } }
}

test('real HomePage keeps cards, skeleton state and composer stable during the minute refresh', async t => {
  const { root, calls, coverRequest, tick } = mountHome(t)
  await flush()
  const cards = collect(root, 'card')
  assert.equal(cards.length, 3)
  assert.equal(calls.mounted, 3)
  assert.equal(collect(root, 'skeleton').length, 0)
  const input = collect(root, 'textarea')[0]
  input.props['onUpdate:value']('Keep my unfinished description')
  await flush()

  await tick(59999)
  assert.equal(calls.details, 0)
  await tick(1)
  assert.equal(calls.details, 1, 'shared pending card is fetched once')
  assert.equal(calls.mine, 1, 'background cover check never reloads my list')
  assert.equal(calls.featured, 1, 'background cover check never reloads featured list')
  assert.equal(collect(root, 'skeleton').length, 0, 'no skeleton even while the request is pending')
  collect(root, 'card').forEach((item, index) => assert.strictEqual(item, cards[index]))

  coverRequest.resolve({ data: { code: 0, data: { id: 101, deployKey: 'test', cover: '/new-cover.png', appName: 'Must not replace this' } } })
  await flush()
  collect(root, 'card').forEach((item, index) => assert.strictEqual(item, cards[index]))
  assert.equal(calls.unmounted, 0)
  assert.equal(calls.mounted, 3)
  assert.equal(cards[0].props.cover, '/new-cover.png')
  assert.equal(cards[2].props.cover, '/new-cover.png')
  assert.equal(cards[1].props.cover, '/existing.png')
  assert.equal(cards[0].props.appName, 'Pending cover')
  assert.equal(input.props.value, 'Keep my unfinished description')
  await tick(120000)
  assert.equal(calls.details, 1)
})

test('real HomePage removes visibility listener and never polls after early unmount', async t => {
  const { calls, listeners, listsReady, unmount, tick } = mountHome(t, { delayedLists: true })
  assert.equal(listeners.has('visibilitychange'), true)
  unmount()
  assert.equal(listeners.size, 0)
  listsReady.resolve()
  await flush()
  await tick(120000)
  assert.equal(calls.details, 0)
  assert.equal(calls.mounted, 0)
})
