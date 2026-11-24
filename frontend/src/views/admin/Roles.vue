<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-note">
        <b>RBAC1</b> 角色沿继承链自动获得基线角色权限（树根为基线角色）；修改继承关系会重建闭包表并使旧刷新令牌失效。
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建角色</el-button>
    </div>

    <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all
      :tree-props="{ children: 'children' }" class="data-table">
      <el-table-column label="角色" min-width="220">
        <template #default="{ row }">
          <div class="role-name">
            <span class="strong">{{ row.name }}</span>
            <span class="role-code">{{ row.code }}</span>
            <el-tag v-if="row.builtIn" size="small" type="info" effect="plain">内置</el-tag>
          </div>
          <div class="muted">{{ row.description || '无描述' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="继承自" width="150">
        <template #default="{ row }">{{ parentName(row.parentId) }}</template>
      </el-table-column>
      <el-table-column label="成员" width="110">
        <template #default="{ row }">
          {{ row.memberCount }}<span v-if="row.maxMembers" class="muted"> / {{ row.maxMembers }}</span>
        </template>
      </el-table-column>
      <el-table-column label="前置角色" min-width="150">
        <template #default="{ row }">
          <span v-if="requiredRoleName(row.id)" class="muted">{{ requiredRoleName(row.id) }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPermissions(row)">分配权限</el-button>
          <template v-if="canWrite">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :disabled="row.builtIn" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formDialog" :title="form.id ? '编辑角色' : '新建角色'" width="600px">
      <el-form label-position="top">
        <div class="grid-2">
          <el-form-item label="角色编码">
            <el-input v-model="form.code" :disabled="Boolean(form.id)" placeholder="ROLE_CONTENT_REVIEWER" />
          </el-form-item>
          <el-form-item label="角色名称">
            <el-input v-model="form.name" placeholder="内容审核员" />
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="职责说明" />
        </el-form-item>
        <div class="grid-2">
          <el-form-item label="继承自角色（基线）">
            <el-select v-model="form.parentId" clearable placeholder="不继承其他角色" style="width: 100%">
              <el-option v-for="role in parentOptions" :key="role.id" :label="`${role.name}（${role.code}）`"
                :value="role.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="直接成员上限">
            <el-input-number v-model="form.maxMembers" :min="1" :max="10000" controls-position="right"
              placeholder="不限制" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="grid-2">
          <el-form-item label="前置角色">
            <el-select v-model="form.requiredRoleId" clearable placeholder="无前置要求" style="width: 100%">
              <el-option v-for="role in requiredOptions" :key="role.id" :label="`${role.name}（${role.code}）`"
                :value="role.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="1" :max="9999" controls-position="right"
              style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>
      <p class="hint">保存时后端会校验继承关系不能成环；成员上限不得低于当前成员数。</p>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionDialog" title="分配权限" width="640px">
      <div class="perm-head">
        <b>{{ permissionTarget?.name }}</b>
        <span class="muted">{{ permissionTarget?.code }}</span>
      </div>
      <el-tree ref="permissionTreeRef" :data="permissionTree" node-key="id" show-checkbox default-expand-all
        :props="{ label: 'name', children: 'children' }" class="perm-tree">
        <template #default="{ data }">
          <span class="perm-node">
            <span>{{ data.name }}</span>
            <code>{{ data.code }}</code>
            <el-tag size="small" effect="plain" type="info">{{ data.type }}</el-tag>
          </span>
        </template>
      </el-tree>
      <p class="hint">勾选父节点会同时勾选子节点；父节点权限仅用于菜单分组，接口鉴权以叶子权限为准。</p>
      <template #footer>
        <el-button @click="permissionDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  getRolePermissions,
  getRoleTree,
  listPermissions,
  listRoles,
  updateRole
} from '../../api/rbac'
import { useAuthStore } from '../../stores/authStore'

const auth = useAuthStore()
const canWrite = computed(() => auth.hasPerm('rbac:role:write'))

const tree = ref([])
const flat = ref([])
const permissionTree = ref([])
const loading = ref(false)
const saving = ref(false)

const formDialog = ref(false)
const permissionDialog = ref(false)
const permissionTarget = ref(null)
const permissionTreeRef = ref(null)

const form = reactive({
  id: null,
  code: '',
  name: '',
  description: '',
  parentId: null,
  maxMembers: null,
  requiredRoleId: null,
  sortOrder: 100
})

const parentOptions = computed(() => flat.value.filter((role) => role.id !== form.id))
const requiredOptions = computed(() => flat.value.filter((role) => role.id !== form.id))

function parentName(parentId) {
  if (!parentId) return '—'
  return flat.value.find((role) => role.id === parentId)?.name || '—'
}

function requiredRoleName(roleId) {
  return flat.value.find((item) => item.id === roleId)?.requiredRoleName || ''
}

async function load() {
  loading.value = true
  try {
    const [treeResult, flatResult] = await Promise.all([getRoleTree(), listRoles()])
    tree.value = treeResult.data || []
    flat.value = flatResult.data || []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: null,
    code: '',
    name: '',
    description: '',
    parentId: null,
    maxMembers: null,
    requiredRoleId: null,
    sortOrder: 100
  })
}

function openCreate() {
  resetForm()
  formDialog.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    code: row.code,
    name: row.name,
    description: row.description || '',
    parentId: row.parentId,
    maxMembers: row.maxMembers,
    requiredRoleId: row.requiredRoleId,
    sortOrder: row.sortOrder || 100
  })
  formDialog.value = true
}

async function submit() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写角色编码和名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      code: form.code,
      name: form.name,
      description: form.description,
      parentId: form.parentId || null,
      maxMembers: form.maxMembers || null,
      requiredRoleId: form.requiredRoleId || null,
      sortOrder: form.sortOrder || 100
    }
    if (form.id) await updateRole(form.id, payload)
    else await createRole(payload)
    formDialog.value = false
    ElMessage.success('角色已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, '删除角色', { type: 'warning' })
  try {
    await deleteRole(row.id)
    ElMessage.success('角色已删除')
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function openPermissions(row) {
  permissionTarget.value = row
  if (!permissionTree.value.length) {
    const result = await listPermissions()
    permissionTree.value = result.data || []
  }
  const assigned = await getRolePermissions(row.id)
  permissionDialog.value = true
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys(assigned.data || [])
}

async function submitPermissions() {
  saving.value = true
  try {
    const checked = permissionTreeRef.value?.getCheckedKeys() || []
    await assignRolePermissions(permissionTarget.value.id, checked)
    permissionDialog.value = false
    ElMessage.success('权限已更新，拥有该角色的用户需重新登录')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
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
  line-height: 1.6;
}

.toolbar-note b {
  color: #1d4ed8;
}

.data-table {
  width: 100%;
}

.role-name {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  vertical-align: middle;
}

.strong {
  color: #0f172a;
  font-weight: 600;
  font-size: 13px;
}

.role-code {
  font-size: 11px;
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

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.hint {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
  margin-top: 4px;
}

.perm-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.perm-tree {
  max-height: 420px;
  overflow: auto;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 8px;
}

.perm-node {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.perm-node code {
  font-size: 10px;
  color: #94a3b8;
}

.perm-node :deep(.el-tag) {
  transform: scale(0.85);
}

@media (max-width: 720px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
