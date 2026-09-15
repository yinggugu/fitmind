<script setup>
defineProps({
  message: { type: Object, required: true },
})
</script>

<template>
  <article class="message" :class="`message--${message.role}`">
    <div v-if="message.role === 'assistant'" class="message__avatar" aria-hidden="true">
      <svg viewBox="0 0 24 24">
        <path d="M12 3.5c.4 4.4 2.6 6.6 7 7-4.4.4-6.6 2.6-7 7-.4-4.4-2.6-6.6-7-7 4.4-.4 6.6-2.6 7-7Z" />
      </svg>
    </div>
    <div class="message__content">
      <div class="message__bubble">{{ message.content }}</div>
      <p v-if="message.degraded" class="message__degraded">
        AI 服务暂时不可用，已返回基础查询结果。
      </p>
    </div>
  </article>
</template>
