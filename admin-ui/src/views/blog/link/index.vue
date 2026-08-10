<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item label="名称" prop="linkName">
            <el-input v-model="queryParams.linkName" placeholder="请输入站点名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="分组" prop="groupCode">
            <el-select v-model="queryParams.groupCode" placeholder="友链分组" clearable style="width: 200px">
               <el-option v-for="dict in blog_link_group" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 200px">
               <el-option label="正常" value="0" />
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
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['blog:link:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['blog:link:edit']">修改</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['blog:link:remove']">删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="linkList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="图标" align="center" width="70">
            <template #default="scope">
               <el-avatar v-if="scope.row.avatar" :src="scope.row.avatar" :size="32" />
               <el-avatar v-else :size="32">{{ (scope.row.linkName || '?').charAt(0) }}</el-avatar>
            </template>
         </el-table-column>
         <el-table-column label="站点名称" align="left" prop="linkName" :show-overflow-tooltip="true" min-width="160">
            <template #default="scope">
               <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.linkName }}</a>
            </template>
         </el-table-column>
         <el-table-column label="站点地址" align="left" prop="linkUrl" :show-overflow-tooltip="true" min-width="200">
            <template #default="scope">
               <a v-if="scope.row.linkUrl" :href="scope.row.linkUrl" target="_blank" rel="noopener noreferrer" class="link-type">{{ scope.row.linkUrl }}</a>
            </template>
         </el-table-column>
         <el-table-column label="分组" align="center" width="110">
            <template #default="scope">
               <dict-tag :options="blog_link_group" :value="scope.row.groupCode" />
            </template>
         </el-table-column>
         <el-table-column label="描述" align="left" prop="descr" :show-overflow-tooltip="true" min-width="180" />
         <el-table-column label="排序" align="center" prop="orderNum" width="70" />
         <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
               <el-tag v-if="scope.row.status === '0'" type="success" size="small">正常</el-tag>
               <el-tag v-else type="info" size="small">停用</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['blog:link:edit']">修改</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['blog:link:remove']">删除</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination
         v-show="total > 0"
         :total="total"
         v-model:page="queryParams.pageNum"
         v-model:limit="queryParams.pageSize"
         @pagination="getList"
      />

      <!-- 新增/修改对话框 -->
      <el-dialog :title="title" v-model="open" width="680px" append-to-body :close-on-click-modal="false">
         <el-form ref="linkRef" :model="form" :rules="rules" label-width="90px">
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="站点名称" prop="linkName">
                     <el-input v-model="form.linkName" placeholder="请输入站点名称" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="分组" prop="groupCode">
                     <!-- value-on-clear 必须显式给空串：Element Plus 清空时默认 emit undefined，
                          axios 会把该键整个丢掉，后端 <if test="groupCode != null"> 随之跳过，
                          group_code 保留旧值——「留空则归入其他组」这个承诺就做不到 -->
                     <el-select v-model="form.groupCode" placeholder="留空则归入「其他」组" clearable value-on-clear="" style="width: 100%">
                        <el-option v-for="dict in blog_link_group" :key="dict.value" :label="dict.label" :value="dict.value" />
                     </el-select>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="站点地址" prop="linkUrl">
               <el-input v-model="form.linkUrl" placeholder="请输入完整地址，含 https://" />
            </el-form-item>
            <el-form-item label="图标地址" prop="avatar">
               <el-input v-model="form.avatar" placeholder="留空则站点页展示名称首字" />
            </el-form-item>
            <el-form-item label="站点描述" prop="descr">
               <el-input v-model="form.descr" type="textarea" :rows="2" placeholder="一句话介绍，会展示在友链卡片上" />
            </el-form-item>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="显示顺序" prop="orderNum">
                     <el-input-number v-model="form.orderNum" :min="0" controls-position="right" style="width: 100%" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="状态" prop="status">
                     <el-radio-group v-model="form.status">
                        <el-radio value="0">正常</el-radio>
                        <el-radio value="1">停用</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="备注" prop="remark">
               <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="仅后台可见" />
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

<script setup lang="ts" name="BlogLink">
import { listLink, getLink, delLink, addLink, updateLink } from "@/api/blog/link"
import type { BlogLink, BlogLinkQueryParams } from "@/types/api/blog/link"

const { proxy } = getCurrentInstance()

// 分组用字典而非自由文本，避免「技术」「技术 」变成两个组
const { blog_link_group } = useDict("blog_link_group")

const linkList = ref<BlogLink[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

const data = reactive({
  form: {} as BlogLink,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    linkName: undefined,
    groupCode: undefined,
    status: undefined
  } as BlogLinkQueryParams,
  rules: {
    linkName: [{ required: true, message: "站点名称不能为空", trigger: "blur" }],
    linkUrl: [{ pattern: /^$|^https?:\/\/.+/, message: "地址需以 http:// 或 https:// 开头", trigger: "blur" }],
    avatar: [{ pattern: /^$|^https?:\/\/.+/, message: "地址需以 http:// 或 https:// 开头", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询友链列表 */
function getList() {
  loading.value = true
  listLink(queryParams.value).then(response => {
    linkList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    linkId: undefined,
    linkName: undefined,
    linkUrl: undefined,
    avatar: undefined,
    descr: undefined,
    groupCode: undefined,
    orderNum: 0,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("linkRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: BlogLink[]) {
  ids.value = selection.map(item => item.linkId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增友链"
}

/** 修改按钮操作 */
function handleUpdate(row?: BlogLink) {
  reset()
  const linkId = row?.linkId || ids.value[0]
  getLink(linkId as number).then(response => {
    form.value = response.data as BlogLink
    open.value = true
    title.value = "修改友链"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["linkRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const request = form.value.linkId ? updateLink(form.value) : addLink(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.linkId ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row?: BlogLink) {
  const linkIds = row?.linkId ? [row.linkId] : ids.value
  proxy.$modal.confirm('是否确认删除友链编号为"' + linkIds + '"的数据项？').then(() => {
    return delLink(linkIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>
