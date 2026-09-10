type CoverApp = {
  id?: string | number
  deployKey?: string
  cover?: string
}

export const COVER_POLL_INTERVAL_MS = 60_000

type CoverPollingOptions<T extends CoverApp> = {
  getApps: () => T[]
  fetchApp: (id: NonNullable<T['id']>, signal: AbortSignal) => Promise<CoverApp | undefined>
  isVisible?: () => boolean
  intervalMs?: number
  maxAttempts?: number
}

/** Fill missing covers without replacing lists, cards, pagination or selection state. */
export function createCoverPoller<T extends CoverApp>({
  getApps,
  fetchApp,
  isVisible = () => true,
  intervalMs = COVER_POLL_INTERVAL_MS,
  maxAttempts = 20,
}: CoverPollingOptions<T>) {
  let timer: ReturnType<typeof setTimeout> | undefined
  let activeRequest: AbortController | undefined
  let disposed = false
  const attempts = new Map<string, number>()
  const appKey = (app: CoverApp) => JSON.stringify([String(app.id), app.deployKey])

  const pendingApps = () => {
    const pending = new Map<string, { id: NonNullable<T['id']>; key: string }>()
    for (const app of getApps()) {
      if (app.id == null || !app.deployKey || app.cover) continue
      const key = appKey(app)
      if ((attempts.get(key) || 0) < maxAttempts) {
        // The same application may appear in both "my apps" and "featured".
        pending.set(key, { id: app.id as NonNullable<T['id']>, key })
      }
    }
    return [...pending.values()]
  }

  const clearTimer = () => {
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }

  const sync = () => {
    if (disposed) return
    if (!isVisible()) {
      clearTimer()
      activeRequest?.abort()
      return
    }
    if (!pendingApps().length) {
      clearTimer()
      return
    }
    if (timer === undefined && !activeRequest) {
      timer = setTimeout(() => { void poll() }, intervalMs)
    }
  }

  const poll = async () => {
    timer = undefined
    if (disposed || !isVisible()) return
    const pending = pendingApps()
    if (!pending.length) return
    const controller = new AbortController()
    activeRequest = controller

    try {
      await Promise.all(pending.map(async ({ id, key }) => {
        attempts.set(key, (attempts.get(key) || 0) + 1)
        try {
          const latest = await fetchApp(id, controller.signal)
          if (disposed || controller.signal.aborted || !latest?.cover || appKey(latest) !== key) return
          // Re-read the CURRENT page after awaiting. Never reinsert deleted/off-page
          // cards or overwrite a newer cover/deployment with a late response.
          for (const app of getApps()) {
            if (appKey(app) === key && !app.cover) app.cover = latest.cover
          }
        } catch {
          // Cover generation is best-effort; background failures must not disrupt
          // editing, show a loading skeleton or surface repeated error messages.
        }
      }))
    } finally {
      activeRequest = undefined
      // Schedule AFTER completion so slow requests cannot overlap.
      sync()
    }
  }

  return {
    sync,
    dispose: () => {
      disposed = true
      clearTimer()
      activeRequest?.abort()
    },
  }
}
