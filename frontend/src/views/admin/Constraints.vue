<template>
  <div>
    <div class="intro-grid">
      <div class="intro-card ssd">
        <div class="intro-tag">SSD</div>
        <b>静态职责分离</b>
        <p>授权时校验：同一用户的直接/继承角色在同一约束内不得超过阈值，防止权限过度集中。</p>
        <span>示例：系统管理员与安全审计员互斥</span>
      </div>
      <div class="intro-card dsd">
        <div class="intro-tag">DSD</div>
        <b>动态职责分离</b>
        <p>签发/切换会话时校验：单次会话只能激活互斥角色之一，实现 maker-checker 双人复核。</p>
        <span>示例：内容审核员与资源发布员不能同时激活</span>
      </div>
      <div class="intro-card card">
        <div class="intro-tag">CARDINALITY</div>
        <b>基数与前置角色</b>
        <p>角色可设置直接成员上限与前置角色，在“用户管理 → 分配角色”时强制校验。</p>
        <span>示例：系统管理员最多 3 人；发布员须先具备教师角色</span>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-note">约束变更会立即影响后续授权与会话激活，历史审计完整留痕。</div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建约束</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" class="data-table">
      <el-table-column label="约束" min-width="230">
        <template #default="{ row }">
          <div class="constraint-name">
            <span class="strong">{{ row.name }}</span>
            <code>{{ row.code }}</code>
          </div>
          <div class="muted">{{ row.description || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.type === 'SSD' ? 'danger' : 'warning'" size="small" effect="plain">
            {{ row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阈值" width="80">
        <template #default="{ row }">≤ {{ row.maxRoles }}</template>
      </el-table-column>
      <el-table-column label="涉及角色" min-width="220">
        <template #default="{ row }">
          <div class="role-list">
            <el-tag v-for="role in row.roles" :key="role.code" size="small">{{ role.name }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑约束' : '新建约束'" width="620px">
      <el-form label-position="top">
        <div class="grid-2">
          <el-form-item label="约束编码">
            <el-input v-model="form.code" :disabled="Boolean(form.id)" placeholder="SSD_ADMIN_AUDITOR" />
          </el-form-item>
          <el-form-item label="约束名称">
            <el-input v-model="form.name" placeholder="管理员与审计员互斥" />
          </el-form-item>
        </div>
        <div class="grid-3">
          <el-form-item label="类型">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="SSD 静态" value="SSD" />
              <el-option label="DSD 动态" value="DSD" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许角色数">
            <el-input-number v-model="form.maxRoles" :min="1" :max="20" controls-position="right"
              style="width: 100%" />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>
        <el-form-item label="涉及角色（至少两个）">
          <el-checkbox-group v-model="form.roleIds" class="role-grid">
            <el-checkbox v-for="role in roles" :key="role.id" :value="role.id" border>
              {{ role.name }} <span class="role-code">{{ role.code }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="约束的业务含义" />
        </el-form-item>
      </el-form>
      <p class="hint">阈值必须小于勾选角色总数；例如 2 个互斥角色、阈值 1。</p>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createConstraint,
  deleteConstraint,
  listConstraints,
  listRoles,
  updateConstraint
} from '../../api/rbac'
import { useAuthStore } from '../../stores/authStore'

const auth = useAuthStore()
const canWrite = computed(() => auth.hasPerm('rbac:constraint:write'))

const rows = ref([])
const roles = ref([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const form = reactive({
  id: null, code: '', name: '', type: 'SSD', maxRoles: 1, description: '', enabled: true, roleIds: []
})

async function load() {
  loading.value = true
  try {
    const [constraints, roleList] = await Promise.all([listConstraints(), listRoles()])
    rows.value = constraints.data || []
    roles.value = roleList.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, code: '', name: '', type: 'SSD', maxRoles: 1, description: '', enabled: true, roleIds: []
  })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    code: row.code,
    name: row.name,
    type: row.type,
    maxRoles: row.maxRoles,
    description: row.description || '',
    enabled: row.enabled,
    roleIds: (row.roles || []).map((role) => role.id)
  })
  dialog.value = true
}

async function submit() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写约束编码和名称')
    return
  }
  if (form.roleIds.length < 2) {
    ElMessage.warning('职责分离约束至少需要两个角色')
    return
  }
  if (form.maxRoles >= form.roleIds.length) {
    ElMessage.warning('允许角色数必须小于角色总数')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, roleIds: [...form.roleIds] }
    if (form.id) await updateConstraint(form.id, payload)
    else await createConstraint(payload)
    dialog.value = false
    ElMessage.success('约束已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除约束「${row.name}」？`, '删除约束', { type: 'warning' })
  try {
    await deleteConstraint(row.id)
    ElMessage.success('约束已删除')
    await load()
  } catch {
    // 拦截器提示
  }
}

onMounted(load)
</script>

<style scoped>
.intro-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.intro-card {
  border: 1px solid #e8edf5;
  border-radius: 12px;
  padding: 14px 16px;
  background: #fbfdff;
}

.intro-card.ssd {
  border-left: 3px solid #dc2626;
}

.intro-card.dsd {
  border-left: 3px solid #d97706;
}

.intro-card.card {
  border-left: 3px solid #2563eb;
}

.intro-tag {
  font-size: 10px;
  letter-spacing: 1px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.intro-card b {
  color: #0f172a;
  font-size: 13px;
}

.intro-card p {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
  margin: 6px 0;
}

.intro-card span {
  color: #94a3b8;
  font-size: 11px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.toolbar-note {
  color: #64748b;
  font-size: 12px;
}

.data-table {
  width: 100%;
}

.constraint-name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.strong {
  color: #0f172a;
  font-weight: 600;
  font-size: 13px;
}

.constraint-name code {
  font-size: 10px;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 5px;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
  margin-top: 3px;
}

.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.grid-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 100px;
  gap: 14px;
}

.role-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  width: 100%;
}

.role-grid :deep(.el-checkbox) {
  margin-right: 0;
  width: 100%;
}

.role-code {
  font-size: 10px;
  color: #94a3b8;
  margin-left: 4px;
}

.hint {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .intro-grid {
    grid-template-columns: 1fr;
  }

  .grid-2,
  .grid-3,
  .role-grid {
    grid-template-columns: 1fr;
  }
}
</style>
