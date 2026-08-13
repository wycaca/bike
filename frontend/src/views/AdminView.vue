<script setup lang="ts">
import { Edit, Key, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import { getAuditLogs, getOrganizations, getUsers, resetUserPassword, saveOrganization, saveUser } from '@/api/admin'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type {
  AuditLog,
  DataScope,
  Organization,
  OrganizationType,
  PlatformUser,
  RecordStatus,
  UserRole,
} from '@/types/operations'
import { actionLabels, auditTime, dataScopeLabels, organizationPath, roleLabels } from '@/utils/operations'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.hasRole('ADMIN'))
const activeTab = ref(isAdmin.value ? 'organizations' : 'audit')
const loading = ref(false)
const error = ref('')
const organizations = ref<Organization[]>([])
const users = ref<PlatformUser[]>([])
const auditLogs = ref<AuditLog[]>([])
const userTotal = ref(0)
const auditTotal = ref(0)
const userPage = ref(1)
const auditPage = ref(1)
const pageSize = ref(20)
const userKeyword = ref('')
const auditKeyword = ref('')
const auditAction = ref('')
const orgDialogVisible = ref(false)
const userDialogVisible = ref(false)
const editingOrgId = ref<string | null>(null)
const editingUserId = ref<string | null>(null)

const orgForm = reactive({
  parentOrgId: null as string | null,
  orgName: '',
  orgType: 'REGION' as OrganizationType,
  cityCode: '',
  status: 'ACTIVE' as RecordStatus,
})
const userForm = reactive({
  username: '', displayName: '', phone: '', orgId: '',
  role: 'OPERATOR' as UserRole, dataScope: 'ORG_ONLY' as DataScope,
  status: 'ACTIVE' as RecordStatus, password: '',
})

const organizationTypeLabels: Record<OrganizationType, string> = {
  COMPANY: '公司', REGION: '区域中心', TEAM: '运营班组',
}

/** 输入: 无; 输出: 当前全部组织。 */
async function loadOrganizations() {
  if (!isAdmin.value) return
  organizations.value = await getOrganizations()
}

/** 输入: 用户分页与关键字; 输出: 用户分页数据。 */
async function loadUsers() {
  if (!isAdmin.value) return
  const result = await getUsers(userPage.value, pageSize.value, userKeyword.value.trim())
  users.value = result.items
  userTotal.value = result.total
}

/** 输入: 审计筛选和分页; 输出: 审计日志分页数据。 */
async function loadAudit() {
  const result = await getAuditLogs(auditPage.value, pageSize.value, auditKeyword.value.trim(), auditAction.value)
  auditLogs.value = result.items
  auditTotal.value = result.total
}

/** 输入: 当前页签; 输出: 页签所需数据。 */
async function loadCurrentTab() {
  loading.value = true
  error.value = ''
  try {
    if (activeTab.value === 'organizations') await loadOrganizations()
    else if (activeTab.value === 'users') {
      await loadOrganizations()
      await loadUsers()
    } else await loadAudit()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

/** 输入: 可选现有组织; 输出: 打开新建或编辑对话框。 */
function openOrganization(org?: Organization) {
  editingOrgId.value = org?.orgId ?? null
  orgForm.parentOrgId = org?.parentOrgId ?? null
  orgForm.orgName = org?.orgName ?? ''
  orgForm.orgType = org?.orgType ?? 'REGION'
  orgForm.cityCode = org?.cityCode ?? ''
  orgForm.status = org?.status ?? 'ACTIVE'
  orgDialogVisible.value = true
}

/** 输入: 组织表单; 输出: 新建或更新组织。 */
async function submitOrganization() {
  if (!orgForm.orgName.trim()) return
  try {
    await saveOrganization({ ...orgForm, orgName: orgForm.orgName.trim() }, editingOrgId.value ?? undefined)
    ElMessage.success(editingOrgId.value ? '组织已更新' : '组织已创建')
    orgDialogVisible.value = false
    await loadOrganizations()
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 可选现有用户; 输出: 打开新建或编辑对话框。 */
function openUser(user?: PlatformUser) {
  editingUserId.value = user?.userId ?? null
  userForm.username = user?.username ?? ''
  userForm.displayName = user?.displayName ?? ''
  userForm.phone = user?.phone ?? ''
  userForm.orgId = user?.orgId ?? organizations.value[0]?.orgId ?? ''
  userForm.role = user?.role ?? 'OPERATOR'
  userForm.dataScope = user?.dataScope ?? defaultDataScope(userForm.role)
  userForm.status = user?.status ?? 'ACTIVE'
  userForm.password = ''
  userDialogVisible.value = true
}

/** 输入: 用户角色; 输出: 新建或切换角色时使用的数据范围默认值. */
function defaultDataScope(role: UserRole): DataScope {
  if (role === 'ADMIN') return 'ALL'
  return role === 'AUDITOR' ? 'ORG_AND_CHILDREN' : 'ORG_ONLY'
}

/** 输入: 用户表单; 输出: 新建或更新用户。 */
async function submitUser() {
  if (!userForm.username.trim() || !userForm.displayName.trim() || !userForm.orgId) return
  if (!editingUserId.value && userForm.password.length < 8) {
    ElMessage.warning('新建用户的初始密码至少 8 位')
    return
  }
  try {
    await saveUser({
      ...userForm,
      username: userForm.username.trim(),
      displayName: userForm.displayName.trim(),
      password: userForm.password || null,
    }, editingUserId.value ?? undefined)
    ElMessage.success(editingUserId.value ? '用户已更新' : '用户已创建')
    userDialogVisible.value = false
    await loadUsers()
  } catch (cause) {
    ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 目标用户; 输出: 确认后更新密码摘要。 */
async function resetPassword(user: PlatformUser) {
  try {
    const result = await ElMessageBox.prompt(`为 ${user.displayName} 设置新密码`, '重置密码', {
      inputType: 'password',
      inputPattern: /^.{8,64}$/,
      inputErrorMessage: '密码长度需为 8 到 64 位',
      confirmButtonText: '确认重置',
    })
    await resetUserPassword(user.userId, result.value)
    ElMessage.success('密码已重置')
  } catch (cause) {
    if (cause === 'cancel' || cause === 'close') return
    ElMessage.error(errorMessage(cause))
  }
}

/** 输入: 当前用户关键字; 输出: 从第一页重新查询用户。 */
function searchUsers() { userPage.value = 1; void loadCurrentTab() }
/** 输入: 当前审计筛选; 输出: 从第一页重新查询日志。 */
function searchAudit() { auditPage.value = 1; void loadCurrentTab() }
watch(activeTab, loadCurrentTab)
onMounted(loadCurrentTab)
</script>

<template>
  <div class="page-view admin-page">
    <div class="admin-heading">
      <div class="page-heading">
        <div><h1>用户、组织与审计</h1><p>维护平台访问范围并追踪关键操作</p></div>
        <el-button :icon="Refresh" circle aria-label="刷新" :loading="loading" @click="loadCurrentTab" />
      </div>
    </div>

    <div class="admin-body">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="isAdmin" label="组织架构" name="organizations">
          <div class="tab-toolbar">
            <span>{{ organizations.length }} 个组织</span>
            <el-button type="primary" :icon="Plus" @click="openOrganization()">新建组织</el-button>
          </div>
          <el-table v-loading="loading" :data="organizations" row-key="orgId" stripe>
            <el-table-column prop="orgName" label="组织名称" min-width="180" />
            <el-table-column label="层级路径" min-width="260"><template #default="scope">{{ organizationPath(scope.row.orgId, organizations) }}</template></el-table-column>
            <el-table-column label="类型" width="110"><template #default="scope">{{ organizationTypeLabels[scope.row.orgType as OrganizationType] }}</template></el-table-column>
            <el-table-column prop="cityCode" label="城市代码" width="110"><template #default="scope">{{ scope.row.cityCode || '--' }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="76" align="center"><template #default="scope"><el-tooltip content="编辑组织"><el-button :icon="Edit" text circle aria-label="编辑组织" @click="openOrganization(scope.row)" /></el-tooltip></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="isAdmin" label="用户账号" name="users">
          <div class="tab-toolbar">
            <el-input v-model="userKeyword" clearable placeholder="用户名、姓名或手机号" :prefix-icon="Search" style="width: 260px" @keyup.enter="searchUsers" @clear="searchUsers" />
            <el-button type="primary" :icon="Search" @click="searchUsers">查询</el-button>
            <span class="toolbar-spacer" />
            <el-button type="primary" :icon="Plus" @click="openUser()">新建用户</el-button>
          </div>
          <el-table v-loading="loading" :data="users" stripe>
            <el-table-column prop="username" label="用户名" min-width="130" />
            <el-table-column prop="displayName" label="姓名" min-width="120" />
            <el-table-column prop="orgName" label="所属组织" min-width="160" />
            <el-table-column label="角色" width="110"><template #default="scope">{{ roleLabels[scope.row.role as UserRole] }}</template></el-table-column>
            <el-table-column label="数据范围" width="130"><template #default="scope">{{ dataScopeLabels[scope.row.dataScope as DataScope] }}</template></el-table-column>
            <el-table-column prop="phone" label="手机号" width="130"><template #default="scope">{{ scope.row.phone || '--' }}</template></el-table-column>
            <el-table-column label="最近登录" min-width="170"><template #default="scope">{{ auditTime(scope.row.lastLoginAt) }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="100" align="center"><template #default="scope"><el-tooltip content="编辑用户"><el-button :icon="Edit" text circle aria-label="编辑用户" @click="openUser(scope.row)" /></el-tooltip><el-tooltip content="重置密码"><el-button :icon="Key" text circle aria-label="重置密码" @click="resetPassword(scope.row)" /></el-tooltip></template></el-table-column>
          </el-table>
          <div class="admin-pagination"><el-pagination v-model:current-page="userPage" background layout="total, prev, pager, next" :total="userTotal" :page-size="pageSize" @current-change="loadUsers" /></div>
        </el-tab-pane>

        <el-tab-pane label="审计日志" name="audit">
          <div class="tab-toolbar">
            <el-input v-model="auditKeyword" clearable placeholder="用户名、路径或资源编号" :prefix-icon="Search" style="width: 270px" @keyup.enter="searchAudit" @clear="searchAudit" />
            <el-select v-model="auditAction" clearable placeholder="全部操作" style="width: 120px" @change="searchAudit">
              <el-option v-for="(label, value) in actionLabels" :key="value" :label="label" :value="value" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="searchAudit">查询</el-button>
          </div>
          <el-table v-loading="loading" :data="auditLogs" stripe>
            <el-table-column label="时间" width="174"><template #default="scope">{{ auditTime(scope.row.createdAt) }}</template></el-table-column>
            <el-table-column prop="username" label="操作人" width="110"><template #default="scope">{{ scope.row.username || 'anonymous' }}</template></el-table-column>
            <el-table-column label="动作" width="88"><template #default="scope"><el-tag size="small" :type="scope.row.statusCode < 400 ? 'success' : 'danger'">{{ actionLabels[scope.row.action] ?? scope.row.action }}</el-tag></template></el-table-column>
            <el-table-column prop="resourceType" label="资源" width="110" />
            <el-table-column prop="requestPath" label="请求路径" min-width="260" show-overflow-tooltip />
            <el-table-column prop="clientIp" label="来源 IP" width="130" />
            <el-table-column prop="statusCode" label="状态码" width="86" align="right" />
            <el-table-column prop="durationMs" label="耗时(ms)" width="96" align="right" />
          </el-table>
          <div class="admin-pagination"><el-pagination v-model:current-page="auditPage" background layout="total, prev, pager, next" :total="auditTotal" :page-size="pageSize" @current-change="loadAudit" /></div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="orgDialogVisible" :title="editingOrgId ? '编辑组织' : '新建组织'" width="480px">
      <el-form label-position="top">
        <el-form-item label="组织名称"><el-input v-model="orgForm.orgName" maxlength="64" /></el-form-item>
        <el-form-item label="上级组织"><el-select v-model="orgForm.parentOrgId" clearable placeholder="无上级组织" style="width: 100%"><el-option v-for="org in organizations.filter((item) => item.orgId !== editingOrgId)" :key="org.orgId" :label="organizationPath(org.orgId, organizations)" :value="org.orgId" /></el-select></el-form-item>
        <div class="form-columns"><el-form-item label="组织类型"><el-select v-model="orgForm.orgType" style="width: 100%"><el-option v-for="(label, value) in organizationTypeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="城市代码"><el-input v-model="orgForm.cityCode" maxlength="6" placeholder="可留空" /></el-form-item></div>
        <el-form-item label="状态"><el-switch v-model="orgForm.status" active-value="ACTIVE" inactive-value="DISABLED" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="orgDialogVisible = false">取消</el-button><el-button type="primary" @click="submitOrganization">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="userDialogVisible" :title="editingUserId ? '编辑用户' : '新建用户'" width="520px">
      <el-form label-position="top">
        <div class="form-columns"><el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="Boolean(editingUserId)" /></el-form-item><el-form-item label="姓名"><el-input v-model="userForm.displayName" /></el-form-item></div>
        <el-form-item label="所属组织"><el-select v-model="userForm.orgId" filterable style="width: 100%"><el-option v-for="org in organizations.filter((item) => item.status === 'ACTIVE')" :key="org.orgId" :label="organizationPath(org.orgId, organizations)" :value="org.orgId" /></el-select></el-form-item>
        <div class="form-columns"><el-form-item label="角色"><el-select v-model="userForm.role" style="width: 100%" @change="userForm.dataScope = defaultDataScope(userForm.role)"><el-option v-for="(label, value) in roleLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="数据范围"><el-select v-model="userForm.dataScope" style="width: 100%"><el-option v-for="(label, value) in dataScopeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item></div>
        <el-form-item label="手机号"><el-input v-model="userForm.phone" maxlength="11" /></el-form-item>
        <el-form-item v-if="!editingUserId" label="初始密码"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="userForm.status" active-value="ACTIVE" inactive-value="DISABLED" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="userDialogVisible = false">取消</el-button><el-button type="primary" @click="submitUser">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page { display: grid; grid-template-rows: 78px minmax(0, 1fr); background: #fff; }
.admin-heading { display: flex; align-items: center; padding: 0 18px; border-bottom: 1px solid var(--line); }.admin-heading .page-heading { width: 100%; }
.admin-body { min-height: 0; padding: 0 16px 14px; overflow: auto; }.admin-body > .el-alert { margin: 10px 0; }
.tab-toolbar { display: flex; align-items: center; gap: 9px; min-height: 54px; color: var(--muted); font-size: 12px; }.toolbar-spacer { flex: 1; }
.admin-body :deep(.el-table__header th) { background: #f1f4f2; }
.admin-pagination { display: flex; justify-content: flex-end; padding-top: 14px; }
.form-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
</style>
