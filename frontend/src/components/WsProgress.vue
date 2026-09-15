<template>
  <el-drawer v-model="visible" title="资源生成进度" size="320px" :close-on-click-modal="false">
    <div v-if="items.length" class="progress-list">
      <div v-for="(item, i) in items" :key="i" class="progress-item">
        <div class="item-header">
          <span class="agent-name">{{ item.agentName }}</span>
          <el-tag :type="statusType(item.status)" size="small">{{ item.status }}</el-tag>
        </div>
        <div class="item-message">{{ item.message }}</div>
        <el-progress
          v-if="item.status === 'RUNNING'"
          :percentage="60"
          :indeterminate="true"
          :show-text="false"
          style="margin-top: 4px"
        />
      </div>
    </div>
    <el-empty v-else description="等待任务开始..." :image-size="80" />
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  taskId: { type: String, default: null }
})

const visible = ref(false)
const items = ref([])
let ws = null

watch(() => props.taskId, (id) => {
  if (!id) return
  visible.value = true
  connect(id)
})

function connect(taskId) {
  if (ws) ws.close()
  items.value = []
  ws = new WebSocket(`ws://${location.host}/ws/progress?taskId=${taskId}`)
  ws.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.agentName === 'orchestrator' && data.status === 'DONE') {
        setTimeout(() => { visible.value = false }, 2000)
      }
      const idx = items.value.findIndex(x => x.agentName === data.agentName)
      if (idx >= 0) Object.assign(items.value[idx], data)
      else items.value.push(data)
    } catch {}
  }
}

function statusType(status) {
  const map = { DONE: 'success', FAILED: 'danger', RUNNING: 'warning' }
  return map[status] || 'info'
}
</script>

<style scoped>
.progress-item { padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.item-header { display: flex; justify-content: space-between; align-items: center; }
.agent-name { font-weight: 500; }
.item-message { font-size: 12px; color: #999; margin-top: 4px; }
</style>