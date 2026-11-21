<script setup>
import { reactive } from 'vue'
import { noticeCategories } from '@/domain/presentation.js'

const props = defineProps({ detail: Object, busy: Boolean })
const emit = defineEmits(['save', 'cancel'])
const notice = props.detail?.notice
const form = reactive({
  category: notice?.category ?? 'NOTICE',
  title: notice?.title ?? '',
  summary: notice?.summary ?? '',
  body: props.detail?.body ?? '',
  publisher: notice?.publisher ?? '',
  pinned: notice?.pinned ?? false,
  featured: notice?.featured ?? false,
})

function submit() {
  emit('save', {
    ...form,
    title: form.title.trim(),
    summary: form.summary.trim(),
    body: form.body.trim(),
    publisher: form.publisher.trim(),
  })
}
</script>

<template>
  <form class="editor-form notice-editor" @submit.prevent="submit">
    <label class="full-width">标题<input v-model="form.title" maxlength="200" required /></label>
    <label>
      分类
      <select v-model="form.category" required>
        <option v-for="(label, key) in noticeCategories" :key="key" :value="key">
          {{ label }}
        </option>
      </select>
    </label>
    <label>发布单位<input v-model="form.publisher" maxlength="100" required /></label>
    <label class="full-width">
      摘要
      <textarea v-model="form.summary" rows="2" maxlength="500" required />
    </label>
    <label class="full-width">
      正文
      <textarea v-model="form.body" rows="8" maxlength="16000" required />
    </label>
    <div class="notice-editor-flags full-width">
      <label><input v-model="form.pinned" type="checkbox" />列表置顶</label>
      <label><input v-model="form.featured" type="checkbox" />首页焦点</label>
    </div>
    <div class="dialog-actions full-width">
      <button type="button" class="button outline" :disabled="busy" @click="emit('cancel')">
        取消
      </button>
      <button type="submit" class="button primary" :disabled="busy">
        {{ busy ? '保存中…' : notice ? '保存修改' : '创建草稿' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.notice-editor-flags,
.notice-editor-flags label {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.notice-editor-flags {
  flex-wrap: wrap;
  column-gap: 1.5rem;
}

.notice-editor-flags input {
  width: auto;
  min-height: 0;
}
</style>
