<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({ meals: { type: Array, default: () => [] } })
const element = ref(null)
let chart
const totals = computed(() => ({
  protein: props.meals.reduce((sum, item) => sum + Number(item.proteinG || 0), 0),
  carbohydrate: props.meals.reduce((sum, item) => sum + Number(item.carbohydrateG || 0), 0),
  fat: props.meals.reduce((sum, item) => sum + Number(item.fatG || 0), 0),
  calories: props.meals.reduce((sum, item) => sum + Number(item.caloriesKcal || 0), 0),
}))

function render() {
  if (!element.value || !props.meals.length) return
  if (!chart) chart = echarts.init(element.value)
  const data = [
    { name: '蛋白质', value: totals.value.protein },
    { name: '碳水', value: totals.value.carbohydrate },
    { name: '脂肪', value: totals.value.fat },
  ]
  chart.setOption({
    animationDuration: 520,
    color: ['#6f91bb', '#a8bdd4', '#d3a879'],
    tooltip: { trigger: 'item', formatter: '{b}: {c}g（{d}%）' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, textStyle: { color: '#748190' } },
    title: {
      text: `${totals.value.calories.toFixed(0)} kcal`,
      subtext: '当日总热量',
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
