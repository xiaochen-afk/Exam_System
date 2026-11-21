import { reactive, ref, watch } from 'vue'
import { adminRequest } from '@/api/client.js'
import { useAsync } from './useAsync.js'

export function useAdminList(path, initialFilters, extraQuery = () => ({}), ready = () => true) {
  const page = ref(1)
  const size = ref(10)
  const filters = reactive({ ...initialFilters })
  let applied = { ...initialFilters }
  const loader = useAsync((signal) =>
    ready()
      ? adminRequest(path, {
          signal,
          query: { ...applied, ...extraQuery(), page: page.value, size: size.value },
        })
      : undefined,
  )
  watch([page, size], loader.reload)

  function search() {
    applied = Object.fromEntries(
      Object.entries(filters).map(([key, value]) => [
        key,
        typeof value === 'string' ? value.trim() : value,
      ]),
    )
    if (page.value === 1) loader.reload()
    else page.value = 1
  }

  function reset() {
    Object.assign(filters, initialFilters)
    search()
  }

  function resize(value) {
    page.value = 1
    size.value = value
  }

  return { ...loader, page, size, filters, search, reset, resize }
}

export async function loadOptions(kinds, signal, query = {}) {
  const entries = await Promise.all(
    [...new Set(kinds)].map(async (kind) => [
      kind,
      await adminRequest('/options', { signal, query: { kind, ...query } }),
    ]),
  )
  return Object.fromEntries(entries)
}
