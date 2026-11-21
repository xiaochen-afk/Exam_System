import { ref, shallowRef } from 'vue'

export function useAction() {
  const busy = ref(false)
  const failure = ref('')
  const success = ref('')
  const output = shallowRef()

  async function run(action, message = '') {
    if (busy.value) return false
    busy.value = true
    failure.value = ''
    success.value = ''
    try {
      output.value = await action()
      success.value = message
      return true
    } catch (error) {
      failure.value = error.message
      return false
    } finally {
      busy.value = false
    }
  }

  return { busy, failure, success, output, run }
}
