<script setup>
import { computed, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import AdminTitle from '@/components/AdminTitle.vue'
import ActionDialog from '@/components/ActionDialog.vue'
import ResourceEditor from '@/components/ResourceEditor.vue'
import StaffAccountEditor from '@/components/StaffAccountEditor.vue'
import FetchState from '@/components/FetchState.vue'
import DataTable from '@/components/DataTable.vue'
import PageControl from '@/components/PageControl.vue'
import { adminRequest, demoMode } from '@/api/client.js'
import { hasPermission } from '@/composables/auth.js'
import { useAsync } from '@/composables/useAsync.js'
import { useAction } from '@/composables/useAction.js'
import { resources, validateSession } from '@/domain/admin.js'

const route = useRoute()
const section = ref(route.meta.resource)
const resource = computed(() => resources[section.value])
const { busy, failure, success, run } = useAction()
const editorOpen = ref(false)
const publishing = ref(false)
const changingRegistration = ref(false)
const selected = shallowRef()
const keyword = ref('')
const status = ref('')
const page = ref(1)
const size = ref(10)
const applied = shallowRef({})
const { result, pending, error, reload } = useAsync((signal) =>
  adminRequest(resource.value.path, {
    signal,
    query: { ...applied.value, page: page.value, size: size.value },
  }),
)
watch([section, page, size, applied], reload, { immediate: true })
watch(() => route.meta.resource, selectSection)
const canEdit = computed(
  () =>
    resource.value.edit &&
    (hasPermission(resource.value.edit) ||
      (section.value === 'sites' && hasPermission('sites.manage'))),
)

const fields = computed(() => {
  if (section.value === 'sites' && !hasPermission('sites.manage')) {
    return resource.value.fields.filter((field) => field.key === 'address')
  }
  return resource.value.fields
})

function resize(value) {
  page.value = 1
  size.value = value
}

function selectSection(value) {
  editorOpen.value = false
  publishing.value = false
  changingRegistration.value = false
  selected.value = undefined
  success.value = ''
  failure.value = ''
  section.value = value
  clear()
}
function search() {
  page.value = 1
  applied.value = { keyword: keyword.value.trim(), status: status.value }
}
function clear() {
  keyword.value = ''
  status.value = ''
  search()
}
function openEditor(row) {
  selected.value =
    row && section.value === 'sessions'
      ? {
          ...row,
          projectName: `${row.subjectName} / ${row.applicationCode || '独立项目'} #${row.projectId}`,
        }
      : row
  failure.value = ''
  editorOpen.value = true
}

async function save(body) {
  const row = selected.value
  if (
    Object.values(body).some((value) => value === '') &&
    fields.value.some(
      (field) => !field.optional && Object.hasOwn(body, field.key) && body[field.key] === '',
    )
  ) {
    failure.value = '请填写所有必填字段'
    return
  }
  if (section.value === 'sessions') {
    failure.value = validateSession(body, row)
    if (failure.value) return
  }
  if (section.value === 'sites' && Object.hasOwn(body, 'schoolId') && !body.schoolId) {
    body.schoolId = null
    body.clearSchool = true
  }
  const path = resource.value.path + (row ? '/' + encodeURIComponent(row.id) : '')
  if (await run(() => adminRequest(path, { method: row ? 'PATCH' : 'POST', body }), '已保存')) {
    editorOpen.value = false
    await reload()
  }
}

function confirmRegistration(row) {
  selected.value = row
  failure.value = ''
  changingRegistration.value = true
}

async function changeRegistration() {
  const row = selected.value
  if (
    await run(
      () =>
        adminRequest(
          `/exam-projects/${encodeURIComponent(row.projectId)}/sites/${encodeURIComponent(row.siteId)}`,
          { method: 'PUT', body: { registrationOpen: !row.registrationOpen } },
        ),
      row.registrationOpen ? '该项目考点已暂停新报名' : '该项目考点已开放报名',
    )
  ) {
    changingRegistration.value = false
    await reload()
  }
}

function confirmPublish(row) {
  selected.value = row
  failure.value = ''
  publishing.value = true
}
async function publish() {
  if (
    await run(
      () =>
        adminRequest('/exam-sessions/' + encodeURIComponent(selected.value.id), {
          method: 'PATCH',
          body: { status: 'PUBLISHED' },
        }),
      '场次已发布',
    )
  ) {
    publishing.value = false
    await reload()
  }
}
</script>

<template>
  <AdminTitle :title="$route.meta.title" :summary="resource.summary">
    <button
      v-if="resource.create && hasPermission(resource.create)"
      class="button primary"
      @click="openEditor()"
    >
      <Plus aria-hidden="true" />新增{{ resource.singular }}
    </button>
  </AdminTitle>
  <div
    v-if="$route.meta.resource === 'sites' && hasPermission('rooms.read')"
    class="category-links"
    role="group"
    aria-label="场地类别"
  >
    <button
      class="button outline"
      :aria-pressed="section === 'sites'"
      @click="selectSection('sites')"
    >
      考点
    </button>
    <button
      class="button outline"
      :aria-pressed="section === 'rooms'"
      @click="selectSection('rooms')"
    >
      考场
    </button>
  </div>
  <form class="filter-bar panel" @submit.prevent="search">
    <label>关键词<input v-model="keyword" maxlength="100" placeholder="输入关键词" /></label>
    <label v-if="resource.fields && resource.filterStatus !== false"
      >状态<select v-model="status">
        <option value="">全部状态</option>
        <template v-if="section === 'sessions'"
          ><option value="DRAFT">草稿</option>
          <option value="PUBLISHED">已发布</option>
          <option value="COMPLETED">已结束</option></template
        >
        <template v-else
          ><option value="ACTIVE">启用</option>
          <option value="INACTIVE">停用</option></template
        >
      </select></label
    >
    <button class="button primary">查询</button
    ><button type="button" class="button outline" @click="clear">重置</button>
  </form>
  <p v-if="success" class="inline-success" role="status">{{ success }}</p>
  <section class="admin-panel panel">
    <FetchState :error="error" :pending="pending" :empty="!result?.items.length" @retry="reload">
      <DataTable :columns="resource.columns" :rows="result.items">
        <template
          v-if="canEdit || (resource.publish && hasPermission(resource.publish))"
          #actions="{ row }"
        >
          <button
            v-if="canEdit && row.capabilities?.includes('EDIT')"
            class="link-button"
            @click="openEditor(row)"
          >
            编辑
          </button>
          <button
            v-if="section === 'sessions' && row.capabilities?.includes('REGISTRATION_OPEN')"
            class="link-button"
            @click="confirmRegistration(row)"
          >
            {{ row.registrationOpen ? '暂停报名' : '开放报名' }}
          </button>
          <button
            v-if="
              resource.publish &&
              hasPermission(resource.publish) &&
              row.capabilities?.includes('PUBLISH')
            "
            class="link-button"
            @click="confirmPublish(row)"
          >
            发布
          </button>
        </template>
      </DataTable>
      <PageControl
        :page="page"
        :size="size"
        :total="result.total"
        sizes
        @change="page = $event"
        @resize="resize"
      />
    </FetchState>
  </section>
  <ActionDialog
    v-model="editorOpen"
    :title="(selected ? '编辑' : '新增') + resource.singular"
    :busy="busy"
    :error="failure"
  >
    <StaffAccountEditor
      v-if="section === 'users' && !demoMode"
      :key="selected?.id ?? 'new'"
      :row="selected"
      :busy="busy"
      @save="save"
      @cancel="editorOpen = false"
    />
    <ResourceEditor
      v-else
      :key="selected?.id ?? 'new'"
      :fields="fields"
      :row="selected"
      :busy="busy"
      :resource="section"
      @save="save"
      @cancel="editorOpen = false"
    />
  </ActionDialog>
  <ActionDialog v-model="publishing" title="发布考试场次" :busy="busy" :error="failure">
    <p>确认发布「{{ selected?.name }}」？发布后，场次将出现在公开门户。</p>
    <div class="dialog-actions">
      <button class="button outline" :disabled="busy" @click="publishing = false">取消</button
      ><button class="button primary" :disabled="busy" @click="publish">确认发布</button>
    </div>
  </ActionDialog>
  <ActionDialog
    v-model="changingRegistration"
    :title="selected?.registrationOpen ? '暂停考点报名' : '开放考点报名'"
    :busy="busy"
    :error="failure"
  >
    <p>
      确认{{ selected?.registrationOpen ? '暂停' : '开放' }}「{{
        selected?.siteName
      }}」的该项目报名？此设置影响同一项目在该考点的全部场次，已有报名将保留。
    </p>
    <div class="dialog-actions">
      <button class="button outline" :disabled="busy" @click="changingRegistration = false">
        取消
      </button>
      <button class="button primary" :disabled="busy" @click="changeRegistration">
        确认{{ selected?.registrationOpen ? '暂停' : '开放' }}
      </button>
    </div>
  </ActionDialog>
</template>
