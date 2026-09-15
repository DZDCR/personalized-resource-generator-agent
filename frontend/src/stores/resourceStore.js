import { defineStore } from 'pinia'
import { ref } from 'vue'
import { generateResources, listResources } from '../api'

export const useResourceStore = defineStore('resource', () => {
  const studentId = '1'
  const list = ref([])
  const generating = ref(false)
  const currentTaskId = ref(null)

  async function refresh() {
    const res = await listResources(studentId)
    list.value = res.data || []
  }

  async function generate(topic) {
    generating.value = true
    try {
      const res = await generateResources(studentId, topic, [])
      currentTaskId.value = res.data.taskId
      await refresh()
    } finally {
      generating.value = false
    }
  }

  return { list, generating, currentTaskId, refresh, generate }
})