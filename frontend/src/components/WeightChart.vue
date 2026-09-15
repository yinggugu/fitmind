<script setup>
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  unit: { type: String, default: '斤' },
})

const element = ref(null)
let chart
const resize = () => chart?.resize()

function render() {
  if (!element.value || !props.data.length) return
  if (!chart) chart = echarts.init(element.value)
  const rows = [...props.data].sort((a, b) => String(a.recordDate).localeCompare(String(b.recordDate)))
  const ratio = props.unit === '斤' ? 2 : 1
  chart.setOption({
    animationDuration: 520,
    grid: { top: 24, right: 24, bottom: 35, left: 48 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => `${Number(value).toFixed(1)} ${props.unit}`,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map((item) => String(item.recordDate).slice(5)),
      axisLine: { lineStyle: { color: '#dfe7ee' } },
      axisTick: { show: false },
      axisLabel: { color: '#84909d' },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#84909d' },
      splitLine: { lineStyle: { color: '#edf1f5' } },
    },
    series: [{
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: rows.map((item) => +(Number(item.weightKg) * ratio).toFixed(1)),
      itemStyle: { color: '#6f91bb' },
      lineStyle: { width: 3, color: '#6f91bb' },
      areaStyle: { color: 'rgba(111,145,187,.10)' },
    }],
  }, true)
}

onMounted(() => {
  nextTick(render)
  window.addEventListener('resize', resize)
})
watch(() => [props.data, props.unit], () => nextTick(render), { deep: true })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
</script>

<template>
  <div class="chart-box">
    <div v-if="!data.length" class="empty-state">暂无体重数据</div>
    <div v-else ref="element" class="chart-canvas"></div>
  </div>
</template>
