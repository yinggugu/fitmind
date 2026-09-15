<script setup>
defineProps({
  suggestions: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['select'])
</script>

<template>
  <section class="suggestions" aria-label="快捷问题">
    <div class="suggestions__head">
      <span>你可以这样问</span>
      <span v-if="loading" class="suggestions__loading">正在准备问题…</span>
    </div>
    <div class="suggestions__list">
      <button
        v-for="suggestion in suggestions"
        :key="suggestion"
        type="button"
        :disabled="disabled"
        @click="$emit('select', suggestion)"
      >
        <span>{{ suggestion }}</span>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m9 6 6 6-6 6" />
        </svg>
      </button>
    </div>
  </section>
</template>
