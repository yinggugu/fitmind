<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

import { askAssistant, getAssistantSuggestions } from '../api/assistant'
import ChatMessage from '../components/ChatMessage.vue'
import SuggestionList from '../components/SuggestionList.vue'

const FALLBACK_SUGGESTIONS = [
  '分析最近30天体重变化',
  '总结最近7天饮食',
  '最近哪一天热量最高',
  '我距离目标体重还有多少',
  '总结最近14天体重和饮食情况',
]

function buildSuggestions(remote) {
  const values = Array.isArray(remote) ? remote.filter((item) => typeof item === 'string' && item.trim()) : []
  return [...new Set(values.map((item) => item.trim()))]
}

let messageId = 0
const input = ref('')
const sending = ref(false)
const suggestionsLoading = ref(true)
const suggestions = ref([])
const conversation = ref(null)

function greeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

const messages = ref([
  {
    id: ++messageId,
    role: 'assistant',
    content: `${greeting()}，我可以帮你查询和分析已经记录的健康数据。`,
    degraded: false,
  },
])

const canSend = computed(() => input.value.trim().length > 0 && !sending.value)

async function scrollToLatest() {
  await nextTick()
  if (conversation.value) {
    conversation.value.scrollTo({
      top: conversation.value.scrollHeight,
      behavior: 'smooth',
    })
  }
}

function appendMessage(role, content, degraded = false) {
  messages.value.push({ id: ++messageId, role, content, degraded })
}

async function sendMessage(preset) {
  if (sending.value) return
  const question = typeof preset === 'string' ? preset.trim() : input.value.trim()
  if (!question) return

  appendMessage('user', question)
  input.value = ''
  sending.value = true
  await scrollToLatest()

  try {
    const result = await askAssistant(question)
    appendMessage(
      'assistant',
      result?.answer?.trim() || 'AI 暂时没有成功完成分析，请稍后再试。',
      Boolean(result?.degraded),
    )
    if (import.meta.env.DEV) {
      console.debug('[FitMind assistant] response received', {
        degraded: Boolean(result?.degraded),
      })
    }
  } catch (error) {
    appendMessage('assistant', 'AI 暂时没有成功完成分析，请稍后再试。')
    if (import.meta.env.DEV) {
      console.warn('[FitMind assistant] request failed', {
        status: error?.status || 0,
        message: error?.message || 'unknown error',
      })
    }
  } finally {
    sending.value = false
    await scrollToLatest()
  }
}

async function loadSuggestions() {
  suggestionsLoading.value = true
  try {
    const remote = await getAssistantSuggestions()
    const remoteSuggestions = buildSuggestions(remote)
    suggestions.value = remoteSuggestions.length ? remoteSuggestions : FALLBACK_SUGGESTIONS
  } catch (error) {
    suggestions.value = FALLBACK_SUGGESTIONS
    if (import.meta.env.DEV) {
      console.warn('[FitMind assistant] suggestions fallback enabled', {
        status: error?.status || 0,
      })
    }
  } finally {
    suggestionsLoading.value = false
  }
}

onMounted(loadSuggestions)
</script>

<template>
  <section class="assistant-page">
    <div class="page-heading assistant-heading">
      <div>
        <p class="eyebrow">FITMIND · DATA ASSISTANT</p>
        <h1>AI 健康助手</h1>
        <p>可以直接问我你的体重、饮食和营养数据。</p>
      </div>
      <div class="privacy-note">
        <span aria-hidden="true"></span>
        只读取已记录数据
      </div>
    </div>

    <div class="chat-shell">
      <div ref="conversation" class="conversation" aria-live="polite">
        <div class="conversation__inner">
          <ChatMessage v-for="message in messages" :key="message.id" :message="message" />

          <SuggestionList
            v-if="messages.length === 1"
            :suggestions="suggestions"
            :loading="suggestionsLoading"
            :disabled="sending"
            @select="sendMessage"
          />

          <article v-if="sending" class="message message--assistant" aria-label="AI 正在分析">
            <div class="message__avatar" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path d="M12 3.5c.4 4.4 2.6 6.6 7 7-4.4.4-6.6 2.6-7 7-.4-4.4-2.6-6.6-7-7 4.4-.4 6.6-2.6 7-7Z" />
              </svg>
            </div>
            <div class="message__content">
              <div class="message__bubble message__bubble--typing">
                <span>正在分析已记录的数据</span>
                <i></i><i></i><i></i>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div class="composer">
        <div class="composer__box">
          <el-input
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            maxlength="500"
            resize="none"
            placeholder="输入你想了解的健康问题…"
            :disabled="sending"
            aria-label="输入健康问题"
            @keydown.enter.exact.prevent="sendMessage()"
          />
          <el-button
            class="send-button"
            type="primary"
            :loading="sending"
            :disabled="!canSend"
            aria-label="发送问题"
            @click="sendMessage()"
          >
            <svg v-if="!sending" viewBox="0 0 24 24" aria-hidden="true">
              <path d="m4 12 15-7-4.5 14-3-5.5L4 12Z" />
              <path d="m11.5 13.5 3-3" />
            </svg>
            <span>发送</span>
          </el-button>
        </div>
        <p>AI 只基于已记录的数据回答，营养结果仅供日常参考。</p>
      </div>
    </div>
  </section>
</template>
