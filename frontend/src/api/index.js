class ApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request(path, options = {}) {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: options.body instanceof FormData
      ? options.headers
      : { 'Content-Type': 'application/json', ...options.headers },
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.code !== 200) {
    throw new ApiError(payload?.message || '请求未成功，请稍后再试。', response.status)
  }
  return payload.data
}

export const getDashboard = () => request('/dashboard/overview')
export const getWeights = (page = 1, size = 100) => request(`/weights?page=${page}&size=${size}`)
export const getWeightTrend = (days = 7) => request(`/weights/trend?days=${days}`)
export const getMeals = (date, page = 1, size = 100) =>
  request(`/meals?date=${encodeURIComponent(date)}&page=${page}&size=${size}`)
export const getLetter = () => request('/health-letters/today')
export const getLatestBodyMeasurement = () => request('/body-measurements/latest')
export const getBodyMeasurements = (page = 1, size = 10) => request(`/body-measurements?page=${page}&size=${size}`)

export const saveWeight = (data) => request('/weights', { method: 'POST', body: JSON.stringify(data) })
export const updateWeight = (id, data) => request(`/weights/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deleteWeight = (id) => request(`/weights/${id}`, { method: 'DELETE' })

export const saveMeal = (data) => request('/meals', { method: 'POST', body: JSON.stringify(data) })
export const updateMeal = (id, data) => request(`/meals/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deleteMeal = (id) => request(`/meals/${id}`, { method: 'DELETE' })

export const saveBodyMeasurement = (data) => request('/body-measurements', { method: 'POST', body: JSON.stringify(data) })
export const updateBodyMeasurement = (id, data) => request(`/body-measurements/${id}`, { method: 'PUT', body: JSON.stringify(data) })

export function analyzeMealImage(file) {
  const form = new FormData()
  form.append('file', file)
  return request('/meals/analyze-image', { method: 'POST', body: form })
}
