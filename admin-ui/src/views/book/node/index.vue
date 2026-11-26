<template>
  <div class="app-container">
    <el-form inline @submit.prevent="load(1)">
      <el-form-item label="归属用户"><el-input v-model="filters.userId" placeholder="用户 ID" clearable /></el-form-item>
      <el-form-item label="标题"><el-input v-model="filters.title" clearable /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="filters.type" clearable style="width: 130px">
          <el-option v-for="item in types" :key="item.value" v-bind="item" />
        </el-select>
      </el-form-item>
      <el-button native-type="submit" type="primary">查询</el-button>
      <el-button v-hasPermi="['book:node:add']" @click="open()">新增</el-button>
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="nodeType" label="类型" />
      <el-table-column prop="userId" label="用户 ID" />
      <el-table-column prop="parentId" label="父节点" />
      <el-table-column prop="updatedAt" label="更新时间" />
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button v-hasPermi="['book:node:query']" link @click="open(scope.row.id, true)">查看</el-button>
          <el-button v-hasPermi="['book:node:edit']" link @click="open(scope.row.id)">编辑</el-button>
          <el-button v-hasPermi="['book:node:remove']" link type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="total" @current-change="load" />
    <el-dialog v-model="visible" :title="readOnly ? '节点详情' : form.id ? '编辑节点' : '新增节点'">
      <el-form label-width="100" :disabled="readOnly">
        <el-form-item label="归属用户 ID"><el-input v-model="form.userId" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="节点类型">
          <el-select v-model="form.nodeType" :disabled="!!form.id">
            <el-option v-for="item in types" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="父节点">
          <el-select v-model="form.parentId" filterable remote clearable
            :disabled="form.nodeType === 'category' || !form.userId" :remote-method="loadParents"
            placeholder="按标题找父节点" @visible-change="(shown: boolean) => shown && loadParents('')">
            <el-option v-for="item in parents" :key="item.id" :label="item.title" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" /></el-form-item>
        <el-form-item label="封面类型"><el-input v-model="form.coverKind" /></el-form-item>
        <el-form-item label="封面引用"><el-input v-model="form.coverRef" /></el-form-item>
        <template v-if="form.nodeType === 'book'">
          <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
            <el-input v-model="content[field.key]" />
          </el-form-item>
        </template>
        <el-form-item :label="form.nodeType === 'book' ? '简介' : form.nodeType === 'category' ? '描述' : '正文'">
          <el-input v-model="content.text" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item v-if="form.nodeType === 'note' && attachmentCount" label="附件">
          <el-text>已有 {{ attachmentCount }} 个附件，保存时保留</el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="!readOnly" type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import request from '@/utils/request'

interface Node {
  id?: string
  userId?: string
  parentId?: string
  nodeType: string
  title: string
  sortOrder: number
  coverKind?: string
  coverRef?: string
  contentJson?: string
}

const types = [
  { label: '分类', value: 'category' }, { label: '书本', value: 'book' },
  { label: '感悟', value: 'note' }, { label: '备注', value: 'remark' },
]
const url = '/book/admin/nodes'
const filters = reactive({ userId: '', title: '', type: '' })
const rows = ref<Node[]>([])
const total = ref(0)
const page = ref(1)
const visible = ref(false)
const readOnly = ref(false)
const form = ref<Node>({ nodeType: 'category', title: '', sortOrder: 0 })
const fields = [
  { key: 'author', label: '作者' }, { key: 'translator', label: '译者' },
  { key: 'publisher', label: '出版社' }, { key: 'year', label: '出版年' },
  { key: 'isbn', label: 'ISBN' }, { key: 'pages', label: '页数' },
]
const content = ref<Record<string, unknown>>({})
const attachmentCount = computed(() => {
  const value = content.value.attachments
  return Array.isArray(value) ? value.length : 0
})
const parents = ref<Node[]>([])
let parentQuery = 0
watch(() => [form.value.userId, form.value.nodeType], () => {
  parentQuery++
  parents.value = []
  if (!form.value.id) form.value.parentId = undefined
})
async function loadParents(title: string) {
  const { userId, nodeType, id } = form.value
  if (!userId || nodeType === 'category') return
  const sequence = ++parentQuery
  const parentTypes = nodeType === 'book' ? ['category'] : nodeType === 'note' ? ['book'] : ['note', 'remark']
  const results = await Promise.all(parentTypes.map(type => request({
    url, method: 'get', params: { userId, type, title, pageNum: 1, pageSize: 30 },
  }) as unknown as Promise<{ rows: Node[] }>))
  if (sequence === parentQuery) parents.value = results.flatMap(result => result.rows).filter(node => node.id !== id)
}
async function load(next = page.value) {
  page.value = next
  const response = await request({ url, method: 'get', params: { ...filters, pageNum: next, pageSize: 20 } }) as unknown as { rows: Node[]; total: number }
  rows.value = response.rows
  total.value = response.total
}
async function open(id?: string, preview = false) {
  readOnly.value = preview
  if (id) {
    const response = await request({ url: url + '/' + id, method: 'get' }) as unknown as { data: Node }
    form.value = response.data
  } else {
    form.value = { nodeType: 'category', title: '', sortOrder: 0, userId: filters.userId }
  }
  content.value = JSON.parse(form.value.contentJson || "{}")
  visible.value = true
}
async function save() {
  const { id } = form.value
  await request({ url: id ? url + '/' + id : url, method: id ? 'put' : 'post', data: { ...form.value, contentJson: JSON.stringify(content.value) } })
  visible.value = false
  await load()
}
async function remove(node: Node) {
  try { await ElMessageBox.confirm('将一并删除该节点的所有后代，确认继续？', node.title) } catch { return }
  await request({ url: url + '/' + node.id, method: 'delete' })
  await load()
}
onMounted(() => void load())
</script>
