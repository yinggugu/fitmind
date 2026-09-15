<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  analyzeMealImage,
  deleteMeal,
  deleteWeight,
  getMeals,
  getWeights,
  saveMeal,
  saveWeight,
  updateMeal,
  updateWeight,
} from '../api'
import MealShareChart from '../components/MealShareChart.vue'
import NutritionChart from '../components/NutritionChart.vue'
import WeightChart from '../components/WeightChart.vue'

const route = useRoute()
const router = useRouter()
const tab = ref(route.path === '/meals' ? 'meal' : 'weight')
const unit = ref('斤')
const weights = ref([])
const meals = ref([])
const date = ref('')
const loading = ref(true)
const fileInput = ref(null)
const selectedFile = ref(null)
const previewUrl = ref('')
const analyzing = ref(false)
const editingMealId = ref(null)
const draftVisible = ref(false)

const blankMeal = () => ({
  mealType: 'LUNCH',
  foodName: '',
  portionDescription: '',
  caloriesKcal: 0,
  proteinG: 0,
  carbohydrateG: 0,
  fatG: 0,
  estimated: false,
  sourceType: 'MANUAL',
})
const mealDraft = ref(blankMeal())
const mealLabels = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐', SNACK: '加餐' }
const sourceLabels = { MANUAL: '手动填写', AI_IMAGE: 'AI识别', SEED_ESTIMATE: '历史估算' }
const subtitle = computed(() => tab.value === 'weight' ? '记录晨起体重，也记录每一点稳定的变化。' : '记录饮食，查看最近一周的营养结构。')

async function loadWeights() {
  const result = await getWeights(1, 100)
  weights.value = result?.records || []
  if (!date.value && weights.value.length) date.value = weights.value[0].recordDate
}

async function loadMeals() {
  if (!date.value) return
  const result = await getMeals(date.value, 1, 100)
  meals.value = result?.records || []
}

async function refresh() {
  loading.value = true
  try {
    await loadWeights()
    await loadMeals()
  } catch (exception) {
    ElMessage.error(exception.message)
  } finally {
    loading.value = false
  }
}

function switchTab(value) {
  router.push(value === 'weight' ? '/weights' : '/meals')
}

async function addWeight() {
  try {
    const { value } = await ElMessageBox.prompt(`请输入晨起体重（${unit.value}）`, '新增体重', {
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: '请输入大于 0 的数字，最多两位小数。',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    const number = Number(value)
    if (number <= 0) throw new Error('体重必须大于 0。')
    await saveWeight({ recordDate: date.value, recordTime: '07:00:00', weightKg: unit.value === '斤' ? number / 2 : number })
    await loadWeights()
    ElMessage.success('体重记录已保存。')
  } catch (exception) {
    if (exception === 'cancel' || exception === 'close') return
    ElMessage.error(exception.message || '体重保存失败。')
  }
}

async function editWeight(record) {
  try {
    const current = Number(record.weightKg) * (unit.value === '斤' ? 2 : 1)
    const { value } = await ElMessageBox.prompt(`修改体重（${unit.value}）`, '编辑体重', {
      inputValue: current.toFixed(1),
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: '请输入大于 0 的数字，最多两位小数。',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    const number = Number(value)
    if (number <= 0) throw new Error('体重必须大于 0。')
    await updateWeight(record.id, {
      recordDate: record.recordDate,
      recordTime: record.recordTime || '07:00:00',
      weightKg: unit.value === '斤' ? number / 2 : number,
    })
    await loadWeights()
    ElMessage.success('体重记录已更新。')
  } catch (exception) {
    if (exception === 'cancel' || exception === 'close') return
    ElMessage.error(exception.message || '体重更新失败。')
  }
}

async function removeWeight(record) {
  try {
    await ElMessageBox.confirm(`确定删除 ${record.recordDate} 的体重记录吗？`, '删除确认', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
    await deleteWeight(record.id)
    await loadWeights()
    ElMessage.success('体重记录已删除。')
  } catch (exception) {
    if (exception === 'cancel' || exception === 'close') return
    ElMessage.error(exception.message || '删除失败。')
  }
}

function chooseImage() {
  fileInput.value?.click()
}

function handleImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
}

function removeImage() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

async function analyzeImage() {
  if (!selectedFile.value) return ElMessage.info('请先选择一张食物图片。')
  analyzing.value = true
  try {
    const result = await analyzeMealImage(selectedFile.value)
    mealDraft.value = { ...blankMeal(), ...result, estimated: true, sourceType: 'AI_IMAGE' }
    editingMealId.value = null
    draftVisible.value = true
    ElMessage.success('识别完成，请确认结果后保存。')
  } catch (exception) {
    ElMessage.error(exception.message || '图片识别暂时不可用。')
  } finally {
    analyzing.value = false
  }
}

function startManualMeal() {
  editingMealId.value = null
  mealDraft.value = blankMeal()
  draftVisible.value = true
}

function editMealRecord(record) {
  editingMealId.value = record.id
  mealDraft.value = {
    mealType: record.mealType,
    foodName: record.foodName,
    portionDescription: record.portionDescription || '',
    caloriesKcal: Number(record.caloriesKcal || 0),
    proteinG: Number(record.proteinG || 0),
    carbohydrateG: Number(record.carbohydrateG || 0),
    fatG: Number(record.fatG || 0),
    estimated: Boolean(record.estimated),
    sourceType: record.sourceType || 'MANUAL',
  }
  draftVisible.value = true
}

async function saveMealDraft() {
  if (!mealDraft.value.foodName.trim()) return ElMessage.warning('请填写食物名称。')
  const payload = {
    ...mealDraft.value,
    recordDate: date.value,
    caloriesKcal: Number(mealDraft.value.caloriesKcal || 0),
    proteinG: Number(mealDraft.value.proteinG || 0),
    carbohydrateG: Number(mealDraft.value.carbohydrateG || 0),
    fatG: Number(mealDraft.value.fatG || 0),
  }
  try {
    if (editingMealId.value) await updateMeal(editingMealId.value, payload)
    else await saveMeal(payload)
    await loadMeals()
    draftVisible.value = false
    ElMessage.success(editingMealId.value ? '饮食记录已更新。' : '饮食记录已保存。')
  } catch (exception) {
    ElMessage.error(exception.message || '饮食记录保存失败。')
  }
}

async function removeMealRecord(record) {
  try {
    await ElMessageBox.confirm(`确定删除“${record.foodName}”吗？`, '删除确认', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
    await deleteMeal(record.id)
    await loadMeals()
    ElMessage.success('饮食记录已删除。')
  } catch (exception) {
    if (exception === 'cancel' || exception === 'close') return
    ElMessage.error(exception.message || '删除失败。')
  }
}

watch(() => route.path, (path) => { tab.value = path === '/meals' ? 'meal' : 'weight' })
watch(date, () => loadMeals().catch((exception) => ElMessage.error(exception.message)))
onMounted(refresh)
onBeforeUnmount(removeImage)
</script>

<template>
  <section class="legacy-page records-page">
    <div class="page-heading legacy-heading">
      <div><p class="eyebrow">FITMIND · DAILY RECORDS</p><h1>每日记录</h1><p>{{ subtitle }}</p></div>
    </div>

    <div class="record-tabs">
      <button :class="{ active: tab === 'weight' }" type="button" @click="switchTab('weight')">体重记录</button>
      <button :class="{ active: tab === 'meal' }" type="button" @click="switchTab('meal')">饮食记录</button>
    </div>

    <div v-if="loading" class="legacy-loading">正在读取记录…</div>

    <template v-else-if="tab === 'weight'">
      <section class="legacy-section">
        <div class="section-heading section-heading--actions">
          <div><h2>体重趋势</h2><span>晨起空腹 · 单位：{{ unit }}</span></div>
          <div class="inline-actions">
            <button type="button" class="soft-button" @click="unit = unit === '斤' ? 'kg' : '斤'">切换为{{ unit === '斤' ? 'kg' : '斤' }}</button>
            <input v-model="date" type="date" aria-label="记录日期" />
            <button type="button" class="primary-button" @click="addWeight">＋ 记录今日体重</button>
          </div>
        </div>
        <WeightChart :data="weights.slice(0, 30)" :unit="unit" />
      </section>

      <section class="legacy-section history-section">
        <div class="section-heading"><h2>历史记录</h2><span>共 {{ weights.length }} 条</span></div>
        <div v-if="!weights.length" class="empty-state">暂无体重记录</div>
        <div v-else class="legacy-table-wrap">
          <table class="legacy-table">
            <thead><tr><th>日期</th><th>晨起体重</th><th>记录时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="record in weights" :key="record.id">
                <td>{{ record.recordDate }}</td>
                <td>{{ (Number(record.weightKg) * (unit === '斤' ? 2 : 1)).toFixed(unit === '斤' ? 1 : 2) }} {{ unit }}</td>
                <td>{{ record.recordTime || '07:00:00' }}</td>
                <td><button class="text-button" @click="editWeight(record)">编辑</button><button class="text-button text-button--danger" @click="removeWeight(record)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <template v-else>
      <div class="meal-datebar"><input v-model="date" type="date" /><span>查看所选日期的饮食与营养</span></div>
      <div class="meal-entry-grid">
        <section class="upload-workspace">
          <input ref="fileInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="handleImage" />
          <img v-if="previewUrl" :src="previewUrl" alt="食物图片本地预览" />
          <div v-else class="upload-empty"><span>＋</span><h2>上传食物照片</h2><p>图片只在当前页面预览，不会直接入库。</p></div>
          <div class="upload-actions">
            <button class="primary-button" type="button" @click="chooseImage">选择图片</button>
            <button v-if="previewUrl" class="soft-button" type="button" @click="removeImage">移除</button>
            <button class="soft-button" type="button" @click="startManualMeal">手动添加</button>
          </div>
        </section>

        <section class="recognition-workspace">
          <div class="section-heading"><h2>识别结果</h2><span>{{ draftVisible ? (editingMealId ? '编辑记录' : mealDraft.sourceType === 'AI_IMAGE' ? 'AI 估算' : '手动填写') : '等待识别' }}</span></div>
          <div v-if="!draftVisible" class="recognition-empty"><p>上传图片后，在这里确认食物和营养数据。</p><button class="primary-button" :disabled="analyzing" @click="analyzeImage">{{ analyzing ? '正在识别…' : '开始识别' }}</button></div>
          <div v-else class="meal-form">
            <label class="meal-form__wide">食物名称<input v-model="mealDraft.foodName" /></label>
            <label>餐次<select v-model="mealDraft.mealType"><option v-for="(label, value) in mealLabels" :key="value" :value="value">{{ label }}</option></select></label>
            <label>份量<input v-model="mealDraft.portionDescription" /></label>
            <label>热量 kcal<input v-model.number="mealDraft.caloriesKcal" type="number" min="0" /></label>
            <label>蛋白质 g<input v-model.number="mealDraft.proteinG" type="number" min="0" /></label>
            <label>碳水 g<input v-model.number="mealDraft.carbohydrateG" type="number" min="0" /></label>
            <label>脂肪 g<input v-model.number="mealDraft.fatG" type="number" min="0" /></label>
            <div class="meal-form__actions"><button class="soft-button" @click="draftVisible = false">取消</button><button class="primary-button" @click="saveMealDraft">保存记录</button></div>
          </div>
        </section>
      </div>

      <div class="meal-chart-grid">
        <section class="legacy-section"><div class="section-heading"><h2>当日营养结构</h2><span>根据当前记录计算</span></div><NutritionChart :meals="meals" /></section>
        <section class="legacy-section"><div class="section-heading"><h2>当日热量来源</h2><span>按餐次汇总</span></div><MealShareChart :meals="meals" /></section>
      </div>

      <section class="legacy-section history-section">
        <div class="section-heading section-heading--actions"><div><h2>{{ date }} 饮食记录</h2><span>共 {{ meals.length }} 条</span></div><button class="soft-button" @click="startManualMeal">＋ 手动添加</button></div>
        <div v-if="!meals.length" class="empty-state">当天暂无饮食记录</div>
        <div v-else class="legacy-table-wrap">
          <table class="legacy-table">
            <thead><tr><th>餐次</th><th>食物</th><th>热量</th><th>蛋白质</th><th>来源</th><th>操作</th></tr></thead>
            <tbody><tr v-for="record in meals" :key="record.id"><td>{{ mealLabels[record.mealType] }}</td><td>{{ record.foodName }}</td><td>{{ record.caloriesKcal }} kcal</td><td>{{ record.proteinG }}g</td><td>{{ sourceLabels[record.sourceType] || record.sourceType }}</td><td><button class="text-button" @click="editMealRecord(record)">编辑</button><button class="text-button text-button--danger" @click="removeMealRecord(record)">删除</button></td></tr></tbody>
          </table>
        </div>
      </section>
    </template>
  </section>
</template>
