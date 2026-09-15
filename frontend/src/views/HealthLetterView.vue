<script setup>
import { onMounted, ref } from 'vue'
import { getLetter } from '../api'

const letter = ref(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    letter.value = await getLetter()
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="legacy-page health-letter-page">
    <div class="page-heading legacy-heading">
      <div>
        <p class="eyebrow">FITMIND · DAILY LETTER</p>
        <h1>今日健康信</h1>
        <p>根据昨天的饮食记录，为今天写一封温和的短信。</p>
      </div>
    </div>

    <div v-if="loading" class="legacy-loading">正在展开今天的信…</div>
    <div v-else-if="error" class="legacy-alert">{{ error }}</div>
    <article v-else-if="letter" class="health-letter">
      <header>
        <div><span>写给今天的你</span><small>分析日期 {{ letter.analysisDate }}</small></div>
        <time>{{ letter.reportDate }}</time>
      </header>
      <p>早上好。</p>
      <p>{{ letter.summary }}</p>
      <p>{{ letter.calorieAnalysis }}</p>
      <p>{{ letter.nutritionAnalysis }}</p>
      <section class="letter-focus">
        <strong>今天只关注一件事</strong>
        <p>{{ letter.todayFocus }}</p>
      </section>
      <footer>FitMind · 今日陪伴</footer>
    </article>
    <div v-else class="legacy-alert">今天还没有健康信。</div>
  </section>
</template>
