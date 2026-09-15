<template>
  <div class="progress-bar">
    <div v-for="(item, i) in items" :key="i" class="step" :class="item.status">
      <span class="icon">
        <template v-if="item.status === 'DONE'">✓</template>
        <template v-else-if="item.status === 'FAILED'">✗</template>
        <template v-else-if="item.status === 'RUNNING'">...</template>
        <template v-else>○</template>
      </span>
      <span class="label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({ items: { type: Array, default: () => [] } })
</script>

<style scoped>
.progress-bar { display: flex; gap: 16px; flex-wrap: wrap; }
.step { display: flex; align-items: center; gap: 4px; opacity: 0.5; }
.step.DONE { opacity: 1; color: #67c23a; }
.step.RUNNING { opacity: 1; color: #409eff; animation: pulse 1s infinite; }
.step.FAILED { opacity: 1; color: #f56c6c; }
.icon { font-weight: bold; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>