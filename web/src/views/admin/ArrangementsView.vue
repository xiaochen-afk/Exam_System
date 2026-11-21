<script setup>
import { computed, ref, shallowRef, watch } from 'vue'
import { Calendar, Check, Grid } from '@element-plus/icons-vue'
import { adminRequest } from '@/api/client.js'
import { useAdminList, loadOptions } from '@/composables/useAdminList.js'
import { useAsync } from '@/composables/useAsync.js'
import { useAction } from '@/composables/useAction.js'
import { hasPermission } from '@/composables/auth.js'
import AdminTitle from '@/components/AdminTitle.vue'
import SessionPicker from '@/components/SessionPicker.vue'
import ActionDialog from '@/components/ActionDialog.vue'
import DataTable from '@/components/DataTable.vue'
import FetchState from '@/components/FetchState.vue'
import PageControl from '@/components/PageControl.vue'

const sessionId = ref('')
const { result, pending, error, reload, page, size, search, resize } = useAdminList(
  '/arrangements',
  {},
  () => ({ sessionId: sessionId.value }),
  () => !!sessionId.value,
)
const {
  result: options,
  pending: roomsPending,
  error: roomsError,
  reload: loadRooms,
} = useAsync((signal) =>
  loadOptions(['rooms'], signal, { context: 'arrangements', sessionId: sessionId.value }),
)
const { busy, failure, success, run } = useAction()
const action = ref('')
const selected = shallowRef()
const roomId = ref('')
const seatNumber = ref()
const stats = computed(() => result.value?.summary)
const columns = [
  ['registrationNumber', '报名编号'],
  ['candidateName', '考生姓名'],
  ['roomName', '考场'],
  ['seatNumber', '座位号'],
  ['ticketNumber', '准考证号'],
  ['arrangementStatus', '排考状态'],
]
const titles = { seat: '分配考场与座位', auto: '自动排考', publish: '开放准考证' }
const canEdit = computed(
  () => hasPermission('arrangements.write') && result.value?.capabilities.includes('EDIT'),
)
const canPublish = computed(
  () => hasPermission('tickets.publish') && result.value?.capabilities.includes('PUBLISH'),
)
const activeRoom = computed(() => options.value?.rooms.find((room) => room.id === roomId.value))
watch(sessionId, () => {
  success.value = ''
  search()
})

function open(key, row) {
  failure.value = ''
  action.value = key
  selected.value = row
  if (key === 'seat') {
    roomId.value = row.roomId ?? ''
    seatNumber.value = row.seatNumber ?? undefined
    loadRooms()
  }
}

async function submit() {
  const path = '/exam-sessions/' + encodeURIComponent(sessionId.value)
  const operation = action.value
  const saved = await run(() => {
    if (operation === 'seat')
      return adminRequest(
        '/registrations/' + encodeURIComponent(selected.value.id) + '/arrangement',
        {
          method: 'PUT',
          body: { roomId: roomId.value, seatNumber: Number(seatNumber.value) },
        },
      )
    if (operation === 'auto')
      return adminRequest(path + '/arrangement-runs', { method: 'POST', body: {} })
    return adminRequest(path + '/ticket-publication', { method: 'PUT', body: { published: true } })
  }, { seat: '座位已保存', auto: '自动排考已完成', publish: '准考证已开放' }[operation])
  if (saved) {
    action.value = ''
    await reload()
  }
}
</script>

<template>
  <AdminTitle title="排考管理" summary="分配考场与座位，确认后开放准考证" />
  <SessionPicker v-model="sessionId" context="arrangements" />
  <section class="panel admin-panel">
    <p v-if="!sessionId" class="feedback">选择考试场次后开始排考</p>
    <FetchState v-else :error="error" :pending="pending" @retry="reload">
      <div v-if="stats" class="workbench-summary">
        <div>
          <h2><Calendar aria-hidden="true" />排考进度</h2>
          <p>
            已分配 {{ stats.assigned }} / {{ stats.total }} 人 · 可用座位 {{ stats.capacity }} 个
          </p>
          <progress :value="stats.assigned" :max="Math.max(1, stats.total)" aria-label="排考进度" />
        </div>
        <div class="inline-actions">
          <button
            v-if="canEdit"
            class="button outline"
            :disabled="stats.capacity < stats.total || stats.assigned >= stats.total"
            @click="open('auto')"
          >
            <Grid aria-hidden="true" />自动排考
          </button>
          <button
            v-if="canPublish"
            class="button primary"
            :disabled="
              !stats.total || stats.assigned !== stats.total || stats.capacity < stats.total
            "
            @click="open('publish')"
          >
            <Check aria-hidden="true" />开放准考证
          </button>
        </div>
      </div>
      <p v-if="stats?.capacity < stats?.total" class="inline-error" role="status">
        考场座位不足，请先调整考场容量。
      </p>
      <p v-if="stats?.ticketsPublished" class="inline-success" role="status">准考证已开放</p>
      <p v-else-if="success" class="inline-success" role="status">{{ success }}</p>
      <p v-if="!result?.items.length" class="feedback">该场次暂无有效报名</p>
      <template v-else>
        <DataTable :columns="columns" :rows="result.items"
          ><template v-if="canEdit" #actions="{ row }"
            ><button class="link-button" @click="open('seat', row)">
              {{ row.roomId ? '调整座位' : '分配座位' }}
            </button></template
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
      <template v-if="action === 'seat'">
        <p class="editor-hint">报名编号 {{ selected.registrationNumber }}</p>
        <div class="editor-hint">
          <FetchState
            :pending="roomsPending"
            :error="roomsError"
            :empty="!options?.rooms.length"
            empty-text="暂无可用考场"
            @retry="loadRooms"
          />
        </div>
        <label
          >考场<select v-model="roomId" :disabled="roomsPending || Boolean(roomsError)" required>
            <option value="">请选择考场</option>
            <option v-for="room in options?.rooms" :key="room.id" :value="room.id">
              {{ room.name }}（{{ room.capacity }} 座）
            </option>
          </select></label
        >
        <label
          >座位号<input
            v-model.number="seatNumber"
            type="number"
            min="1"
            :max="activeRoom?.capacity"
            step="1"
            required
        /></label>
      </template>
      <p v-else class="editor-hint">
        {{
          action === 'auto'
            ? '系统将为尚未分配的报名安排座位。已有座位保留。'
            : '确认排考无误后开放准考证，考生可在小程序查看本人准考证。'
        }}
      </p>
      <div class="dialog-actions">
        <button type="button" class="button outline" :disabled="busy" @click="action = ''">
          取消</button
        ><button class="button primary" :disabled="busy || (action === 'seat' && !activeRoom)">
          {{ busy ? '处理中…' : '确认' }}
        </button>
      </div>
    </form>
  </ActionDialog>
</template>
