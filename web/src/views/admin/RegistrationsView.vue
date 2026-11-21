<script setup>
import { User, School, Avatar, Search, RefreshRight, Tickets } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { adminRequest } from '@/api/client.js'
import { useAsync } from '@/composables/useAsync.js'
import { useAdminList, loadOptions } from '@/composables/useAdminList.js'
import { formatDate, states } from '@/domain/presentation.js'
import AdminTitle from '@/components/AdminTitle.vue'
import DataTable from '@/components/DataTable.vue'
import FetchState from '@/components/FetchState.vue'
import PageControl from '@/components/PageControl.vue'
import ActionDialog from '@/components/ActionDialog.vue'

const filterDefaults = {
  sessionId: '',
  siteId: '',
  candidateType: '',
  registrationNumber: '',
  status: '',
}
const { result, pending, error, reload, page, size, filters, search, reset, resize } = useAdminList(
  '/registrations',
  filterDefaults,
)
const {
  result: options,
  error: optionsError,
  reload: reloadOptions,
} = useAsync((signal) => loadOptions(['sessions', 'sites'], signal, { context: 'registrations' }))
const selectedId = ref('')
const detailOpen = ref(false)
const {
  result: detail,
  pending: detailPending,
  error: detailError,
  reload: reloadDetail,
} = useAsync((signal) =>
  adminRequest('/registrations/' + encodeURIComponent(selectedId.value), { signal }),
)
const columns = [
  ['sequence', '序号'],
  ['registrationNumber', '报名编号'],
  ['candidateName', '考生姓名'],
  ['candidateType', '考生类型'],
  ['schoolName', '在读学校'],
  ['sessionName', '考试项目'],
  ['subjectName', '考试科目'],
  ['siteName', '报考考点'],
  ['submittedAt', '报名时间'],
  ['status', '报名状态'],
]
const metrics = [
  ['total', '报名总数', User],
  ['student', '在校考生', School],
  ['social', '社会考生', Avatar],
  ['assigned', '已分配座位', Tickets],
]
reloadOptions()
reload()

function showDetail(row) {
  selectedId.value = row.id
  detailOpen.value = true
  reloadDetail()
}
</script>

<template>
  <AdminTitle title="报名管理" summary="查询报名名单、考点归属与排考进度" />
  <div v-if="optionsError" class="inline-error" role="alert">
    {{ optionsError.message }}
    <button class="link-button" @click="reloadOptions">重试加载筛选项</button>
  </div>
  <form class="filter-bar panel registration-filters" @submit.prevent="search">
    <label
      >考试项目<select v-model="filters.sessionId">
        <option value="">全部考试项目</option>
        <option v-for="item in options?.sessions" :key="item.id" :value="item.id">
          {{ item.name }}
        </option>
      </select></label
    >
    <label
      >报考考点<select v-model="filters.siteId">
        <option value="">全部考点</option>
        <option v-for="item in options?.sites" :key="item.id" :value="item.id">
          {{ item.name }}
        </option>
      </select></label
    >
    <label
      >报名状态<select v-model="filters.status">
        <option value="">全部状态</option>
        <option value="REGISTERED">报名成功</option>
      </select></label
    >
    <label
      >报名编号<input
        v-model="filters.registrationNumber"
        maxlength="80"
        placeholder="输入报名编号"
    /></label>
    <label
      >考生类型<select v-model="filters.candidateType">
        <option value="">全部类型</option>
        <option value="STUDENT">在校考生</option>
        <option value="SOCIAL">社会考生</option>
      </select></label
    >
    <div class="inline-actions">
      <button class="button primary"><Search aria-hidden="true" />查询</button
      ><button type="button" class="button outline" @click="reset">
        <RefreshRight aria-hidden="true" />重置
      </button>
    </div>
  </form>
  <div v-if="result && !pending && !error" class="panel metrics-wrap">
    <div class="metrics-grid">
      <article v-for="([key, title, icon], index) in metrics" :key="key" :data-color="index">
        <span class="metric-icon"><component :is="icon" aria-hidden="true" /></span>
        <div>
          <span>{{ title }}</span
          ><strong>{{ result.summary[key].toLocaleString('zh-CN') }} <small>人</small></strong>
        </div>
      </article>
    </div>
  </div>
  <section class="panel admin-panel">
    <div class="table-toolbar">
      <h2>报名名单</h2>
      <span v-if="result && !pending && !error" class="muted"
        >共 {{ result.total.toLocaleString('zh-CN') }} 条记录</span
      >
    </div>
    <FetchState :error="error" :pending="pending" :empty="!result?.items.length" @retry="reload">
      <DataTable :rows="result.items" :columns="columns">
        <template #sequence="{ row }">{{
          (page - 1) * size + result.items.indexOf(row) + 1
        }}</template>
        <template #actions="{ row }"
          ><button class="link-button" @click="showDetail(row)">查看</button></template
        >
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
  <ActionDialog v-model="detailOpen" title="报名详情">
    <FetchState
      :pending="detailPending"
      :error="detailError"
      :empty="!detail"
      @retry="reloadDetail"
    >
      <dl class="detail-grid">
        <div>
          <dt>报名编号</dt>
          <dd>{{ detail.registrationNumber }}</dd>
        </div>
        <div>
          <dt>考生姓名</dt>
          <dd>{{ detail.candidateName }}</dd>
        </div>
        <div>
          <dt>考生类型</dt>
          <dd>{{ states[detail.candidateType] }}</dd>
        </div>
        <div>
          <dt>在读学校</dt>
          <dd>{{ detail.schoolName ?? '—' }}</dd>
        </div>
        <div>
          <dt>考试与科目</dt>
          <dd>{{ detail.sessionName }} · {{ detail.subjectName }}</dd>
        </div>
        <div>
          <dt>实际报考考点</dt>
          <dd>{{ detail.siteName }}</dd>
        </div>
        <div>
          <dt>报名时间</dt>
          <dd>{{ formatDate(detail.submittedAt, true) }}</dd>
        </div>
        <div>
          <dt>报名状态</dt>
          <dd>{{ states[detail.status] }}</dd>
        </div>
        <div v-if="detail.identityNumberMasked">
          <dt>证件号码</dt>
          <dd>{{ detail.identityNumberMasked }}</dd>
        </div>
        <div v-if="detail.phoneMasked">
          <dt>联系电话</dt>
          <dd>{{ detail.phoneMasked }}</dd>
        </div>
      </dl>
    </FetchState>
  </ActionDialog>
</template>
