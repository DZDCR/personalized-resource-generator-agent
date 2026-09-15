<template>
  <div class="path-timeline">
    <el-timeline>
      <el-timeline-item
        v-for="(step, i) in steps"
        :key="i"
        :type="statusType(step.status)"
        :hollow="step.status !== 'DONE'"
        :timestamp="step.estimatedTime || ''"
        placement="top"
      >
        <div class="step-content">
          <span class="step-name">{{ step.topic || step }}</span>
          <el-tag v-if="step.status" :type="statusType(step.status)" size="small">
            {{ step.status }}
          </el-tag>
          <el-button v-if="step.status === 'AVAILABLE'" type="primary" size="small" plain>
            开始学习
          </el-button>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-if="!steps.length" description="尚未生成学习路径" />
  </div>
</template>

<script setup>
defineProps({
  steps: { type: Array, default: () => [] }
})

function statusType(status) {
  const map = { DONE: 'success', AVAILABLE: 'primary', LOCKED: 'info', RUNNING: 'warning' }
  return map[status] || 'info'
}
</script>

<style scoped>
.step-content { display: flex; align-items: center; gap: 8px; }
.step-name { font-weight: 500; }
</style>