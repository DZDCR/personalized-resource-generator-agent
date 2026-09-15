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