<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 16px">
      <div style="display: flex; gap: 8px; align-items: center">
        <el-input v-model="chatStore.studentId" placeholder="学号" style="width: 160px" />
        <el-button type="primary" @click="chatStore.loadProfile">加载画像</el-button>
        <el-tag v-if="chatStore.profile" type="success">画像已加载 v{{ chatStore.profile?.meta?.version }}</el-tag>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>对话式学习</template>
      <div class="messages">
        <el-alert
          v-for="(m, i) in chatStore.messages"
          :key="i"
          :title="m.text"
          :type="m.role === 'assistant' ? 'info' : 'primary'"
          :closable="false"
          show-icon
          style="margin-bottom: 8px; white-space: pre-wrap"
        />
        <el-empty v-if="!chatStore.messages.length" description="发送消息开始对话" :image-size="60" />
      </div>
      <div style="display: flex; gap: 8px; margin-top: 12px">
        <el-input
          v-model="input"
          placeholder="输入你的专业、学习目标或问题..."
          @keyup.enter="handleSend"
          :disabled="chatStore.loading"
        />
        <el-button type="primary" :loading="chatStore.loading" @click="handleSend">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useChatStore } from '../stores/chatStore'

const chatStore = useChatStore()
const input = ref('')

function handleSend() {
  const text = input.value.trim()
  if (!text || chatStore.loading) return
  chatStore.send(text)
  input.value = ''
}
</script>

<style scoped>
.messages { max-height: 500px; overflow-y: auto; }
</style>