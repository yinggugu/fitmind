<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '../api'
import GoalProgressChart from '../components/GoalProgressChart.vue'
import WeightChart from '../components/WeightChart.vue'

const overview = ref(null)
const loading = ref(true)
const error = ref('')
const unit = ref('斤')

const ratio = computed(() => (unit.value === '斤' ? 2 : 1))
const digits = computed(() => (unit.value === '斤' ? 1 : 2))
const formatWeight = (value, signed = false) => {
  const number = Number(value || 0) * ratio.value
  const prefix = signed && number > 0 ? '+' : ''
  return `${prefix}${number.toFixed(digits.value)} ${unit.value}`
}

onMounted(async () => {
  try {
    overview.value = await getDashboard()
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="legacy-page dashboard-page">
    <div class="page-heading legacy-heading">
      <div>
        <p class="eyebrow">FITMIND · OVERVIEW</p>
        <h1>数据总览</h1>
        <p>体重、目标和智能建议，一眼掌握近期状态。</p>
      </div>
      <button class="unit-switch" type="button" @click="unit = unit === '斤' ? 'kg' : '斤'">
        <strong>{{ unit }}</strong><span>{{ unit === '斤' ? 'kg' : '斤' }}</span>
      </button>
    </div>

    <div v-if="loading" class="legacy-loading">正在读取健康数据…</div>
    <div v-else-if="error" class="legacy-alert">{{ error }}</div>

    <template v-else-if="overview">
      <div class="metric-grid">
        <article class="metric-item metric-item--primary">
          <span>当前体重</span><strong>{{ formatWeight(overview.currentWeightKg) }}</strong><small>最新晨起记录</small>
        </article>
        <article class="metric-item">
          <span>距离目标</span><strong>{{ formatWeight(overview.distanceToTargetKg) }}</strong><small>目标 {{ formatWeight(overview.targetWeightKg) }}</small>
        </article>
        <article class="metric-item">
          <span>近7天变化</span><strong>{{ formatWeight(overview.change7dKg, true) }}</strong><small>根据实际记录计算</small>
        </article>
      </div>

      <div class="dashboard-charts">
        <section class="legacy-section trend-section">
          <div class="section-heading"><h2>近7天体重趋势</h2><span>单位：{{ unit }}</span></div>
          <WeightChart :data="overview.weightTrend" :unit="unit" />
        </section>
        <section class="legacy-section goal-section">
          <div class="section-heading"><h2>目标进度</h2><span>目标 {{ formatWeight(overview.targetWeightKg) }}</span></div>
          <GoalProgressChart :current="overview.currentWeightKg" :start="overview.startWeightKg" :target="overview.targetWeightKg" />
        </section>
      </div>

      <section class="today-reminder">
        <h2>FitMind 今日提醒</h2>
        <p><strong>近期状态：</strong>{{ overview.todayHealthLetter?.summary || '近期记录正在整理中。' }}</p>
        <p><strong>今天的一步：</strong>{{ overview.todayHealthLetter?.todayFocus || '保持正常三餐，继续轻松记录。' }}</p>
      </section>
    </template>
  </section>
</template>
