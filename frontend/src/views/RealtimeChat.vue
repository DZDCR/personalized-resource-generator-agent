<template>
  <div class="im-page">
    <aside class="im-sidebar">
      <div class="im-brand">
        <div>
          <div class="brand-title">课程聊天</div>
          <div class="brand-sub">WebSocket · 多端同步</div>
        </div>
        <el-tag :type="tagType" size="small" effect="dark">{{ im.connectionLabel }}</el-tag>
      </div>

      <div class="identity">
        <el-select
          v-model="im.studentId"
          size="small"
          style="flex: 1"
          @change="im.switchStudent"
        >
          <el-option
            v-for="user in im.users"
            :key="user.id"
            :label="`${user.name}（${user.studentNo}）`"
            :value="user.id"
          />
        </el-select>
        <el-button size="small" @click="openGroupDialog">建群</el-button>
      </div>

      <div class="sidebar-actions">
        <span class="sidebar-hint">切换身份可模拟多名学生</span>
        <el-dropdown trigger="click" @command="startDirect">
          <el-button text size="small">发起私聊</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="user in otherUsers"
                :key="user.id"
                :command="user.id"
              >
                {{ user.name }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-loading="im.loadingConversations" class="conversation-list">
        <div
          v-for="conv in im.conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: conv.id === im.activeId }"
          @click="im.selectConversation(conv)"
        >
          <div class="avatar" :class="{ group: conv.type === 'GROUP' }">
            {{ avatarText(conv) }}
          </div>
          <div class="conversation-main">
            <div class="conversation-top">
              <span class="conversation-title">{{ conv.title }}</span>
              <span class="conversation-time">{{ formatConvTime(conv.lastMessageAt) }}</span>
            </div>
            <div class="conversation-bottom">
              <span class="conversation-preview">{{ conv.lastMessagePreview || '暂无消息' }}</span>
              <el-badge
                v-if="conv.unreadCount"
                :value="conv.unreadCount > 99 ? '99+' : conv.unreadCount"
                class="unread-badge"
              />
            </div>
          </div>
        </div>
        <el-empty
          v-if="!im.loadingConversations && !im.conversations.length"
          description="暂无会话，先建一个课程群"
          :image-size="64"
        />
      </div>
    </aside>

    <section class="im-main">
      <template v-if="im.activeConversation">
        <header class="chat-header">
          <div class="chat-title-block">
            <div class="chat-title">{{ im.activeConversation.title }}</div>
            <div class="chat-members">
              {{ memberLabel }}
            </div>
          </div>
          <div class="chat-actions">
            <el-tag size="small" effect="plain">
              {{ im.activeConversation.type === 'GROUP' ? '群聊' : '私聊' }}
            </el-tag>
            <el-button text size="small" @click="im.loadMessages">刷新</el-button>
          </div>
        </header>

        <div ref="messageListRef" v-loading="im.loading" class="message-scroll">
          <div class="message-list">
            <div
              v-for="message in im.messages"
              :key="message.id || message.clientMsgId"
              class="message-row"
              :class="{ mine: isMine(message) }"
            >
              <div class="avatar message-avatar">{{ initials(displaySender(message)) }}</div>
              <div class="message-body">
                <div class="message-meta">
                  <span>{{ displaySender(message) }}</span>
                  <span>{{ formatTime(message.createdAt) }}</span>
                </div>
                <div class="bubble">{{ message.content }}</div>
                <div class="message-status">
                  <template v-if="isMine(message)">
                    {{ statusText(message.status) }}
                    <span v-if="message.seq" class="seq">#{{ message.seq }}</span>
                  </template>
                </div>
              </div>
            </div>
            <el-empty
              v-if="!im.loading && !im.messages.length"
              description="还没有消息，打个招呼吧"
              :image-size="72"
            />
          </div>
        </div>

        <footer class="composer">
          <el-input
            v-model="im.draft"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="4000"
            show-word-limit
            placeholder="输入消息，Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="composer-actions">
            <span class="composer-hint">
              {{ im.connected ? '消息实时送达；切换身份可验证多端' : '当前离线，消息会进入待发送队列' }}
            </span>
            <el-button type="primary" :disabled="!im.draft.trim()" @click="handleSend">
              发送
            </el-button>
          </div>
        </footer>
      </template>

      <div v-else class="empty-main">
        <el-empty description="选择左侧会话开始聊天" :image-size="100" />
      </div>
    </section>

    <el-dialog v-model="groupDialog" title="新建课程群" width="440px">
      <el-form label-position="top">
        <el-form-item label="群名称">
          <el-input v-model="groupTitle" maxlength="128" placeholder="例如：软件工程课程群" />
        </el-form-item>
        <el-form-item label="邀请成员">
          <el-checkbox-group v-model="selectedMembers" class="member-checkboxes">
            <el-checkbox
              v-for="user in otherUsers"
              :key="user.id"
              :value="user.id"
              :label="user.name"
            />
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingGroup" @click="createGroup">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useImStore } from '../stores/imStore'

const im = useImStore()
const route = useRoute()
const messageListRef = ref(null)
const groupDialog = ref(false)
const groupTitle = ref('')
const selectedMembers = ref([])
const creatingGroup = ref(false)

const otherUsers = computed(() => im.users.filter((u) => u.id !== im.studentId))
const tagType = computed(() => {
  if (im.connected) return 'success'
  if (im.connecting) return 'warning'
  return 'danger'
})
const memberLabel = computed(() => {
  const names = im.activeMembers.map((m) => m.displayName)
  if (names.length <= 6) return names.join('、')
  return `${names.slice(0, 6).join('、')} 等 ${names.length} 人`
})

onMounted(async () => {
  // 初始化：拉取演示用户、建立 WebSocket、加载会话列表与首个会话的历史消息。
  await im.init()
  // 支持 /im?student=2 直接指定演示身份，便于开多个标签页验证多端实时同步。
  const requested = Number(route.query.student)
  if (Number.isFinite(requested) && requested > 0 && requested !== im.studentId) {
    await im.switchStudent(requested)
  }
  await nextTick()
  scrollToBottom()
})

onBeforeUnmount(() => {
  // 离开页面时主动断开，避免后端残留无效会话连接。
  im.disconnect()
})

watch(
  () => im.messages.length,
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

watch(
  () => im.activeId,
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function handleSend() {
  if (!im.draft.trim()) return
  // 发送逻辑（乐观渲染 + WebSocket/离线队列）统一放在 store 中，页面只负责触发和滚动。
  im.sendMessage()
  nextTick(scrollToBottom)
}

function isMine(message) {
  return message.senderId === im.studentId
}

function displaySender(message) {
  return isMine(message) ? '我' : message.senderName || `学生${message.senderId}`
}

function avatarText(conv) {
  if (conv.type === 'GROUP') return '群'
  return (conv.title || '聊').slice(0, 1)
}

function initials(name) {
  return (name || '?').slice(0, 1)
}

function statusText(status) {
  const map = {
    SENDING: '发送中…',
    QUEUED: '等待重连',
    SENT: '已发送',
    FAILED: '发送失败'
  }
  return map[status] || '已发送'
}

function formatConvTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function openGroupDialog() {
  groupTitle.value = ''
  selectedMembers.value = otherUsers.value.map((u) => u.id)
  groupDialog.value = true
}

async function createGroup() {
  if (!groupTitle.value.trim()) {
    ElMessage.warning('请输入群名称')
    return
  }
  if (!selectedMembers.value.length) {
    ElMessage.warning('至少邀请一名成员')
    return
  }
  creatingGroup.value = true
  try {
    await im.createGroup(groupTitle.value.trim(), selectedMembers.value)
    groupDialog.value = false
    ElMessage.success('课程群已创建')
    await nextTick()
    scrollToBottom()
  } catch (e) {
    ElMessage.error(e.message || '创建群聊失败')
  } finally {
    creatingGroup.value = false
  }
}

async function startDirect(peerId) {
  try {
    await im.createDirect(peerId)
    await nextTick()
    scrollToBottom()
  } catch (e) {
    ElMessage.error(e.message || '发起私聊失败')
  }
}
</script>

<style scoped>
.im-page {
  display: flex;
  height: calc(100vh - 40px);
  min-height: 520px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e3ebee;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(23, 74, 91, 0.06);
}

.im-sidebar {
  width: 300px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: #f6faf8;
  border-right: 1px solid #e3ebee;
}

.im-brand {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 18px 10px;
}

.brand-title {
  font-size: 19px;
  font-weight: 800;
  color: #174a5b;
  letter-spacing: 0.5px;
}

.brand-sub {
  margin-top: 3px;
  font-size: 11px;
  color: #7d9198;
}

.identity {
  display: flex;
  gap: 8px;
  padding: 4px 14px 4px;
}

.sidebar-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 16px 8px;
}

.sidebar-hint {
  font-size: 11px;
  color: #8a9ba1;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.conversation-item {
  display: flex;
  gap: 10px;
  padding: 11px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}

.conversation-item:hover {
  background: #edf5f2;
}

.conversation-item.active {
  background: #e1efea;
}

.avatar {
  width: 40px;
  height: 40px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 13px;
  color: #174a5b;
  font-weight: 800;
  background: #dcece6;
}

.avatar.group {
  color: #6b4e16;
  background: #f3e7c8;
}

.conversation-main {
  min-width: 0;
  flex: 1;
}

.conversation-top,
.conversation-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conversation-title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13.5px;
  font-weight: 700;
  color: #26383f;
}

.conversation-time {
  flex: none;
  font-size: 10px;
  color: #96a6ac;
}

.conversation-bottom {
  margin-top: 4px;
}

.conversation-preview {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 11.5px;
  color: #81939a;
}

.unread-badge {
  flex: none;
}

.im-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbfa 100%);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid #e8eff1;
  background: rgba(255, 255, 255, 0.86);
}

.chat-title {
  font-size: 17px;
  font-weight: 800;
  color: #1b3d49;
}

.chat-members {
  margin-top: 3px;
  max-width: 640px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 11px;
  color: #83959c;
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px;
}

.message-list {
  max-width: 900px;
  margin: 0 auto;
}

.message-row {
  display: flex;
  gap: 9px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.message-row.mine {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  font-size: 12px;
  margin-top: 2px;
}

.message-body {
  max-width: 72%;
}

.message-row.mine .message-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 10.5px;
  color: #8b9ca3;
}

.message-row.mine .message-meta {
  flex-direction: row-reverse;
}

.bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 4px 14px 14px 14px;
  background: #fff;
  border: 1px solid #e5edef;
  box-shadow: 0 3px 10px rgba(24, 58, 69, 0.04);
  color: #26383f;
  font-size: 13.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
}

.message-row.mine .bubble {
  border: none;
  border-radius: 14px 4px 14px 14px;
  background: linear-gradient(135deg, #2c6e7a, #174a5b);
  color: #fff;
  box-shadow: 0 5px 16px rgba(23, 74, 91, 0.16);
}

.message-status {
  min-height: 16px;
  margin-top: 3px;
  font-size: 10px;
  color: #9aaab0;
}

.seq {
  margin-left: 5px;
  color: #b5c3c8;
}

.composer {
  padding: 10px 18px 14px;
  border-top: 1px solid #e8eff1;
  background: #fff;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.composer-hint {
  font-size: 11px;
  color: #8b9ca3;
}

.empty-main {
  flex: 1;
  display: grid;
  place-items: center;
}

.member-checkboxes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

@media (max-width: 860px) {
  .im-sidebar {
    width: 240px;
  }

  .message-body {
    max-width: 80%;
  }

  .chat-members {
    max-width: 300px;
  }
}
</style>
