<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <div style="padding: 60px; text-align: center; color: #94a3b8">
    <h2>「{{ route.meta.title }}」</h2>
    <p>模块开发中 —— 工程骨架阶段（接口契约见 docs/design/05.API接口设计.md）</p>
  </div>
</template>
