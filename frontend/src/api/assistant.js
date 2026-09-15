const REQUEST_TIMEOUT_MS = 55_000

export class AssistantApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'AssistantApiError'
    this.status = status
  }
}

async function request(path, options = {}) {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(path, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok || payload?.code !== 200) {
      throw new AssistantApiError(payload?.message || '请求未成功', response.status)
    }
    return payload.data
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new AssistantApiError('请求超时，请稍后再试', 408)
    }
    throw error
  } finally {
    window.clearTimeout(timer)
  }
}

export function getAssistantSuggestions() {
  return request('/api/assistant/suggestions', { method: 'GET' })
}

export function askAssistant(question) {
  return request('/api/assistant/chat', {
    method: 'POST',
    body: JSON.stringify({ question }),
  })
}
