<script setup>
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { getBodyMeasurements, getLatestBodyMeasurement, saveBodyMeasurement, updateBodyMeasurement } from '../api'
import BodyMeasurementForm from '../components/BodyMeasurementForm.vue'
import BodyModelViewer from '../components/BodyModelViewer.vue'

const measurement = ref(null)
const historyTotal = ref(0)
const loading = ref(true)
const error = ref('')
const dialogVisible = ref(false)
const saving = ref(false)

const groups = computed(() => {
  if (!measurement.value) return []
  return [
    { title: '躯干维度', items: [
      ['上胸围', measurement.value.upperChestCm], ['下胸围', measurement.value.underChestCm],
      ['肚脐腰围', measurement.value.waistCm], ['腹部最大围', measurement.value.abdomenCm],
      ['肩宽', measurement.value.shoulderWidthCm],
    ] },
    { title: '四肢围度', items: [
      ['大腿围', measurement.value.thighCm], ['小腿围', measurement.value.calfCm],
      ['脚踝围', measurement.value.ankleCm], ['大臂围', measurement.value.upperArmCm],
      ['手腕围', measurement.value.wristCm],
    ] },
    { title: '肢体长度', items: [
      ['大腿长', measurement.value.thighLengthCm], ['小腿长', measurement.value.calfLengthCm],
      ['大臂长', measurement.value.upperArmLengthCm], ['小臂长', measurement.value.forearmLengthCm],
    ] },
  ]
})

const completeness = computed(() => {
  if (!measurement.value) return 0
  const values = groups.value.flatMap((group) => group.items.map((item) => item[1]))
  return Math.round((values.filter((value) => value !== null && value !== undefined).length / values.length) * 100)
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [latest, page] = await Promise.all([getLatestBodyMeasurement(), getBodyMeasurements(1, 10)])
    measurement.value = latest
    historyTotal.value = page?.total || 0
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

async function save(payload) {
  saving.value = true
  try {
    measurement.value = measurement.value?.id
      ? await updateBodyMeasurement(measurement.value.id, payload)
      : await saveBodyMeasurement(payload)
    dialogVisible.value = false
    await loadData()
    ElMessage.success('身体维度已保存到数据库。')
  } catch (exception) {
    ElMessage.error(exception.message || '身体维度保存失败。')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <section class="legacy-page body-profile-page">
    <div class="page-heading legacy-heading body-profile-heading">
      <div>
        <p class="eyebrow">FITMIND · BODY PROFILE</p>
        <h1>身体画像</h1>
        <p>用当前身体维度建立一份可以持续更新的个人画像。</p>
      </div>
      <button class="primary-button" type="button" :disabled="loading" @click="dialogVisible = true">编辑身体数据</button>
    </div>

    <div v-if="error" class="legacy-alert">{{ error }}</div>
    <div v-else class="body-profile-layout">
      <section class="body-visual-panel">
        <div class="body-panel-heading">
          <div><span>当前维度模型</span><small v-if="measurement">记录于 {{ measurement.recordDate }}</small></div>
          <span class="body-status"><i></i>模型已加载</span>
        </div>
        <BodyModelViewer model-url="/models/fitmind-current-body.glb" />
        <p class="body-model-caption">模型用于当前身体形象的三维展示；维度数据变化会保存到数据库，但不会对模型做失真的局部拉伸。</p>
      </section>

      <aside class="body-data-panel">
        <div v-if="loading" class="legacy-loading">正在读取身体维度…</div>
        <template v-else-if="measurement">
          <div class="body-summary-grid">
            <article><small>身高</small><strong>{{ measurement.heightCm }}<em>cm</em></strong></article>
            <article><small>当前体重</small><strong>{{ (measurement.weightKg * 2).toFixed(1) }}<em>斤</em></strong></article>
            <article><small>数据完整度</small><strong>{{ completeness }}<em>%</em></strong></article>
          </div>

          <section v-for="group in groups" :key="group.title" class="body-data-group">
            <h2>{{ group.title }}</h2>
            <div class="body-measurement-list">
              <div v-for="item in group.items" :key="item[0]"><span>{{ item[0] }}</span><strong>{{ item[1] }} <small>cm</small></strong></div>
            </div>
          </section>

          <div class="body-history-note"><span>已保存 {{ historyTotal }} 次身体画像记录</span><small>同一天保存会更新当天记录</small></div>
        </template>
        <div v-else class="empty-state">暂无身体维度，点击“编辑身体数据”创建第一条记录。</div>
      </aside>
    </div>

    <BodyMeasurementForm v-model="dialogVisible" :measurement="measurement || {}" :saving="saving" @save="save" />
  </section>
</template>
