<script setup>
import { computed, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { adminRequest, downloadFile } from '@/api/client.js'
import ActionDialog from '@/components/ActionDialog.vue'
import AdminTitle from '@/components/AdminTitle.vue'
import DataTable from '@/components/DataTable.vue'
import FetchState from '@/components/FetchState.vue'
import NoticeEditor from '@/components/NoticeEditor.vue'
import PageControl from '@/components/PageControl.vue'
import StateBadge from '@/components/StateBadge.vue'
import { hasPermission } from '@/composables/auth.js'
import { useAction } from '@/composables/useAction.js'
import { useAdminList } from '@/composables/useAdminList.js'
import { useAsync } from '@/composables/useAsync.js'
import { formatDate, noticeCategories } from '@/domain/presentation.js'

const columns = [
  ['title', '标题'],
  ['category', '分类'],
  ['status', '状态'],
  ['publisher', '发布单位'],
  ['updatedAt', '更新时间'],
]
const list = useAdminList('/notices', { keyword: '', category: '', status: '' })
const { filters, result: rows, pending: loadingRows, error: listError, page, size } = list
list.reload()

const open = ref(false)
const mode = ref('view')
const selectedId = ref()
const confirmation = ref()
const file = ref()
const fileInput = ref()
const { busy, failure, success, output, run } = useAction()
const resource = computed(() => '/notices/' + encodeURIComponent(selectedId.value))
const details = useAsync((signal) => adminRequest(resource.value, { signal }))
const { pending: loadingDetail, error: detailError } = details
const selected = computed(() => {
  const detail = details.result.value
  return detail?.notice.id === selectedId.value ? detail : undefined
})
const isPublished = computed(() => selected.value?.notice.status === 'PUBLISHED')
const canWrite = computed(() => hasPermission('notices.write'))
const canWriteAttachments = computed(() => hasPermission('notices.attachments.write'))
const canPublish = computed(() => hasPermission('notices.publish'))

function resetDialog() {
  confirmation.value = undefined
  failure.value = ''
  success.value = ''
  file.value = undefined
}

function show(id) {
  resetDialog()
  selectedId.value = id
  mode.value = id ? 'view' : 'create'
  open.value = true
  if (id) details.reload()
}

function edit() {
  resetDialog()
  mode.value = 'edit'
}

async function refreshNotice() {
  await Promise.all([details.reload(), list.reload()])
}

async function save(body) {
  const creating = mode.value === 'create'
  if (
    await run(
      () =>
        adminRequest(creating ? '/notices' : resource.value, {
          method: creating ? 'POST' : 'PATCH',
          body,
        }),
      creating ? '草稿已创建' : '修改已保存',
    )
  ) {
    selectedId.value = output.value.id
    mode.value = 'view'
    await refreshNotice()
  }
}

function chooseFile(event) {
  file.value = event.target.files?.[0]
}

async function upload() {
  if (!file.value) return
  const body = new FormData()
  body.append('file', file.value)
  if (
    await run(
      () => adminRequest(resource.value + '/attachments', { method: 'POST', body }),
      '附件已上传',
    )
  ) {
    file.value = undefined
    if (fileInput.value) fileInput.value.value = ''
    await refreshNotice()
  }
}

function attachmentPath(item) {
  return resource.value + '/attachments/' + encodeURIComponent(item.id)
}

function download(item) {
  return run(async () => {
    const content = await adminRequest(attachmentPath(item), { binary: true })
    downloadFile(content, item.filename)
  })
}

function ask(message, label, action) {
  failure.value = ''
  confirmation.value = { message, label, action }
}

function confirmPublication(published) {
  ask(
    published
      ? '发布后，资讯和未下架的附件会立即在门户公开。'
      : '下架后，门户将隐藏该资讯及其全部附件。',
    published ? '确认发布' : '确认下架',
    () => adminRequest(resource.value + '/publication', { method: 'PUT', body: { published } }),
  )
}

function confirmRemoval(item) {
  ask(`移除「${item.filename}」？`, '确认移除', () =>
    adminRequest(attachmentPath(item), { method: 'DELETE' }),
  )
}

function confirmAttachmentWithdrawal(item) {
  ask(`下架「${item.filename}」后，公众将无法下载，后台仍可查阅。`, '确认下架附件', () =>
    adminRequest(attachmentPath(item) + '/withdrawal', { method: 'PUT' }),
  )
}

async function applyConfirmation() {
  const operation = confirmation.value
  if (await run(operation.action, operation.label.replace('确认', '') + '成功')) {
    confirmation.value = undefined
    await refreshNotice()
  }
}

function bytes(value) {
  if (value >= 1024 * 1024) return (value / (1024 * 1024)).toFixed(1) + ' MB'
  if (value >= 1024) return (value / 1024).toFixed(1) + ' KB'
  return value + ' B'
}
</script>

<template>
  <AdminTitle title="资讯管理" summary="维护门户政策、考务通知、报考须知和常见答疑。">
    <button v-if="canWrite" class="button primary" type="button" @click="show()">
      <Plus aria-hidden="true" />新增资讯
    </button>
  </AdminTitle>

  <form class="filter-bar panel" @submit.prevent="list.search">
    <label>标题<input v-model="filters.keyword" maxlength="100" placeholder="按标题查找" /></label>
    <label
      >分类
      <select v-model="filters.category">
        <option value="">全部分类</option>
        <option v-for="(name, category) in noticeCategories" :key="category" :value="category">
          {{ name }}
        </option>
      </select>
    </label>
    <label
      >状态
      <select v-model="filters.status">
        <option value="">全部状态</option>
        <option value="DRAFT">草稿</option>
        <option value="PUBLISHED">已发布</option>
        <option value="WITHDRAWN">已下架</option>
      </select>
    </label>
    <button class="button primary">查询</button>
    <button class="button outline" type="button" @click="list.reset">重置</button>
  </form>

  <section class="panel admin-panel" aria-label="资讯列表">
    <FetchState
      :pending="loadingRows"
      :error="listError"
      :empty="!rows?.items.length"
      @retry="list.reload"
    >
      <DataTable :columns="columns" :rows="rows.items">
        <template #category="{ row }">{{ noticeCategories[row.category] }}</template>
        <template #actions="{ row }">
          <button type="button" class="link-button" @click="show(row.id)">查看</button>
        </template>
      </DataTable>
    </FetchState>
    <PageControl
      v-if="rows?.total"
      :page="page"
      :size="size"
      :total="rows.total"
      sizes
      @change="page = $event"
      @resize="list.resize"
    />
  </section>

  <ActionDialog
    v-model="open"
    :title="mode === 'create' ? '新增资讯' : mode === 'edit' ? '编辑资讯' : '资讯详情'"
    :busy="busy"
    :error="failure"
  >
    <NoticeEditor
      v-if="mode === 'create' || mode === 'edit'"
      :detail="mode === 'edit' ? selected : undefined"
      :busy="busy"
      @save="save"
      @cancel="mode === 'create' ? (open = false) : (mode = 'view')"
    />
    <FetchState
      v-else
      :pending="loadingDetail"
      :error="detailError"
      :empty="!selected"
      @retry="details.reload"
    >
      <article class="notice-detail">
        <div class="notice-meta">
          <StateBadge :value="selected.notice.status" />
          <span>{{ noticeCategories[selected.notice.category] }}</span>
          <span v-if="selected.notice.pinned">列表置顶</span>
          <span v-if="selected.notice.featured">首页焦点</span>
        </div>
        <h2>{{ selected.notice.title }}</h2>
        <p class="notice-meta">
          <span>{{ selected.notice.publisher }}</span>
          <span v-if="selected.notice.publishedAt">
            发布时间 {{ formatDate(selected.notice.publishedAt, true) }}
          </span>
        </p>
        <p class="notice-summary">{{ selected.notice.summary }}</p>
        <p class="notice-body">{{ selected.body }}</p>

        <section class="notice-files" aria-label="资讯附件">
          <h3>附件</h3>
          <p v-if="!selected.attachments.length" class="notice-meta">暂无附件</p>
          <ul v-else>
            <li v-for="item in selected.attachments" :key="item.id">
              <button type="button" class="link-button" :disabled="busy" @click="download(item)">
                {{ item.filename }}
              </button>
              <small>{{ bytes(item.byteCount) }}</small>
              <StateBadge v-if="item.withdrawnAt" value="WITHDRAWN" />
              <button
                v-if="canWriteAttachments && !item.withdrawnAt"
                type="button"
                class="link-button"
                :disabled="busy"
                @click="confirmAttachmentWithdrawal(item)"
              >
                下架附件
              </button>
              <button
                v-if="canWriteAttachments && !isPublished"
                type="button"
                class="link-button"
                :disabled="busy"
                @click="confirmRemoval(item)"
              >
                移除
              </button>
            </li>
          </ul>
          <form v-if="canWriteAttachments" class="notice-upload" @submit.prevent="upload">
            <label>
              <span>PDF、PNG 或 JPEG，单文件最多 10 MB</span>
              <input
                ref="fileInput"
                type="file"
                accept=".pdf,.png,.jpg,.jpeg"
                :disabled="busy"
                @change="chooseFile"
              />
            </label>
            <button class="button outline" :disabled="!file || busy">上传附件</button>
          </form>
        </section>

        <p v-if="success" class="inline-success" role="status">{{ success }}</p>
        <div v-if="confirmation" class="notice-confirm">
          <p>{{ confirmation.message }}</p>
          <div class="dialog-actions">
            <button
              type="button"
              class="button outline"
              :disabled="busy"
              @click="confirmation = undefined"
            >
              取消
            </button>
            <button
              type="button"
              class="button primary"
              :disabled="busy"
              @click="applyConfirmation"
            >
              {{ confirmation.label }}
            </button>
          </div>
        </div>
        <div v-else class="dialog-actions">
          <button
            v-if="canWrite"
            type="button"
            class="button outline"
            :disabled="busy"
            @click="edit"
          >
            编辑
          </button>
          <button
            v-if="canPublish"
            type="button"
            class="button primary"
            :disabled="busy"
            @click="confirmPublication(!isPublished)"
          >
            {{
              isPublished ? '下架' : selected.notice.status === 'WITHDRAWN' ? '重新发布' : '发布'
            }}
          </button>
        </div>
      </article>
    </FetchState>
  </ActionDialog>
</template>

<style scoped>
.notice-detail {
  display: grid;
  gap: 0.85rem;
  overflow-wrap: anywhere;
}

.notice-detail :is(h2, h3, p) {
  margin: 0;
}

.notice-meta,
.notice-files small,
.notice-upload label span {
  color: var(--muted);
}

.notice-meta,
.notice-files li,
.notice-upload {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
}

.notice-summary,
.notice-confirm {
  background: var(--wash, #f3f6fb);
  padding: 0.85rem 1rem;
}

.notice-body {
  white-space: pre-wrap;
  line-height: 1.75;
  max-height: 18rem;
  overflow: auto;
}

.notice-files {
  border-top: 1px solid var(--line);
  padding-top: 0.85rem;
}

.notice-files ul {
  display: grid;
  gap: 0.6rem;
  padding: 0;
  list-style: none;
}

.notice-upload label {
  display: grid;
  gap: 0.35rem;
  margin-top: 0.6rem;
}
</style>
