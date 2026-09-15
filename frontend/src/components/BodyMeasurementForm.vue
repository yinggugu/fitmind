<script setup>
import { reactive, ref, watch } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  measurement: { type: Object, default: () => ({}) },
  saving: Boolean,
})
const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)
const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const defaults = {
  recordDate: today(), heightCm: 156, weightKg: 56.5,
  upperChestCm: 82.5, underChestCm: 72.5, waistCm: 77, abdomenCm: 79,
  shoulderWidthCm: 38, thighCm: 57, calfCm: 37, ankleCm: 23,
  upperArmCm: 25, wristCm: 15, thighLengthCm: 38, calfLengthCm: 32,
  upperArmLengthCm: 24, forearmLengthCm: 24,
}
const form = reactive({ ...defaults })
const groups = [
  { title: '基础数据', fields: [
    { key: 'heightCm', label: '身高', min: 120, max: 220 },
    { key: 'weightKg', label: '体重', min: 25, max: 250, unit: 'kg' },
  ] },
  { title: '躯干维度', fields: [
    { key: 'upperChestCm', label: '上胸围', min: 50, max: 180 },
    { key: 'underChestCm', label: '下胸围', min: 45, max: 170 },
    { key: 'waistCm', label: '肚脐腰围', min: 45, max: 180 },
    { key: 'abdomenCm', label: '腹部最大围', min: 45, max: 200 },
    { key: 'shoulderWidthCm', label: '肩宽', min: 25, max: 70 },
  ] },
  { title: '四肢围度', fields: [
    { key: 'thighCm', label: '大腿围', min: 25, max: 100 },
    { key: 'calfCm', label: '小腿围', min: 18, max: 70 },
    { key: 'ankleCm', label: '脚踝围', min: 12, max: 45 },
    { key: 'upperArmCm', label: '大臂围', min: 15, max: 65 },
    { key: 'wristCm', label: '手腕围', min: 10, max: 35 },
  ] },
  { title: '肢体长度', fields: [
    { key: 'thighLengthCm', label: '大腿长', min: 20, max: 70 },
    { key: 'calfLengthCm', label: '小腿长', min: 20, max: 65 },
    { key: 'upperArmLengthCm', label: '大臂长', min: 15, max: 55 },
    { key: 'forearmLengthCm', label: '小臂长', min: 15, max: 55 },
  ] },
]

const numberRule = [{ required: true, message: '请填写该项', trigger: 'blur' }]

watch(() => props.modelValue, (open) => {
  if (open) Object.assign(form, defaults, props.measurement || {})
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = { recordDate: form.recordDate }
  groups.forEach((group) => group.fields.forEach((field) => { payload[field.key] = Number(form[field.key]) }))
  emit('save', payload)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑身体维度"
    width="720px"
    class="body-form-dialog"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="body-form-note">保存的是身体维度记录；当前 GLB 模型是静态参考，不会因数值变化而错误拉伸。</p>
    <el-form ref="formRef" :model="form" label-position="top">
      <el-form-item label="记录日期" prop="recordDate" :rules="[{ required: true, message: '请选择日期' }]">
        <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <section v-for="group in groups" :key="group.title" class="body-form-group">
        <h3>{{ group.title }}</h3>
        <div class="body-form-grid">
          <el-form-item v-for="field in group.fields" :key="field.key" :label="field.label" :prop="field.key" :rules="numberRule">
            <el-input-number v-model="form[field.key]" :min="field.min" :max="field.max" :precision="1" :step="0.5" controls-position="right" />
            <span class="body-form-unit">{{ field.unit || 'cm' }}</span>
          </el-form-item>
        </div>
      </section>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存到数据库</el-button>
    </template>
  </el-dialog>
</template>
