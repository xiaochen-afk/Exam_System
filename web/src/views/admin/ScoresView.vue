<script setup>
import { computed, ref, shallowRef, watch } from 'vue'
import { Upload, Download, Check } from '@element-plus/icons-vue'
import { adminRequest, downloadFile } from '@/api/client.js'
import { useAdminList } from '@/composables/useAdminList.js'
import { useAction } from '@/composables/useAction.js'
import { hasPermission } from '@/composables/auth.js'
import AdminTitle from '@/components/AdminTitle.vue'
import SessionPicker from '@/components/SessionPicker.vue'
import ActionDialog from '@/components/ActionDialog.vue'
import DataTable from '@/components/DataTable.vue'
import FetchState from '@/components/FetchState.vue'
import PageControl from '@/components/PageControl.vue'

const sessionId = ref('')
const { result, error, pending, reload, page, size, search, resize } = useAdminList(
  '/scores',
  {},
  () => ({ sessionId: sessionId.value }),
  () => !!sessionId.value,
)
const { busy, failure, success, output, run } = useAction()
const action = ref('')
const selected = shallowRef()
const score = ref()
const file = shallowRef()
const confirmation = ref('')
const importReport = shallowRef()
const capabilities = computed(() => result.value?.capabilities ?? [])
const stats = computed(() => result.value?.summary)
const columns = [
  ['registrationNumber', '报名编号'],
  ['candidateName', '考生姓名'],
  ['siteName', '报考考点'],
  ['score', '分数'],
  ['scoreStatus', '成绩状态'],
  ['updatedAt', '更新时间'],
]
const titles = { score: '录入成绩', import: '导入成绩', publish: '发布成绩' }
watch(sessionId, () => {
  importReport.value = undefined
  failure.value = ''
  success.value = ''
  search()
})

function open(key, row) {
  failure.value = ''
  action.value = key
  selected.value = row
  score.value = row?.score ?? undefined
  confirmation.value = ''
  file.value = undefined
}

function sessionPath() {
  return '/exam-sessions/' + encodeURIComponent(sessionId.value)
}

async function templateDownload() {
  await run(async () => {
    const blob = await adminRequest(sessionPath() + '/score-import-template', { binary: true })
    downloadFile(blob, 'score-import-template.csv')
  })
}

async function submit() {
  const operation = action.value
  const saved = await run(() => {
    if (operation === 'score')
      return adminRequest('/registrations/' + encodeURIComponent(selected.value.id) + '/score', {
        method: 'PUT',
        body: { score: Number(score.value) },
      })
    if (operation === 'import') {
      const body = new FormData()
      body.set('file', file.value)
      return adminRequest(sessionPath() + '/score-imports', { method: 'POST', body })
    }
    return adminRequest(sessionPath() + '/score-publication', {
      method: 'PUT',
      body: { published: true },
    })
  }, { score: '已保存草稿', import: '成绩导入已处理', publish: '成绩已发布' }[operation])
  if (saved) {
    if (operation === 'import') importReport.value = output.value
    action.value = ''
    await reload()
  }
}
</script>

<template>
  <AdminTitle title="成绩管理" summary="按场次录入或导入分数，确认后发布成绩" />
  <SessionPicker v-model="sessionId" context="scores" />
  <section class="panel admin-panel">
    <p v-if="!sessionId" class="feedback">选择考试场次后管理成绩</p>
    <FetchState v-else :error="error" :pending="pending" @retry="reload">
      <div v-if="stats" class="workbench-summary">
        <div>
          <h2>成绩进度</h2>
          <p>
            已录入 {{ stats.recorded }} / {{ stats.total }} 人 ·
            {{ stats.published ? '已发布' : '未发布' }}
          </p>
        </div>
        <div class="inline-actions">
          <template v-if="hasPermission('scores.import') && capabilities.includes('IMPORT')">
            <button class="button outline" :disabled="busy" @click="templateDownload">
              <Download aria-hidden="true" />下载模板
            </button>
            <button class="button outline" :disabled="busy" @click="open('import')">
              <Upload aria-hidden="true" />导入成绩
            </button>
          </template>
          <button
            v-if="hasPermission('scores.publish') && capabilities.includes('PUBLISH')"
            class="button primary"
            :disabled="busy || stats.recorded === 0 || stats.recorded !== stats.total"
            @click="open('publish')"
          >
            <Check aria-hidden="true" />发布成绩
          </button>
        </div>
      </div>
      <p v-if="success" class="inline-success" role="status">{{ success }}</p>
      <p v-if="failure && !action" class="inline-error" role="alert">{{ failure }}</p>
      <div v-if="importReport" class="import-report">
        <h3>导入结果</h3>
        <p>成功 {{ importReport.applied }} 条，失败 {{ importReport.failed }} 条。</p>
        <ul v-if="importReport.errors.length">
          <li v-for="entry in importReport.errors" :key="entry.row">
            第 {{ entry.row }} 行 · {{ entry.message }}
          </li>
        </ul>
      </div>
      <p v-if="!result?.items.length" class="feedback">该场次暂无待处理的考生</p>
      <template v-else>
        <DataTable :columns="columns" :rows="result.items"
          ><template
            v-if="hasPermission('scores.write') && capabilities.includes('EDIT')"
            #actions="{ row }"
            ><button class="link-button" @click="open('score', row)">录入</button></template
          ></DataTable
        >
        <PageControl
          :page="page"
          :size="size"
          :total="result.total"
          sizes
          @change="page = $event"
          @resize="resize"
        />
      </template>
    </FetchState>
  </section>
  <ActionDialog
    :model-value="!!action"
    :title="titles[action]"
    :busy="busy"
    :error="failure"
    @update:model-value="action = ''"
  >
    <form class="editor-form" @submit.prevent="submit">
      <template v-if="action === 'score'">
        <p class="editor-hint">报名编号 {{ selected.registrationNumber }}</p>
        <label
          >分数<input v-model.number="score" type="number" min="0" max="100" step="0.01" required
        /></label>
      </template>
      <template v-else-if="action === 'import'">
        <p class="editor-hint">请使用下载的 CSV 模板。导入成功的记录将保存为草稿。</p>
        <label class="full-width"
          >成绩文件<input
            type="file"
            accept=".csv,text/csv"
            required
            @change="file = $event.target.files[0]"
        /></label>
      </template>
      <template v-else>
        <p class="editor-hint">发布后，考生将可查询本人分数。请核对本次考试成绩。</p>
        <label class="full-width"
          >输入 CONFIRM 确认发布<input v-model="confirmation" autocomplete="off" required
        /></label>
      </template>
      <div class="dialog-actions">
        <button type="button" class="button outline" :disabled="busy" @click="action = ''">
          取消</button
        ><button
          class="button primary"
          :disabled="
            busy ||
            (action === 'publish' && confirmation !== 'CONFIRM') ||
            (action === 'import' && !file)
          "
        >
          {{ busy ? '处理中…' : '确认' }}
        </button>
      </div>
    </form>
  </ActionDialog>
</template>
