<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
})
</script>

<template>
  <section class="recovery-page">
    <div class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
    </div>
    <div class="recovery-notice">
      <span class="recovery-notice__icon" aria-hidden="true">i</span>
      <div>
        <h2>身体画像源码尚未找到</h2>
        <p>
          已从旧目录恢复数据总览、体重记录、饮食记录和健康信源码，但旧目录中没有身体画像页面。
          本轮没有从压缩后的 dist 反向拼装该模块，旧构建产物仍原样保存在 <code>frontend/legacy-dist-backup</code>。
        </p>
        <RouterLink to="/assistant">前往 AI 健康助手</RouterLink>
      </div>
    </div>
  </section>
</template>
