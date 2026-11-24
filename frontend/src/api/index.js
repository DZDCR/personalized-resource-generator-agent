import request from './request'

export function sendChat(studentId, message) {
  return request.post('/chat', { studentId, message })
}

export function getProfile(studentId) {
  return request.get(`/profile/${studentId}`)
}

export function generateResources(studentId, topic, resourceTypes) {
  return request.post('/resources/generate', { studentId, topic, resourceTypes })
}

export function listResources(studentId) {
  return request.get(`/resources/${studentId}`)
}

export function generatePath(studentId, targetTopic) {
  return request.post('/path/generate', { studentId, targetTopic })
}

export function submitQuiz(payload) {
  return request.post('/quiz/submit', payload)
}

export function askTutor(studentId, question) {
  return request.post(`/tutor/ask?studentId=${studentId}&question=${encodeURIComponent(question)}`)
}

export function weeklyAssessment(studentId) {
  return request.get(`/assessment/${studentId}/weekly`)
}

// ---------------------------------------------------------------------------
// 实时聊天（IM）
// ---------------------------------------------------------------------------

export function listImUsers() {
  return request.get('/im/demo/users')
}

export function listImConversations(studentId) {
  return request.get('/im/conversations', { params: { studentId } })
}

export function getImMessages(conversationId, studentId, afterSeq = 0, limit = 50) {
  return request.get(`/im/conversations/${conversationId}/messages`, {
    params: { studentId, afterSeq, limit }
  })
}

export function sendImMessage(conversationId, payload) {
  return request.post(`/im/conversations/${conversationId}/messages`, payload)
}

export function markImRead(conversationId, studentId, seq) {
  return request.post(`/im/conversations/${conversationId}/read`, { studentId, seq })
}

export function createImGroup(payload) {
  return request.post('/im/conversations/group', payload)
}

export function createImDirect(payload) {
  return request.post('/im/conversations/direct', payload)
}