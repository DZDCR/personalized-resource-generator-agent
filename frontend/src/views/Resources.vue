<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>多智能体资源生成</template>
      <div style="display: flex; gap: 8px; align-items: center">
        <el-input v-model="topic" placeholder="输入学习主题（如：递归算法）" style="width: 300px" />
        <el-button type="primary" :loading="resourceStore.generating" @click="handleGenerate">
          生成全套资源
        </el-button>
        <el-button @click="resourceStore.refresh">刷新列表</el-button>
      </div>
    </el-card>

    <WsProgress :taskId="resourceStore.currentTaskId" />

    <el-table :data="resourceStore.list" stripe v-loading="!resourceStore.list.length">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="type" label="类型" width="140">
        <template #default="{ row }">
          <el-tag :type="tagType(row.type)">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="topic" label="主题" />
      <el-table-column prop="difficulty" label="难度" width="100" />
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}</template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!resourceStore.list.length && !resourceStore.generating" description="暂无资源，输入主题生成" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useResourceStore } from '../stores/resourceStore'
import WsProgress from '../components/WsProgress.vue'

const resourceStore = useResourceStore()
const topic = ref('')

function tagType(type) {
  const map = { DOC: '', MINDMAP: 'success', QUIZ: 'warning', READING: 'info', VIDEO_SCRIPT: 'danger', CODE: 'primary' }
  return map[type] || ''
}

async function handleGenerate() {
  if (!topic.value.trim()) return
  await resourceStore.generate(topic.value)
}
</script>