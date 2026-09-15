<script setup>
import { computed } from 'vue'

const props = defineProps({ current: Number, start: Number, target: Number })
const progress = computed(() => {
  const total = Number(props.start) - Number(props.target)
  if (!Number.isFinite(total) || total <= 0) return 0
  return Math.max(0, Math.min(100, ((Number(props.start) - Number(props.current)) / total) * 100))
})
</script>

<template>
  <div class="goal-chart">
    <div class="progress-ring" :style="{ '--progress': `${progress}%` }">
      <span><strong>{{ progress.toFixed(1) }}%</strong><small>已完成</small></span>
    </div>
    <div class="goal-chart__values">
      <span><strong>{{ ((start - current) * 2).toFixed(1) }}斤</strong><small>已经减去</small></span>
      <span><strong>{{ Math.max(0, (current - target) * 2).toFixed(1) }}斤</strong><small>距离目标</small></span>
    </div>
  </div>
</template>
