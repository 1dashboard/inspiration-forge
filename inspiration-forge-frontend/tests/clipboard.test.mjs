import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { copyInputValue } from '../src/utils/clipboard.ts'

const descriptors = new Map(
  ['window', 'navigator', 'document'].map((key) => [key, Object.getOwnPropertyDescriptor(globalThis, key)]),
)
afterEach(() => {
  for (const [key, descriptor] of descriptors) {
    if (descriptor) Object.defineProperty(globalThis, key, descriptor)
    else delete globalThis[key]
  }
})

function fixture({ secure = false, writeText, legacy = () => true, value = 'http://example.test/dist/demo/' } = {}) {
  const calls = []
  const input = {
    value,
    focus(options) { calls.push(['focus', options]) },
    select() { calls.push(['select']) },
    setSelectionRange(start, end) { calls.push(['range', start, end]) },
  }
  const globals = {
    window: { isSecureContext: secure },
    navigator: { clipboard: writeText ? { writeText } : undefined },
    document: { execCommand(command) { calls.push(['command', command]); return legacy() } },
  }
  for (const [key, value] of Object.entries(globals)) {
    Object.defineProperty(globalThis, key, { configurable: true, value })
  }
  return { input, calls }
}

test('HTTPS uses Clipboard API with the full URL', async () => {
  const copied = []
  const { input, calls } = fixture({ secure: true, writeText: async (text) => copied.push(text) })
  assert.equal(await copyInputValue(input), true)
  assert.deepEqual(copied, [input.value])
  assert.deepEqual(calls, [])
})

test('HTTP without Clipboard API copies from the visible input', async () => {
  const { input, calls } = fixture()
  assert.equal(await copyInputValue(input), true)
  assert.deepEqual(calls, [['focus', { preventScroll: true }], ['select'], ['range', 0, input.value.length], ['command', 'copy']])
})

test('HTTPS permission rejection falls back to input selection', async () => {
  const { input, calls } = fixture({ secure: true, writeText: async () => { throw new Error('NotAllowedError') } })
  assert.equal(await copyInputValue(input), true)
  assert.equal(calls.at(-1)[0], 'command')
})

test('HTTPS without Clipboard API also uses the fallback', async () => {
  const { input } = fixture({ secure: true })
  assert.equal(await copyInputValue(input), true)
})

test('legacy copy returning false leaves the URL selected for manual copy', async () => {
  const { input, calls } = fixture({ legacy: () => false })
  assert.equal(await copyInputValue(input), false)
  assert.deepEqual(calls.at(-2), ['range', 0, input.value.length])
})

test('legacy copy exception returns false, not an unhandled rejection', async () => {
  const { input } = fixture({ legacy: () => { throw new Error('Blocked') } })
  assert.equal(await copyInputValue(input), false)
})

test('missing legacy API leaves the URL selected for manual copy', async () => {
  const { input, calls } = fixture()
  document.execCommand = undefined
  assert.equal(await copyInputValue(input), false)
  assert.deepEqual(calls.at(-1), ['range', 0, input.value.length])
})

test('empty URL never reports a successful copy', async () => {
  const { input, calls } = fixture({ value: '' })
  assert.equal(await copyInputValue(input), false)
  assert.deepEqual(calls, [])
})
