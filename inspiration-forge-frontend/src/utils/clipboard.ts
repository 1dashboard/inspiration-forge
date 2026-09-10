/**
 * Prefer the secure-context Clipboard API. The selected, visible input is also
 * usable on HTTP and stays inside the modal's focus trap for the legacy fallback.
 * If both APIs fail, leave the URL selected for a manual copy.
 */
export async function copyInputValue(input: HTMLInputElement): Promise<boolean> {
  if (!input.value) return false

  if (window.isSecureContext && typeof navigator.clipboard?.writeText === 'function') {
    try {
      await navigator.clipboard.writeText(input.value)
      return true
    } catch {
      // Permission can be denied even on HTTPS; try the user-gesture fallback.
    }
  }

  input.focus({ preventScroll: true })
  input.select()
  input.setSelectionRange(0, input.value.length)

  try {
    // Deprecated, but needed for existing HTTP deployments without Clipboard API.
    return document.execCommand('copy')
  } catch {
    return false
  }
}
