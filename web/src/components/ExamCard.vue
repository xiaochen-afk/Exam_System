<script setup>
import {
  Calendar,
  Collection,
  Location,
  User,
  Reading,
  Medal,
  Monitor,
  Tickets,
} from '@element-plus/icons-vue'
import StateBadge from './StateBadge.vue'
import { formatDate } from '@/domain/presentation.js'

defineProps({ exam: { type: Object, required: true }, index: { type: Number, default: 0 } })
const icons = [Reading, Tickets, Monitor, Medal]
</script>

<template>
  <article class="exam-card">
    <h3>
      <span class="exam-icon"
        ><component :is="icons[index % icons.length]" aria-hidden="true" /></span
      >{{ exam.name }}
    </h3>
    <dl>
      <div>
        <dt><User aria-hidden="true" />报名状态</dt>
        <dd><StateBadge :value="exam.registrationStatus" /></dd>
      </div>
      <div>
        <dt><Collection aria-hidden="true" />考试科目</dt>
        <dd>{{ exam.subjectName }}</dd>
      </div>
      <div>
        <dt><Calendar aria-hidden="true" />考试时间</dt>
        <dd>{{ formatDate(exam.examStartsAt, true) }}</dd>
      </div>
      <div>
        <dt><Location aria-hidden="true" />开放考点</dt>
        <dd>{{ exam.siteName }}</dd>
      </div>
      <div>
        <dt>报名开始</dt>
        <dd>{{ formatDate(exam.registrationStartsAt, true) }}</dd>
      </div>
      <div>
        <dt>报名截止</dt>
        <dd>{{ formatDate(exam.registrationEndsAt, true) }}</dd>
      </div>
    </dl>
    <RouterLink class="button outline" :to="'/exams/' + exam.id">查看详情</RouterLink>
  </article>
</template>
