<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="家庭" prop="deptId" v-if="isPlatformAdmin">
        <el-tree-select
          v-model="queryParams.deptId"
          :data="deptOptions"
          :props="{ value: 'id', label: 'label', children: 'children' }"
          value-key="id"
          placeholder="全部家庭"
          clearable
          check-strictly
          style="width: 220px"
        />
      </el-form-item>
      <el-form-item label="启用状态" prop="enabled">
        <el-select v-model="queryParams.enabled" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="启用" value="1" />
          <el-option label="停用" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['meal:wecom:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['meal:wecom:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="configList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="家庭" align="left" prop="deptName" :show-overflow-tooltip="true" min-width="160">
        <template #default="scope">{{ scope.row.deptName || scope.row.deptId }}</template>
      </el-table-column>
      <el-table-column label="Webhook Key" align="left" prop="webhookKey" width="180">
        <template #default="scope">
          <span v-if="scope.row.webhookKey" class="wecom-mono">{{ scope.row.webhookKey }}</span>
          <span v-else class="wecom-muted">未配置</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" align="center" width="90">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            active-value="1"
            inactive-value="0"
            @change="handleSwitchChange(scope.row, 'enabled')"
            v-hasPermi="['meal:wecom:edit']"
          />
        </template>
      </el-table-column>
      <el-table-column label="订单通知" align="center" width="100">
        <template #default="scope">
          <el-switch
            v-model="scope.row.notifyOrder"
            active-value="1"
            inactive-value="0"
            @change="handleSwitchChange(scope.row, 'notifyOrder')"
            v-hasPermi="['meal:wecom:edit']"
          />
        </template>
      </el-table-column>
      <el-table-column label="AI 通知" align="center" width="100">
        <template #default="scope">
          <el-switch
            v-model="scope.row.notifyAi"
            active-value="1"
            inactive-value="0"
            @change="handleSwitchChange(scope.row, 'notifyAi')"
            v-hasPermi="['meal:wecom:edit']"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170">
        <template #default="scope">
          <span v-if="scope.row.updateTime">{{ parseTime(scope.row.updateTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
          <span v-else class="wecom-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="left" prop="remark" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="操作" align="center" width="220" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Connection" :loading="testingId === scope.row.configId" @click="handleTest(scope.row)" v-hasPermi="['meal:wecom:test']">测试</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['meal:wecom:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['meal:wecom:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="640px" append-to-body :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="家庭" prop="deptId">
          <el-tree-select
            v-if="isPlatformAdmin"
            v-model="form.deptId"
            :data="deptOptions"
            :props="{ value: 'id', label: 'label', children: 'children' }"
            value-key="id"
            placeholder="请选择家庭"
            check-strictly
            style="width: 100%"
          />
          <el-input v-else :model-value="currentDeptName" disabled />
          <div class="wecom-hint" v-if="isPlatformAdmin">每个家庭只能配置一个企业微信群机器人；家庭即部门。</div>
        </el-form-item>
        <el-form-item label="Webhook Key" prop="webhookKey">
          <el-input
            v-model="form.webhookKey"
            type="password"
            show-password
            :placeholder="form.configId ? '留空表示不修改原 Key' : '粘贴企业微信群机器人的 Webhook Key'"
            maxlength="128"
          />
          <div class="wecom-hint">
            只填 key（Webhook 地址中 <code>key=</code> 后面的部分），不填完整 URL；key 仅用于后端发送，接口不会返回明文。
          </div>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" active-value="1" inactive-value="0" />
          <span class="wecom-hint-inline">家庭级总开关，关闭后不发送任何企微通知</span>
        </el-form-item>
        <el-form-item label="订单通知" prop="notifyOrder">
          <el-switch v-model="form.notifyOrder" active-value="1" inactive-value="0" />
          <span class="wecom-hint-inline">下单、接单、完成、评价</span>
        </el-form-item>
        <el-form-item label="AI 通知" prop="notifyAi">
          <el-switch v-model="form.notifyAi" active-value="1" inactive-value="0" />
          <span class="wecom-hint-inline">菜品 AI 更新、提案 AI 审核结果</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MealWeCom">
import { addWeComConfig, delWeComConfig, getWeComConfig, listWeComConfig, testWeComConfig, updateWeComConfig } from "@/api/meal/wecom"
import { listDept } from "@/api/system/dept"
import useUserStore from "@/store/modules/user"
import type { MealWeComConfig, MealWeComConfigQueryParams } from "@/types/api/meal/wecom"
import type { SysDept } from "@/types/api/system/dept"

const { proxy } = getCurrentInstance() as any
const userStore = useUserStore()

const configList = ref<MealWeComConfig[]>([])
const deptOptions = ref<any[]>([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const submitting = ref(false)
const testingId = ref<number | null>(null)
const currentDeptName = ref("")

const isPlatformAdmin = computed(() => userStore.roles.includes("admin"))

const data = reactive({
  form: {} as MealWeComConfig,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    deptId: undefined,
    enabled: undefined
  } as MealWeComConfigQueryParams
})

const { queryParams, form } = toRefs(data)

/** 校验规则：只有平台管理员需要选择家庭，家庭管理员的家庭由后端按登录态固定 */
const rules = computed(() => ({
  deptId: isPlatformAdmin.value
    ? [{ required: true, message: "家庭不能为空", trigger: "change" }]
    : [],
  webhookKey: form.value.configId
    ? []
    : [{ required: true, message: "Webhook Key 不能为空", trigger: "blur" }],
  enabled: [{ required: true, message: "启用状态不能为空", trigger: "change" }],
  notifyOrder: [{ required: true, message: "订单通知开关不能为空", trigger: "change" }],
  notifyAi: [{ required: true, message: "AI 通知开关不能为空", trigger: "change" }]
}))

/** 查询配置列表 */
function getList() {
  loading.value = true
  listWeComConfig(queryParams.value).then((response: any) => {
    configList.value = response.rows || []
    total.value = response.total || 0
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 加载家庭（部门）树，供平台管理员选择 */
function getDeptOptions() {
  listDept().then((response: any) => {
    const list: SysDept[] = response.data || []
    const nodes = list.map((dept) => ({
      id: dept.deptId,
      label: dept.deptName,
      parentId: dept.parentId,
      disabled: dept.status === "1"
    }))
    deptOptions.value = proxy.handleTree(nodes, "id", "parentId")
  })
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置搜索 */
function resetQuery() {
  queryParams.value.deptId = undefined
  queryParams.value.enabled = undefined
  handleQuery()
}

/** 表格多选 */
function handleSelectionChange(selection: MealWeComConfig[]) {
  ids.value = selection.map((item) => item.configId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 重置表单 */
function reset() {
  form.value = {
    configId: undefined,
    deptId: undefined,
    webhookKey: undefined,
    enabled: "1",
    notifyOrder: "1",
    notifyAi: "1",
    remark: undefined
  }
  proxy.resetForm("formRef")
}

/** 新增 */
function handleAdd() {
  reset()
  if (!isPlatformAdmin.value) {
    // 家庭管理员固定本家庭：由后端按登录态兜底，这里仅用于展示
    currentDeptName.value = "本家庭"
  }
  open.value = true
  title.value = "新增企业微信通知"
}

/** 修改 */
function handleUpdate(row?: MealWeComConfig) {
  reset()
  const configId = row?.configId || ids.value[0]
  if (!configId) {
    return
  }
  getWeComConfig(configId).then((response: any) => {
    form.value = response.data
    currentDeptName.value = form.value.deptName || "本家庭"
    open.value = true
    title.value = "修改企业微信通知"
  })
}

/** 表单开关变更后立即保存 */
function handleSwitchChange(row: MealWeComConfig, field: "enabled" | "notifyOrder" | "notifyAi") {
  const payload: MealWeComConfig = {
    configId: row.configId,
    deptId: row.deptId,
    enabled: row.enabled,
    notifyOrder: row.notifyOrder,
    notifyAi: row.notifyAi,
    webhookKey: undefined,
    remark: row.remark
  }
  updateWeComConfig(payload).then(() => {
    proxy.$modal.msgSuccess("修改成功")
  }).catch(() => {
    // 失败时恢复原值，避免界面与服务端不一致
    row[field] = row[field] === "1" ? "0" : "1"
  })
}

/** 提交表单 */
function submitForm() {
  proxy.$refs["formRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const action = form.value.configId ? updateWeComConfig : addWeComConfig
    action(form.value).then(() => {
      proxy.$modal.msgSuccess(form.value.configId ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 取消 */
function cancel() {
  open.value = false
  reset()
}

/** 删除 */
function handleDelete(row?: MealWeComConfig) {
  const configIds = row?.configId ? [row.configId] : ids.value
  if (!configIds.length) {
    return
  }
  proxy.$modal.confirm('是否确认删除选中的企业微信通知配置？').then(() => {
    return delWeComConfig(configIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 测试发送 */
function handleTest(row: MealWeComConfig) {
  if (!row.configId) {
    return
  }
  testingId.value = row.configId
  testWeComConfig(row.configId).then((response: any) => {
    proxy.$modal.msgSuccess(response.msg || "测试消息已发送")
  }).catch(() => {
    // 失败提示由请求层统一展示
  }).finally(() => {
    testingId.value = null
  })
}

onMounted(() => {
  getDeptOptions()
  getList()
})
</script>

<style scoped>
.wecom-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.wecom-muted {
  color: var(--el-text-color-secondary);
}
.wecom-hint {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.wecom-hint-inline {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
