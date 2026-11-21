<script setup>
import { hasPermission } from '@/composables/auth.js'
import { adminRequest } from '@/api/client.js'
import { useAsync } from '@/composables/useAsync.js'
import AdminTitle from '@/components/AdminTitle.vue'
import FetchState from '@/components/FetchState.vue'

const { result, pending, error, reload } = useAsync((signal) => adminRequest('/roles', { signal }))
reload()
</script>

<template>
  <AdminTitle title="角色与权限" summary="查看内部角色对应的动作权限与数据范围">
    <RouterLink v-if="hasPermission('users.write')" class="button primary" to="/admin/users"
      >配置账号授权</RouterLink
    >
  </AdminTitle>
  <section class="panel admin-panel">
    <FetchState :pending="pending" :error="error" :empty="!result?.items.length" @retry="reload">
      <div class="role-grid">
        <article v-for="role in result.items" :key="role.id" class="role-card">
          <h2>{{ role.name }}</h2>
          <p>{{ role.description }}</p>
          <ul>
            <li v-for="permission in role.permissionLabels" :key="permission">{{ permission }}</li>
          </ul>
        </article>
      </div>
    </FetchState>
  </section>
</template>
