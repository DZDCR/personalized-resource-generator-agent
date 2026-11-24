import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import {
  listImUsers,
  listImConversations,
  getImMessages,
  markImRead,
  createImGroup,
  createImDirect
} from '../api'

const ID_KEY = 'im.studentId'
// 离线待发队列存 localStorage：刷新页面或断网重连后仍能补发，避免消息丢失。
const OUTBOX_KEY = 'im.outbox.v1'

function readOutbox() {
  try {
    return JSON.parse(localStorage.getItem(OUTBOX_KEY) || '[]')
  } catch {
    return []
  }
}

function uuid() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `m-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function normalizeMessage(message) {
  return { ...message, status: message.status || 'SENT' }
}

export const useImStore = defineStore('im', () => {
  const studentId = ref(Number(localStorage.getItem(ID_KEY)) || 0)
  const users = ref([])
  const conversations = ref([])
  const activeId = ref(null)
  const messages = ref([])
  const connected = ref(false)
  const connecting = ref(false)
  const loading = ref(false)
  const loadingConversations = ref(false)
  const draft = ref('')
  const outbox = ref(readOutbox())

  const activeConversation = computed(
    () => conversations.value.find((c) => c.id === activeId.value) || null
  )
  const currentUser = computed(
    () => users.value.find((u) => u.id === studentId.value) || null
  )
  const activeMembers = computed(() => activeConversation.value?.members || [])
  const connectionLabel = computed(() => {
    if (connected.value) return '实时在线'
    if (connecting.value) return '连接中'
    return '已离线'
  })

  let socket = null
  let heartbeatTimer = null
  let reconnectTimer = null
  let readTimer = null
  let manualClose = false
  let reconnectAttempts = 0
  let initialized = false
  let refreshPromise = null
  let connectionSeq = 0
  let socketUserId = null

  function persistOutbox() {
    localStorage.setItem(OUTBOX_KEY, JSON.stringify(outbox.value))
  }

  async function loadUsers() {
    const res = await listImUsers()
    users.value = res.data || []
    if (!users.value.length) return
    if (!studentId.value || !users.value.some((u) => u.id === studentId.value)) {
      studentId.value = users.value[0].id
      localStorage.setItem(ID_KEY, String(studentId.value))
    }
  }

  async function refresh() {
    if (refreshPromise) return refreshPromise
    refreshPromise = refreshInternal()
    try {
      return await refreshPromise
    } finally {
      refreshPromise = null
    }
  }

  async function refreshInternal() {
    if (!studentId.value) return
    loadingConversations.value = true
    try {
      const res = await listImConversations(studentId.value)
      conversations.value = res.data || []
      if (!activeId.value && conversations.value.length) {
        await selectConversation(conversations.value[0])
      } else if (activeId.value && !conversations.value.some((c) => c.id === activeId.value)) {
        activeId.value = null
        messages.value = []
        if (conversations.value.length) await selectConversation(conversations.value[0])
      }
    } finally {
      loadingConversations.value = false
    }
  }

  async function init() {
    if (!initialized) {
      initialized = true
      await loadUsers()
    }
    // 先建立长连接，再加载历史消息；页面渲染完成时通常已经是在线状态。
    connect()
    await refresh()
  }

  async function switchStudent(id) {
    // 注意：el-select 的 v-model 会先改 studentId，再触发 change。
    // 这里不能因为 id === studentId.value 就 return，否则 WebSocket 仍停留在旧身份。
    const next = Number(id)
    if (!next) return
    studentId.value = next
    localStorage.setItem(ID_KEY, String(next))
    activeId.value = null
    messages.value = []
    disconnect()
    connect()
    await refresh()
  }

  async function selectConversation(conversation) {
    if (!conversation) return
    activeId.value = conversation.id
    messages.value = []
    loading.value = true
    try {
      const res = await getImMessages(conversation.id, studentId.value, 0, 50)
      messages.value = (res.data?.messages || []).map(normalizeMessage)
      const summary = conversations.value.find((c) => c.id === conversation.id)
      if (summary && res.data?.lastSeq != null) summary.lastSeq = res.data.lastSeq
      await markReadNow()
    } catch (e) {
      ElMessage.error(e.message || '加载消息失败')
    } finally {
      loading.value = false
    }
  }

  async function loadMessages() {
    if (activeConversation.value) {
      await selectConversation(activeConversation.value)
    }
  }

  function connect() {
    if (!studentId.value) return
    // 复用仍处于 OPEN/CONNECTING 的同一身份连接，避免重复建链。
    if (socket && socketUserId === studentId.value
        && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return
    }
    // 身份切换或旧连接残留时，先彻底断开旧连接。
    if (socket) disconnect()

    manualClose = false
    connecting.value = true
    // connectionSeq 用于作废旧连接的回调：断开后旧 socket 的 onmessage/onclose 不再影响新状态。
    const seq = ++connectionSeq
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    // 身份通过握手参数传递；开发环境由 Vite proxy 转发到后端 8080。
    const ws = new WebSocket(`${protocol}://${window.location.host}/ws/im?studentId=${studentId.value}`)
    socket = ws
    socketUserId = studentId.value

    ws.onopen = () => {
      if (seq !== connectionSeq) return
      // 握手成功即可视为在线；服务端 connected 帧随后会触发一次增量同步。
      connected.value = true
      connecting.value = false
      startHeartbeat()
    }
    ws.onmessage = (event) => {
      if (seq !== connectionSeq) return
      try {
        handleFrame(JSON.parse(event.data))
      } catch (e) {
        console.warn('invalid im frame', event.data, e)
      }
    }
    ws.onerror = () => {
      if (seq !== connectionSeq) return
      connecting.value = false
      ws.close()
    }
    ws.onclose = () => {
      if (seq !== connectionSeq) return
      connected.value = false
      connecting.value = false
      socket = null
      socketUserId = null
      stopHeartbeat()
      if (!manualClose) scheduleReconnect()
    }
  }

  function disconnect() {
    connectionSeq += 1
    manualClose = true
    clearTimeout(reconnectTimer)
    reconnectTimer = null
    clearTimeout(readTimer)
    readTimer = null
    stopHeartbeat()
    const old = socket
    socket = null
    socketUserId = null
    if (old) {
      old.onopen = null
      old.onmessage = null
      old.onerror = null
      old.onclose = null
      old.close()
    }
    connected.value = false
    connecting.value = false
  }

  function scheduleReconnect() {
    if (reconnectTimer) return
    reconnectAttempts += 1
    // 指数退避：1s→2s→4s…最多 30s，并加随机抖动，避免多端同时重连造成惊群。
    const base = Math.min(30000, 1000 * 2 ** Math.min(reconnectAttempts, 5))
    const delay = base + Math.floor(Math.random() * 500)
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, delay)
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ v: 1, type: 'ping' }))
      }
    }, 25000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }

  async function handleFrame(frame) {
    const payload = frame.payload || {}
    // 服务端帧协议见 docs/im-api.md：connected / message.new / message.ack / message.read...
    switch (frame.type) {
      case 'connected':
        // 握手成功：重置重连计数，补发离线队列，并拉取断线期间漏掉的消息。
        connected.value = true
        connecting.value = false
        reconnectAttempts = 0
        flushOutbox()
        await refresh()
        await syncActiveConversation()
        break
      case 'message.ack':
        applyAck(payload)
        break
      case 'message.new':
        // 收到别人（或自己其他设备）的新消息：按 id/clientMsgId 去重后插入当前会话。
        await applyIncoming(payload)
        break
      case 'message.read':
        if (payload.userId === studentId.value) {
          const conv = conversations.value.find((c) => c.id === payload.conversationId)
          if (conv) conv.unreadCount = 0
        }
        break
      case 'message.read.ok':
        {
          const conv = conversations.value.find((c) => c.id === payload.conversationId)
          if (conv) conv.unreadCount = payload.unreadCount || 0
        }
        break
      case 'error':
        ElMessage.error(payload.message || '实时消息处理失败')
        break
      case 'pong':
      default:
        break
    }
  }

  function applyAck(message) {
    const local = messages.value.find((m) => m.clientMsgId === message.clientMsgId)
    if (local) {
      Object.assign(local, message, { status: 'SENT' })
      sortMessages()
    }
    removeOutbox(message.clientMsgId)
    updateConversationPreview(message.conversationId, message.content, message.createdAt, message)
  }

  async function applyIncoming(message) {
    const incoming = normalizeMessage(message)
    const existing = messages.value.find(
      (m) => m.id === incoming.id || (m.clientMsgId && m.clientMsgId === incoming.clientMsgId)
    )
    if (existing) {
      Object.assign(existing, incoming, { status: 'SENT' })
      sortMessages()
    } else if (activeId.value === incoming.conversationId) {
      messages.value.push(incoming)
      sortMessages()
    }

    updateConversationPreview(
      incoming.conversationId,
      incoming.content,
      incoming.createdAt,
      incoming
    )

    if (incoming.senderId !== studentId.value) {
      if (activeId.value === incoming.conversationId) {
        scheduleMarkRead()
      } else {
        const conv = conversations.value.find((c) => c.id === incoming.conversationId)
        if (conv && !conv.unreadCount) conv.unreadCount = 1
      }
    }
  }

  function updateConversationPreview(conversationId, preview, at, message) {
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (!conv) return
    conv.lastMessagePreview = preview
    conv.lastMessageAt = at
    if (message?.seq != null) conv.lastSeq = Math.max(conv.lastSeq || 0, message.seq)
    if (message && message.senderId !== studentId.value && activeId.value !== conversationId) {
      conv.unreadCount = (conv.unreadCount || 0) + 1
    }
  }

  async function syncActiveConversation() {
    const conv = activeConversation.value
    if (!conv) return
    // 用本地最大 seq 作为游标，向服务端增量补拉断线期间的消息。
    const lastSeq = messages.value.reduce((max, m) => Math.max(max, m.seq || 0), 0)
    if (!lastSeq) {
      await selectConversation(conv)
      return
    }
    try {
      const res = await getImMessages(conv.id, studentId.value, lastSeq, 100)
      const incoming = res.data?.messages || []
      for (const message of incoming) {
        if (!messages.value.some((m) => m.id === message.id)) {
          messages.value.push(normalizeMessage(message))
        }
      }
      if (incoming.length) {
        sortMessages()
        await markReadNow()
      }
    } catch (e) {
      console.warn('sync messages failed', e)
    }
  }

  function sortMessages() {
    messages.value.sort((a, b) => {
      if (a.seq == null && b.seq == null) {
        return new Date(a.createdAt || 0) - new Date(b.createdAt || 0)
      }
      if (a.seq == null) return 1
      if (b.seq == null) return -1
      return a.seq - b.seq
    })
  }

  function sendMessage() {
    const text = draft.value.trim()
    const conv = activeConversation.value
    if (!text || !conv) return
    const clientMsgId = uuid()
    // 乐观发送：先在本地插入 SENDING 气泡，再走 WebSocket，服务端 ACK 后替换为正式消息。
    const optimistic = {
      id: clientMsgId,
      conversationId: conv.id,
      seq: null,
      senderId: studentId.value,
      senderName: currentUser.value?.name || '我',
      type: 'TEXT',
      content: text,
      clientMsgId,
      createdAt: new Date().toISOString(),
      status: 'SENDING'
    }
    messages.value.push(optimistic)
    draft.value = ''
    updateConversationPreview(conv.id, text, optimistic.createdAt, optimistic)

    if (connected.value && socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({
        v: 1,
        type: 'message.send',
        requestId: clientMsgId,
        payload: {
          conversationId: conv.id,
          clientMsgId,
          content: text
        }
      }))
    } else {
      optimistic.status = 'QUEUED'
      outbox.value.push({
        studentId: studentId.value,
        conversationId: conv.id,
        clientMsgId,
        content: text,
        createdAt: optimistic.createdAt
      })
      persistOutbox()
      ElMessage.warning('当前未连接，消息已进入待发送队列')
    }
  }

  function flushOutbox() {
    if (!connected.value || !socket || socket.readyState !== WebSocket.OPEN) return
    // 重连后补发离线队列；clientMsgId 保持不变，服务端据此做幂等去重。
    for (const item of outbox.value) {
      if (item.studentId !== studentId.value) continue
      socket.send(JSON.stringify({
        v: 1,
        type: 'message.send',
        requestId: item.clientMsgId,
        payload: {
          conversationId: item.conversationId,
          clientMsgId: item.clientMsgId,
          content: item.content
        }
      }))
    }
  }

  function removeOutbox(clientMsgId) {
    const before = outbox.value.length
    outbox.value = outbox.value.filter((item) => item.clientMsgId !== clientMsgId)
    if (outbox.value.length !== before) persistOutbox()
  }

  function scheduleMarkRead() {
    clearTimeout(readTimer)
    readTimer = setTimeout(() => {
      readTimer = null
      markReadNow()
    }, 120)
  }

  async function markReadNow() {
    const conv = activeConversation.value
    if (!conv) return
    // 用当前会话已知的最大 seq 作为已读位置，交由服务端做上限裁剪。
    const seq = Math.max(
      conv.lastSeq || 0,
      messages.value.reduce((max, m) => Math.max(max, m.seq || 0), 0)
    )
    if (!seq) return
    conv.unreadCount = 0
    if (connected.value && socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({
        v: 1,
        type: 'message.read',
        payload: { conversationId: conv.id, seq }
      }))
    } else {
      try {
        const res = await markImRead(conv.id, studentId.value, seq)
        conv.unreadCount = res.data?.unreadCount || 0
      } catch (e) {
        console.warn('mark read failed', e)
      }
    }
  }

  async function createGroup(title, memberIds) {
    const res = await createImGroup({
      studentId: studentId.value,
      title,
      memberIds
    })
    await refresh()
    const created = conversations.value.find((c) => c.id === res.data.id)
    if (created) await selectConversation(created)
    return created
  }

  async function createDirect(peerId) {
    const res = await createImDirect({ studentId: studentId.value, peerId })
    await refresh()
    const created = conversations.value.find((c) => c.id === res.data.id)
    if (created) await selectConversation(created)
    return created
  }

  return {
    studentId,
    users,
    conversations,
    activeId,
    messages,
    connected,
    connecting,
    loading,
    loadingConversations,
    draft,
    outbox,
    activeConversation,
    activeMembers,
    currentUser,
    connectionLabel,
    init,
    switchStudent,
    refresh,
    selectConversation,
    loadMessages,
    sendMessage,
    createGroup,
    createDirect,
    disconnect
  }
})
