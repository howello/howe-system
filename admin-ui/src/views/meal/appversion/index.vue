<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="版本名称" prop="versionName">
        <el-input v-model="queryParams.versionName" placeholder="请输入版本名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="发布状态" clearable style="width: 200px">
          <el-option label="已发布" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['meal:appVersion:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['meal:appVersion:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['meal:appVersion:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="versionList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="版本名称" align="left" prop="versionName" :show-overflow-tooltip="true" min-width="120">
        <template #default="scope">
          <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.versionName }}</a>
        </template>
      </el-table-column>
      <el-table-column label="版本序号" align="center" prop="versionCode" width="100" />
      <el-table-column label="资源包大小" align="center" width="110">
        <template #default="scope">{{ formatSize(scope.row.wgtSize) }}</template>
      </el-table-column>
      <el-table-column label="更新类型" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.forceUpdate === '1' ? 'danger' : 'info'" size="small">
            {{ scope.row.forceUpdate === "1" ? "强制" : "普通" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">
            {{ scope.row.status === "0" ? "已发布" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新说明" align="left" prop="updateLog" :show-overflow-tooltip="true" min-width="200" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['meal:appVersion:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['meal:appVersion:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="版本名称" prop="versionName">
          <el-input v-model="form.versionName" placeholder="如 1.0.4" maxlength="32" />
        </el-form-item>
        <el-form-item label="版本序号" prop="versionCode">
          <el-input-number v-model="form.versionCode" :min="1" controls-position="right" />
          <span class="form-tip">整数递增，客户端按它判断版本先后</span>
        </el-form-item>
        <el-form-item label="资源包" prop="wgtUrl">
          <el-upload :show-file-list="false" :before-upload="beforeWgtUpload" :http-request="handleWgtUpload" accept=".wgt">
            <el-button type="primary" :loading="uploading" v-hasPermi="['meal:appVersion:upload']">选择 wgt 文件</el-button>
          </el-upload>
          <div v-if="form.wgtUrl" class="wgt-file">
            <el-link :href="form.wgtUrl" target="_blank" type="primary">{{ form.fileName || form.wgtUrl }}</el-link>
            <span class="form-tip">{{ formatSize(form.wgtSize) }}</span>
          </div>
        </el-form-item>
        <el-form-item label="更新说明" prop="updateLog">
          <el-input v-model="form.updateLog" type="textarea" :rows="4" placeholder="每行一条，客户端弹窗按行展示" />
        </el-form-item>
        <el-form-item label="强制更新" prop="forceUpdate">
          <el-switch v-model="form.forceUpdate" active-value="1" inactive-value="0" />
          <span class="form-tip">开启后客户端弹窗不可关闭，必须更新后才能继续使用</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">已发布</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
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

<script setup lang="ts" name="MealAppVersion">
import { getAppVersion, listAppVersion, addAppVersion, updateAppVersion, delAppVersion, uploadAppVersionWgt } from "@/api/meal/appversion"
import type { MealAppVersion, MealAppVersionQueryParams } from "@/types/api/meal/appversion"

const { proxy } = getCurrentInstance() as any

const versionList = ref<MealAppVersion[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const uploading = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

const data = reactive({
  form: {} as MealAppVersion,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    versionName: undefined,
    status: undefined
  } as MealAppVersionQueryParams,
  rules: {
    versionName: [{ required: true, message: "版本名称不能为空", trigger: "blur" }],
    versionCode: [{ required: true, message: "版本序号不能为空", trigger: "blur" }],
    wgtUrl: [{ required: true, message: "请先上传 wgt 资源包", trigger: "change" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 资源包大小展示：B / KB / MB */
function formatSize(size?: number): string {
  if (!size || size <= 0) {
    return "-"
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function getList() {
  loading.value = true
  listAppVersion(queryParams.value)
    .then((response: any) => {
      versionList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    versionId: undefined,
    versionName: undefined,
    versionCode: undefined,
    wgtUrl: undefined,
    wgtSize: undefined,
    fileName: undefined,
    updateLog: undefined,
    forceUpdate: "0",
    status: "0",
    remark: undefined
  }
  proxy.resetForm("formRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection: MealAppVersion[]) {
  ids.value = selection.map((item) => item.versionId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "新增 App 版本"
}

function handleUpdate(row?: MealAppVersion) {
  reset()
  const versionId = row?.versionId || ids.value[0]
  getAppVersion(versionId).then((response: any) => {
    form.value = response.data
    open.value = true
    title.value = "修改 App 版本"
  })
}

/** 上传前校验：只允许 .wgt，且不超过服务端 multipart 上限（10MB） */
function beforeWgtUpload(file: File): boolean {
  if (!file.name.toLowerCase().endsWith(".wgt")) {
    proxy.$modal.msgError("只能上传 .wgt 资源包")
    return false
  }
  if (file.size / 1024 / 1024 >= 10) {
    proxy.$modal.msgError("资源包大小不能超过 10MB")
    return false
  }
  return true
}

/** 上传 wgt 到后端（存 R2），成功后回填地址、大小与文件名 */
function handleWgtUpload(options: { file: File }): Promise<void> {
  uploading.value = true
  return uploadAppVersionWgt(options.file)
    .then((res) => {
      const data = res.data
      if (!data || !data.url) {
        proxy.$modal.msgError("上传响应缺少资源包地址")
        return
      }
      form.value.wgtUrl = data.url
      form.value.wgtSize = data.size
      form.value.fileName = data.fileName
      proxy.$modal.msgSuccess("资源包上传成功")
    })
    .finally(() => {
      uploading.value = false
    })
}

function submitForm() {
  proxy.$refs["formRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const payload: MealAppVersion = { ...form.value }
    const action = payload.versionId ? updateAppVersion(payload) : addAppVersion(payload)
    action
      .then(() => {
        proxy.$modal.msgSuccess(payload.versionId ? "修改成功" : "新增成功")
        open.value = false
        getList()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

function handleDelete(row?: MealAppVersion) {
  const versionIds = row?.versionId ? [row.versionId] : ids.value
  proxy.$modal
    .confirm("是否确认删除选中的版本记录？")
    .then(() => delAppVersion(versionIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    })
    .catch(() => {})
}

getList()
</script>

<style scoped lang="scss">
.form-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.wgt-file {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
