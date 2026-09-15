<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>个性化学习路径</template>
      <div style="display: flex; gap: 8px; align-items: center">
        <el-input v-model="target" placeholder="学习目标（如：递归）" style="width: 300px" />
        <el-button type="primary" :loading="pathStore.loading" @click="handleGenerate">生成路径</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <PathTimeline :steps="pathStore.steps" />
      <div v-if="Object.keys(pathStore.estimatedMinutes).length" style="margin-top: 16px; color: #999; font-size: 13px">
        预估总时长：{{ totalMinutes }} 分钟
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { usePathStore } from '../stores/pathStore'
import PathTimeline from '../components/PathTimeline.vue'

const pathStore = usePathStore()
const target = ref('')

const totalMinutes = computed(() => {
  return Object.values(pathStore.estimatedMinutes).reduce((sum, v) => sum + v, 0)
})

function handleGenerate() {
  if (!target.value.trim()) return
  pathStore.generate(target.value)
}
</script>