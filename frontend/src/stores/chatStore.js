import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sendChat, getProfile } from '../api'

export const useChatStore = defineStore('chat', () => {
  const studentId = ref('1')
  const profile = ref(null)
  const messages = ref([])
  const loading = ref(false)

  async function loadProfile() {
    const res = await getProfile(studentId.value)
    profile.value = res.data
  }

  async function send(text) {
    if (!text.trim()) return
    messages.value.push({ role: 'user', text })
    loading.value = true
    try {
      const res = await sendChat(studentId.value, text)
      messages.value.push({ role: 'assistant', text: res.data.reply })
      if (res.data.mode === 'PROFILE_BUILDING') {
        await loadProfile()
      }
    } finally {
      loading.value = false
    }
  }

  return { studentId, profile, messages, loading, loadProfile, send }
})