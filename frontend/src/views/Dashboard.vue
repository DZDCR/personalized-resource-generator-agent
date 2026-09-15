<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>学习效果评估</template>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>综合得分：<el-tag type="success" size="large">{{ assessmentStore.report?.scoreTotal ?? '-' }}</el-tag></span>
        <el-button type="primary" @click="assessmentStore.load" :loading="assessmentStore.loading">刷新评估</el-button>
      </div>
    </el-card>

    <el-empty v-if="!assessmentStore.report && !assessmentStore.loading" description="暂无评估数据，点击上方刷新" />

    <template v-else>
      <el-row :gutter="16">
        <el-col :span="6" v-for="(item, i) in dimensions" :key="i">
          <el-card shadow="hover" style="text-align: center">
            <div style="font-size: 28px; font-weight: bold; color: #409eff">{{ item.value }}</div>
            <div style="color: #999; margin-top: 4px">{{ item.label }}</div>
            <el-progress :percentage="item.value" :color="item.color" :show-text="false" />
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" style="margin-top: 16px">
        <template #header>评估报告</template>
        <div style="color: #666; line-height: 1.8; white-space: pre-wrap">{{ assessmentStore.report?.report }}</div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAssessmentStore } from '../stores/assessmentStore'

const assessmentStore = useAssessmentStore()
assessmentStore.load()

const dimensions = computed(() => {
  const d = assessmentStore.report?.dimensions || {}
  return [
    { label: '知识掌握', value: d.knowledge ?? 0, color: '#67c23a' },
    { label: '学习投入', value: d.effort ?? 0, color: '#409eff' },
    { label: '学习效率', value: d.efficiency ?? 0, color: '#e6a23c' },
    { label: '能力成长', value: d.growth ?? 0, color: '#f56c6c' }
  ]
})
</script>