import { defineStore } from 'pinia'
import { ref } from 'vue'
import { weeklyAssessment } from '../api'

export const useAssessmentStore = defineStore('assessment', () => {
  const studentId = '1'
  const report = ref(null)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await weeklyAssessment(studentId)
      report.value = res.data
    } finally {
      loading.value = false
    }
  }

  return { report, loading, load }
})