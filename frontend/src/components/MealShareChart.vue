<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({ meals: { type: Array, default: () => [] } })
const element = ref(null)
let chart
const labels = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐', SNACK: '加餐' }
const totals = computed(() => {
  const result = { BREAKFAST: 0, LUNCH: 0, DINNER: 0, SNACK: 0 }
  props.meals.forEach((item) => { result[item.mealType] = (result[item.mealType] || 0) + Number(item.caloriesKcal || 0) })
  return result
})

function render() {
  if (!element.value || !props.meals.length) return
  if (!chart) chart = echarts.init(element.value)
  const data = Object.entries(totals.value).filter(([, value]) => value > 0).map(([key, value]) => ({ name: labels[key], value }))
  const total = data.reduce((sum, item) => sum + item.value, 0)
  chart.setOption({
    animationDuration: 520,
    color: ['#6f91bb', '#9fb5cc', '#d3a879', '#c5ced8'],
    tooltip: { trigger: 'item', formatter: '{b}: {c} kcal（{d}%）' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, textStyle: { color: '#748190' } },
    title: {
      text: `${total.toFixed(0)} kcal`,
      subtext: '当日合计',
      left: 'center',
      top: '37%',
      textStyle: { color: '#2f3b48', fontSize: 18 },
      subtextStyle: { color: '#8a96a2', fontSize: 12 },
    },
    series: [{ type: 'pie', radius: ['54%', '72%'], center: ['50%', '45%'], label: { show: false }, data }],
  }, true)
}

const resize = () => chart?.resize()
onMounted(() => { nextTick(render); window.addEventListener('resize', resize) })
watch(() => props.meals, () => nextTick(render), { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>

<template>
  <div class="chart-box">
    <div v-if="!meals.length" class="empty-state">当天暂无饮食数据</div>
    <div v-else ref="element" class="chart-canvas chart-canvas--donut"></div>
  </div>
</template>
