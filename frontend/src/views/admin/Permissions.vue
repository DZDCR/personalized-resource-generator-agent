<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-note">
        权限编码即 Spring Security Authority，例如 <code>resource:review</code>；接口通过
        <code>@PreAuthorize</code> 校验。
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建权限</el-button>
    </div>

    <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all
      :tree-props="{ children: 'children' }" class="data-table">
      <el-table-column label="权限名称" min-width="240">
        <template #default="{ row }">
          <div class="perm-name">
            <span class="strong">{{ row.name }}</span>
            <code>{{ row.code }}</code>
          </div>
          <div class="muted">{{ row.description || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="typeTag(row.type)">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="父节点" width="160">
        <template #default="{ row }">{{ parentName(row.parentId) }}</template>
      </el-table-column>
      <el-table-column label="排序" width="80" prop="sortOrder" />
      <el-table-column label="内置" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.builtIn" size="small" type="info" effect="plain">内置</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.builtIn" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑权限' : '新建权限'" width="560px">
      <el-form label-position="top">
        <div class="grid-2">
          <el-form-item label="权限编码">
            <el-input v-model="form.code" :disabled="Boolean(form.id)" placeholder="resource:review" />
          </el-form-item>
          <el-form-item label="权限名称">
            <el-input v-model="form.name" placeholder="审核学习资源" />
          </el-form-item>
        </div>
        <div class="grid-2">
          <el-form-item label="类型">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="菜单 MENU" value="MENU" />
              <el-option label="接口 API" value="API" />
              <el-option label="按钮 BUTTON" value="BUTTON" />
            </el-select>
          </el-form-item>
          <el-form-item label="父节点">
            <el-select v-model="form.parentId" clearable placeholder="根节点" style="width: 100%">
              <el-option v-for="option in parentOptions" :key="option.id" :label="option.label" :value="option.id" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="权限用途" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <p class="hint">权限编码创建后不可修改，避免已签发令牌中的 Authority 失效。</p>
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
import { createPermission, deletePermission, listPermissions, updatePermission } from '../../api/rbac'
import { useAuthStore } from '../../stores/authStore'

const auth = useAuthStore()
const canWrite = computed(() => auth.hasPerm('rbac:permission:write'))

const tree = ref([])
const flat = ref([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const form = reactive({
  id: null, code: '', name: '', type: 'API', parentId: null, description: '', sortOrder: 100
})

const parentOptions = computed(() => flat.value.filter((item) => item.id !== form.id))

function flatten(nodes, depth = 0) {
  const result = []
  for (const node of nodes) {
    result.push({ id: node.id, name: node.name, label: `${'　'.repeat(depth)}${node.name}（${node.code}）` })
    if (node.children?.length) result.push(...flatten(node.children, depth + 1))
  }
  return result
}

function parentName(parentId) {
  if (!parentId) return '—'
  return flat.value.find((item) => item.id === parentId)?.name || '—'
}

function typeLabel(type) {
  return { MENU: '菜单', API: '接口', BUTTON: '按钮' }[type] || type
}

function typeTag(type) {
  return { MENU: 'success', API: 'primary', BUTTON: 'warning' }[type] || 'info'
}

async function load() {
  loading.value = true
  try {
    const result = await listPermissions()
    tree.value = result.data || []
    flat.value = flatten(tree.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, code: '', name: '', type: 'API', parentId: null, description: '', sortOrder: 100
  })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    code: row.code,
    name: row.name,
    type: row.type,
    parentId: row.parentId,
    description: row.description || '',
    sortOrder: row.sortOrder || 100
  })
  dialog.value = true
}

async function submit() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写权限编码和名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      code: form.code,
      name: form.name,
      type: form.type,
      parentId: form.parentId || null,
      description: form.description,
      sortOrder: form.sortOrder || 100
    }
    if (form.id) await updatePermission(form.id, payload)
    else await createPermission(payload)
    dialog.value = false
    ElMessage.success('权限已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除权限「${row.name}」？`, '删除权限', { type: 'warning' })
  try {
    await deletePermission(row.id)
    ElMessage.success('权限已删除')
    await load()
  } catch {
    // 拦截器已提示
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
  line-height: 1.7;
}

.toolbar-note code {
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 5px;
  color: #1d4ed8;
}

.data-table {
  width: 100%;
}

.perm-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  vertical-align: middle;
}

.strong {
  color: #0f172a;
  font-weight: 600;
  font-size: 13px;
}

.perm-name code {
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
}

@media (max-width: 720px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
