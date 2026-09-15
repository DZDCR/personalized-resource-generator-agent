import { defineStore } from 'pinia'
import { ref } from 'vue'
import { generatePath } from '../api'

export const usePathStore = defineStore('path', () => {
  const studentId = '1'
  const steps = ref([])
  const activeStep = ref(0)
  const estimatedMinutes = ref({})
  const loading = ref(false)

  async function generate(targetTopic) {
    loading.value = true
    try {
      const res = await generatePath(studentId, targetTopic)
      steps.value = res.data.stepOrder || []
      estimatedMinutes.value = res.data.estimatedMinutes || {}
    } finally {
      loading.value = false
    }
  }

  return { steps, activeStep, estimatedMinutes, loading, generate }
})