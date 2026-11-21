<script setup>
import { ElPagination } from 'element-plus'
import 'element-plus/theme-chalk/el-pagination.css'
import 'element-plus/theme-chalk/el-select.css'
import 'element-plus/theme-chalk/el-popper.css'
import 'element-plus/theme-chalk/el-scrollbar.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-select-v2.css'

defineProps({ page: Number, size: Number, total: Number, sizes: Boolean })
defineEmits(['change', 'resize'])
</script>

<template>
  <div v-if="total > 0" class="pager">
    <span>共 {{ total.toLocaleString('zh-CN') }} 条</span>
    <label v-if="sizes" class="pager-size"
      >每页
      <select
        :value="size"
        aria-label="每页条数"
        @change="$emit('resize', Number($event.target.value))"
      >
        <option :value="10">10 条</option>
        <option :value="20">20 条</option>
        <option :value="50">50 条</option>
      </select>
    </label>
    <ElPagination
      background
      layout="prev, pager, next"
      :pager-count="5"
      :total="total"
      :current-page="page"
      :page-size="size"
      @update:current-page="$emit('change', $event)"
    />
  </div>
</template>
