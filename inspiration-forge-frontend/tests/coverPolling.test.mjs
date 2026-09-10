import assert from 'node:assert/strict'
import { test } from 'node:test'
import { COVER_POLL_INTERVAL_MS, createCoverPoller } from '../src/utils/coverPolling.ts'

const flush = () => new Promise(resolve => setImmediate(resolve))
const app = (id = '449363655861526528', extra = {}) => ({ id, deployKey: 'published', ...extra })
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function fixture(t, { apps = [], fetchApp, maxAttempts = 20 } = {}) {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const state = { apps, visible: true, calls: [] }
  const poller = createCoverPoller({
    getApps: () => state.apps,
    isVisible: () => state.visible,
    maxAttempts,
    fetchApp: async (id, signal) => {
      state.calls.push({ id, signal })
      return fetchApp ? fetchApp(id, signal) : app(id, { cover: '/ready.png' })
    },
  })
  t.after(() => poller.dispose())
  const tick = async (ms = COVER_POLL_INTERVAL_MS) => { t.mock.timers.tick(ms); await flush() }
  return { state, poller, tick }
}

test('no polling when every card has a cover, is undeployed, or has no id', async t => {
  const { state, poller, tick } = fixture(t, { apps: [app('1', { cover: '/existing.png' }), { id: '2' }, { deployKey: 'x' }] })
  poller.sync()
  await tick(100000)
  assert.equal(state.calls.length, 0)
})

test('fills only the missing cover, retaining array/card identity and all other data', async t => {
  const pending = app(undefined, { appName: 'Original name', selected: true })
  const covered = app('2', { cover: '/existing.png' })
  const apps = [pending, covered]
  const { state, poller, tick } = fixture(t, { apps, fetchApp: async id => app(id, { cover: '/new.png', appName: 'Changed remotely', selected: false }) })
  poller.sync()
  assert.equal(COVER_POLL_INTERVAL_MS, 60000, 'cover checks must wait a full minute')
  await tick(COVER_POLL_INTERVAL_MS - 1)
  assert.equal(state.calls.length, 0)
  await tick(1)
  assert.strictEqual(state.apps, apps)
  assert.strictEqual(state.apps[0], pending)
  assert.strictEqual(state.apps[1], covered)
  assert.equal(pending.cover, '/new.png')
  assert.equal(pending.appName, 'Original name')
  assert.equal(pending.selected, true)
  assert.equal(covered.cover, '/existing.png')
  assert.equal(state.calls[0].id, '449363655861526528')
  await tick(100000)
  assert.equal(state.calls.length, 1, 'stop immediately after covers arrive')
})

test('deduplicates the same app across both sections and updates both cards', async t => {
  const mine = app('7'), featured = app(7)
  const { state, poller, tick } = fixture(t, { apps: [mine, featured] })
  poller.sync()
  poller.sync()
  await tick()
  assert.equal(state.calls.length, 1)
  assert.equal(mine.cover, '/ready.png')
  assert.equal(featured.cover, '/ready.png')
})

test('slow requests never overlap, even when sync is called repeatedly', async t => {
  const request = deferred()
  const { state, poller, tick } = fixture(t, { apps: [app()], fetchApp: () => request.promise })
  poller.sync()
  await tick()
  poller.sync()
  await tick(60000)
  assert.equal(state.calls.length, 1)
  request.resolve(undefined)
  await flush()
  await tick(COVER_POLL_INTERVAL_MS - 1)
  assert.equal(state.calls.length, 1)
  await tick(1)
  assert.equal(state.calls.length, 2)
})

test('late response cannot replace the page, restore a removed card, or affect a different deployment', async t => {
  const request = deferred()
  const { state, poller, tick } = fixture(t, { apps: [app('1')], fetchApp: () => request.promise })
  poller.sync()
  await tick()
  const newPage = [app('2'), app('1', { deployKey: 'new-deployment' })]
  state.apps = newPage
  poller.sync()
  request.resolve(app('1', { cover: '/old.png' }))
  await flush()
  assert.strictEqual(state.apps, newPage)
  assert.equal(newPage.length, 2)
  assert.ok(newPage.every(item => !item.cover))
})

test('late response does not overwrite a newer cover', async t => {
  const request = deferred(), card = app()
  const { poller, tick } = fixture(t, { apps: [card], fetchApp: () => request.promise })
  poller.sync()
  await tick()
  card.cover = '/newer.png'
  request.resolve(app(undefined, { cover: '/old.png' }))
  await flush()
  assert.equal(card.cover, '/newer.png')
})

test('wrong id or deployment in a response is ignored', async t => {
  let attempt = 0
  const card = app('1')
  const { poller, tick } = fixture(t, { apps: [card], fetchApp: async () => ++attempt === 1
    ? app('2', { cover: '/wrong.png' }) : app('1', { deployKey: 'wrong', cover: '/wrong.png' }) })
  poller.sync()
  await tick()
  await tick()
  assert.equal(card.cover, undefined)
})

test('network failures are quiet and do not prevent another card from updating', async t => {
  const cards = [app('1'), app('2')]
  const { state, poller, tick } = fixture(t, { apps: cards, fetchApp: async id => {
    if (id === '1') throw Error('network unavailable')
    return app(id, { cover: '/ok.png' })
  } })
  poller.sync()
  await tick()
  assert.equal(cards[0].cover, undefined)
  assert.equal(cards[1].cover, '/ok.png')
  await tick()
  assert.deepEqual(state.calls.map(call => call.id), ['1', '2', '1'])
})

test('attempt limit applies per application; a new page still gets its own budget', async t => {
  const { state, poller, tick } = fixture(t, { apps: [app('1')], maxAttempts: 2, fetchApp: async () => undefined })
  poller.sync()
  await tick()
  await tick()
  await tick(100000)
  poller.sync()
  await tick()
  assert.equal(state.calls.length, 2)
  state.apps = [app('2')]
  poller.sync()
  await tick()
  assert.deepEqual(state.calls.map(call => call.id), ['1', '1', '2'])
})

test('hidden pages pause without consuming attempts, and resume when visible', async t => {
  const { state, poller, tick } = fixture(t, { apps: [app()], maxAttempts: 1 })
  poller.sync()
  state.visible = false
  poller.sync()
  await tick(100000)
  assert.equal(state.calls.length, 0)
  state.visible = true
  poller.sync()
  await tick()
  assert.equal(state.calls.length, 1)
  assert.equal(state.apps[0].cover, '/ready.png')
})

test('hiding the page aborts in-flight requests and ignores their late result', async t => {
  const request = deferred(), card = app()
  const { state, poller, tick } = fixture(t, { apps: [card], fetchApp: () => request.promise })
  poller.sync()
  await tick()
  state.visible = false
  poller.sync()
  assert.equal(state.calls[0].signal.aborted, true)
  request.resolve(app(undefined, { cover: '/late.png' }))
  await flush()
  await tick(100000)
  assert.equal(card.cover, undefined)
  assert.equal(state.calls.length, 1)
})

test('unmount/dispose aborts pending work and never restarts a timer', async t => {
  const request = deferred(), card = app()
  const { state, poller, tick } = fixture(t, { apps: [card], fetchApp: () => request.promise })
  poller.sync()
  await tick()
  poller.dispose()
  assert.equal(state.calls[0].signal.aborted, true)
  request.resolve(app(undefined, { cover: '/late.png' }))
  await flush()
  poller.sync()
  await tick(100000)
  assert.equal(card.cover, undefined)
  assert.equal(state.calls.length, 1)
})

test('dispose before initial lists finish prevents the old mount-time timer leak', async t => {
  const { state, poller, tick } = fixture(t)
  poller.dispose()
  state.apps = [app()]
  poller.sync()
  await tick(100000)
  assert.equal(state.calls.length, 0)
})

test('removing pending cards cancels the scheduled refresh', async t => {
  const { state, poller, tick } = fixture(t, { apps: [app()] })
  poller.sync()
  state.apps = []
  poller.sync()
  await tick(100000)
  assert.equal(state.calls.length, 0)
})
