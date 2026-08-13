<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <main class="error-page">
    <van-empty image="error" description="页面不存在或地址已变更">
      <van-button type="primary" size="small" @click="router.replace('/')">返回首页</van-button>
    </van-empty>
  </main>
</template>

<style scoped>.error-page { display:grid; min-height:100vh; place-items:center; background:var(--surface); }</style>
