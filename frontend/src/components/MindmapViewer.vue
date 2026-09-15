<template>
  <div class="mindmap-container">
    <div v-if="!jsonTree" class="empty">暂无思维导图数据</div>
    <div v-else ref="container" class="mindmap" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'

const props = defineProps({
  jsonTree: { type: String, default: '' }
})

const container = ref(null)

onMounted(() => {
  renderMap()
})

watch(() => props.jsonTree, () => renderMap())

function renderMap() {
  if (!container.value || !props.jsonTree) return
  // markmap 渲染示例（实际引入需 npm install markmap-view）
  try {
    const tree = JSON.parse(props.jsonTree)
    container.value.innerHTML = `<pre style="white-space:pre-wrap;font-size:12px">${JSON.stringify(tree, null, 2)}</pre>`
  } catch {
    container.value.innerHTML = `<pre>${props.jsonTree}</pre>`
  }
}
</script>

<style scoped>
.mindmap-container { border: 1px solid #eee; border-radius: 8px; padding: 12px; min-height: 200px; }
.empty { color: #999; text-align: center; padding: 40px; }
.mindmap { max-height: 600px; overflow: auto; }
</style>