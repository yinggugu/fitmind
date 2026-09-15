<script setup>
const navigation = [
  { label: '首页', to: '/' },
  { label: '体重记录', to: '/weights' },
  { label: '饮食记录', to: '/meals' },
  { label: '身体画像', to: '/body-profile' },
  { label: '健康信', to: '/health-letter' },
  { label: 'AI 健康助手', to: '/assistant' },
]
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="topbar__inner">
        <RouterLink class="brand" to="/" aria-label="返回 FitMind 首页">
          <span class="brand__mark" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M12 3.5c.4 4.5 2.5 6.6 7 7-4.5.4-6.6 2.5-7 7-.4-4.5-2.5-6.6-7-7 4.5-.4 6.6-2.5 7-7Z" />
              <path d="M18.5 2.5v4M20.5 4.5h-4" />
            </svg>
          </span>
          <span>FitMind</span>
        </RouterLink>

        <nav class="main-nav" aria-label="主导航">
          <RouterLink v-for="item in navigation" :key="item.to" :to="item.to">
            {{ item.label }}
          </RouterLink>
        </nav>
      </div>
    </header>

    <main class="page-container">
      <RouterView v-slot="{ Component, route }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="route.path" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>
