<script setup>
import { computed, reactive, ref } from 'vue'
import { adminRequest } from '@/api/client.js'
import { useAsync } from '@/composables/useAsync.js'
import {
  scopeLabels,
  scopesForRole,
  actionsForGrant,
  staffPayload,
} from '@/domain/staff-authorization.js'
import FetchState from './FetchState.vue'

const props = defineProps({ row: Object, busy: Boolean })
const emit = defineEmits(['save', 'cancel'])
const form = reactive({
  identifier: '',
  initialPassword: '',
  displayName: props.row?.displayName ?? '',
  status: props.row?.status ?? 'ACTIVE',
})
const grants = ref(
  (props.row?.grants ?? []).map((grant) => ({
    ...grant,
    permissionCodes: [...grant.permissionCodes],
  })),
)
const replaceGrants = ref(!props.row)
const failure = ref('')
const protectedAdministrator = computed(() =>
  props.row?.grants.some((grant) => grant.permissionCodes.includes('admins.manage')),
)
const { result, pending, error, reload } = useAsync((signal) =>
  adminRequest('/grant-options', { signal }),
)
reload()

function changeScope(grant) {
  grant.projectId = null
  grant.sessionId = null
  grant.permissionCodes = []
}
function changeRole(grant) {
  grant.scopeType = scopesForRole(result.value, grant.roleCode)[0] ?? ''
  changeScope(grant)
}
function addGrant() {
  const grant = {
    roleCode: result.value.roles[0]?.id ?? '',
    scopeType: '',
    projectId: null,
    sessionId: null,
    permissionCodes: [],
  }
  changeRole(grant)
  grants.value.push(grant)
}
function submit() {
  if (props.busy) return
  failure.value = ''
  try {
    emit(
      'save',
      staffPayload({
        form,
        row: props.row,
        grants: grants.value,
        replaceGrants: replaceGrants.value,
        options: result.value,
      }),
    )
  } catch (error) {
    failure.value = error.message
  }
}
</script>

<template>
  <FetchState :pending="pending" :error="error" @retry="reload">
    <form class="editor-form" @submit.prevent="submit">
      <label v-if="!row"
        >登录账号<input
          v-model="form.identifier"
          required
          minlength="4"
          maxlength="32"
          autocomplete="off"
      /></label>
      <label
        >姓名<input v-model="form.displayName" required maxlength="50" autocomplete="off"
      /></label>
      <label v-if="!row"
        >初始密码<input
          v-model="form.initialPassword"
          type="password"
          required
          minlength="8"
          maxlength="64"
          autocomplete="new-password"
      /></label>
      <label
        >状态<select v-model="form.status">
          <option value="ACTIVE">启用</option>
          <option value="INACTIVE">停用</option>
        </select></label
      >
      <p v-if="protectedAdministrator" class="full-width muted">
        此账号含受保护的管理员权限，当前页面仅支持修改姓名和状态。
      </p>
      <label v-else-if="row" class="full-width grant-toggle"
        ><input v-model="replaceGrants" type="checkbox" />修改角色与授权</label
      >
      <template v-if="replaceGrants && !protectedAdministrator">
        <p class="full-width editor-hint muted">
          每条授权分别选择角色、范围与允许动作。删除全部授权后，账号将无法访问内部业务数据。
        </p>
        <fieldset v-for="(grant, index) in grants" :key="index" class="full-width grant-card">
          <legend>授权 {{ index + 1 }}</legend>
          <div class="grant-fields">
            <label
              >角色<select
                v-model="grant.roleCode"
                :aria-label="`授权 ${index + 1} 角色`"
                @change="changeRole(grant)"
              >
                <option v-for="role in result.roles" :key="role.id" :value="role.id">
                  {{ role.name }}
                </option>
              </select></label
            >
            <label
              >范围<select
                v-model="grant.scopeType"
                :aria-label="`授权 ${index + 1} 范围`"
                @change="changeScope(grant)"
              >
                <option
                  v-for="scope in scopesForRole(result, grant.roleCode)"
                  :key="scope"
                  :value="scope"
                >
                  {{ scopeLabels[scope] }}
                </option>
              </select></label
            >
            <label v-if="grant.scopeType === 'PROJECT'"
              >报考项目<select
                v-model="grant.projectId"
                :aria-label="`授权 ${index + 1} 报考项目`"
                required
              >
                <option :value="null">请选择报考项目</option>
                <option v-for="option in result.projects" :key="option.id" :value="option.id">
                  {{ option.name }}
                </option>
              </select></label
            >
            <label v-if="grant.scopeType === 'SESSION'"
              >考试场次<select
                v-model="grant.sessionId"
                :aria-label="`授权 ${index + 1} 考试场次`"
                required
              >
                <option :value="null">请选择考试场次</option>
                <option v-for="option in result.sessions" :key="option.id" :value="option.id">
                  {{ option.name }}
                </option>
              </select></label
            >
          </div>
          <fieldset class="grant-actions">
            <legend>允许动作</legend>
            <label v-for="action in actionsForGrant(result, grant)" :key="action.permissionCode">
              <input
                v-model="grant.permissionCodes"
                type="checkbox"
                :value="action.permissionCode"
              />{{ action.permissionLabel }}
            </label>
          </fieldset>
          <button
            type="button"
            class="link-button"
            :disabled="busy"
            :aria-label="`删除授权 ${index + 1}`"
            @click="grants.splice(index, 1)"
          >
            删除此授权
          </button>
        </fieldset>
        <p v-if="!grants.length" class="full-width muted">
          暂未配置授权。账号可以创建，后续按需要授予具体权限。
        </p>
        <button
          type="button"
          class="button outline full-width"
          :disabled="busy || grants.length >= 100 || !result.roles.length"
          @click="addGrant"
        >
          添加授权
        </button>
      </template>
      <p v-if="failure" role="alert" class="inline-error full-width">{{ failure }}</p>
      <div class="dialog-actions">
        <button type="button" class="button outline" :disabled="busy" @click="$emit('cancel')">
          取消
        </button>
        <button class="button primary" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </FetchState>
</template>

<style scoped>
.grant-card {
  border: 1px solid var(--line, #dce4ed);
  border-radius: 8px;
  padding: 16px;
  min-width: 0;
}
.grant-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.grant-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  border: 0;
  padding: 12px 0;
}
.grant-actions label,
.grant-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
}
.grant-actions input,
.grant-toggle input {
  width: auto;
}
@media (max-width: 600px) {
  .grant-fields {
    grid-template-columns: 1fr;
  }
}
</style>
