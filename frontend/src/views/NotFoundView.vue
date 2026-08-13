<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <main class="error-page">
    <el-result icon="warning" title="页面不存在" sub-title="地址可能已变更，或你没有可用的入口。">
      <template #extra><el-button type="primary" @click="router.replace('/dashboard')">返回运营看板</el-button></template>
    </el-result>
  </main>
</template>

<style scoped>.error-page { display:grid; min-height:100vh; place-items:center; background:#f3f6f4; }</style>
