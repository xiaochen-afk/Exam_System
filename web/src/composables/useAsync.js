import { onScopeDispose, ref, shallowRef } from 'vue'

export function useAsync(loader) {
  const result = shallowRef()
  const pending = ref(false)
  const error = shallowRef()
  let controller

  async function reload() {
    controller?.abort()
    const current = new AbortController()
    controller = current
    pending.value = true
    error.value = undefined
    try {
      const value = await loader(current.signal)
      if (!current.signal.aborted) result.value = value
    } catch (failure) {
      if (!current.signal.aborted) error.value = failure
    } finally {
      if (!current.signal.aborted) pending.value = false
    }
  }

  onScopeDispose(() => controller?.abort())
  return { result, pending, error, reload }
}
