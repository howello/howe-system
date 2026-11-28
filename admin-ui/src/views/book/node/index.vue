<template>
  <div class="app-container book-nodes">
    <!-- 顶部书架概览 -->
    <div class="shelf-head">
      <div class="head-main">
        <div class="head-icon">
          <el-icon><Reading /></el-icon>
        </div>
        <div class="head-text">
          <div class="head-title">读书节点</div>
          <div class="head-sub">分类 → 书本 → 感悟 → 备注，逐层展开浏览</div>
        </div>
      </div>
      <div class="head-stats">
        <div class="stat">
          <b>{{ stats.categories }}</b>
          <span>分类</span>
        </div>
        <div class="stat">
          <b>{{ stats.books }}</b>
          <span>书本</span>
        </div>
        <div class="stat">
          <b>{{ stats.notes }}</b>
          <span>感悟</span>
        </div>
        <div class="stat accent">
          <b>{{ stats.weekNotes }}</b>
          <span>近 7 天</span>
        </div>
      </div>
      <div class="head-actions">
        <el-button type="primary" icon="Plus" @click="openCreate()" v-hasPermi="['book:node:add']">新增分类</el-button>
        <el-button icon="Refresh" :loading="rootLoading" @click="reloadTree">刷新</el-button>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="shelf-toolbar">
      <el-input
        v-model="searchText"
        class="toolbar-search"
        placeholder="按标题搜索全库节点"
        clearable
        :prefix-icon="Search"
      />
      <div class="toolbar-actions">
        <template v-if="!searching">
          <el-button text icon="Expand" :loading="expanding" @click="expandAll">展开全部</el-button>
          <el-button text icon="Fold" @click="collapseAll">收起全部</el-button>
        </template>
        <el-button text icon="Refresh" @click="reloadTree">重载</el-button>
      </div>
      <div class="toolbar-count">
        <template v-if="searching">找到 {{ searchRows.length }} 个节点</template>
        <template v-else>已加载 {{ nodes.length }} 个节点</template>
      </div>
    </div>

    <!-- 搜索模式 -->
    <div v-if="searching" v-loading="searchLoading" class="shelf-panel search-panel">
      <div v-for="row in searchRows" :key="row.id" class="search-row">
        <span class="type-chip" :class="'type-' + row.nodeType">
          <el-icon><component :is="typeIcon(row.nodeType)" /></el-icon>
          {{ NODE_TYPE_LABELS[row.nodeType] }}
        </span>
        <span class="search-title">{{ row.title }}</span>
        <span class="search-time">{{ formatTime(row.updatedAt) }}</span>
        <span class="row-actions">
          <el-tooltip content="查看" placement="top">
            <el-button link icon="View" aria-label="查看" @click="openView(row)" />
          </el-tooltip>
          <el-tooltip content="编辑" placement="top">
            <el-button link type="primary" icon="Edit" aria-label="编辑" @click="openEdit(row)" v-hasPermi="['book:node:edit']" />
          </el-tooltip>
        </span>
      </div>
      <el-empty v-if="!searchLoading && !searchRows.length" description="没有匹配的节点" :image-size="72" />
    </div>

    <!-- 树形浏览 -->
    <div v-else v-loading="rootLoading" class="shelf-panel">
      <el-empty v-if="!rootLoading && !roots.length" description="还没有分类，先新增一个分类吧" :image-size="88">
        <el-button type="primary" icon="Plus" @click="openCreate()" v-hasPermi="['book:node:add']">新增分类</el-button>
      </el-empty>

      <div v-else class="tree">
        <div
          v-for="row in treeRows"
          :key="row.key"
          class="node-row"
          :class="['depth-' + Math.min(row.depth, 6), { hint: row.hint }]"
          :style="{ paddingLeft: row.depth * 22 + 14 + 'px' }"
        >
          <span
            v-for="guide in row.depth"
            :key="'g' + guide"
            class="guide"
            :style="{ left: (guide - 1) * 22 + 25 + 'px' }"
          />

          <template v-if="row.hint">
            <span class="hint-dot" />
            <span class="hint-text">暂无子节点</span>
          </template>

          <template v-else-if="row.node">
            <button
              class="node-toggle"
              :class="{ open: isExpanded(row.node), leaf: !row.expandable }"
              :disabled="!row.expandable"
              :aria-label="row.expandable ? (isExpanded(row.node) ? '折叠' : '展开') : '无子节点'"
              @click="toggle(row.node)"
            >
              <el-icon v-if="isLoading(row.node)"><Loading class="is-loading" /></el-icon>
              <el-icon v-else-if="row.expandable"><CaretRight /></el-icon>
              <span v-else class="leaf-dot" />
            </button>

            <span class="node-icon" :class="'type-' + row.node.nodeType">
              <el-icon><component :is="typeIcon(row.node.nodeType)" /></el-icon>
            </span>

            <div class="node-main">
              <div class="node-line">
                <span class="node-title" :title="row.node.title">{{ row.node.title }}</span>
                <span class="type-chip" :class="'type-' + row.node.nodeType">
                  {{ NODE_TYPE_LABELS[row.node.nodeType] }}
                </span>
                <span v-if="row.loaded && row.count" class="count-chip">{{ row.count }} 项</span>
              </div>
              <div v-if="excerptOf(row.node)" class="node-excerpt">{{ truncate(excerptOf(row.node), 90) }}</div>
            </div>

            <span class="node-time">{{ formatTime(row.node.updatedAt) }}</span>

            <span class="row-actions">
              <el-tooltip content="新增子节点" placement="top">
                <el-button
                  link
                  icon="Plus"
                  aria-label="新增子节点"
                  @click="openCreate(row.node)"
                  v-hasPermi="['book:node:add']"
                />
              </el-tooltip>
              <el-tooltip content="查看" placement="top">
                <el-button link icon="View" aria-label="查看" @click="openView(row.node)" />
              </el-tooltip>
              <el-tooltip content="编辑" placement="top">
                <el-button link type="primary" icon="Edit" aria-label="编辑" @click="openEdit(row.node)" v-hasPermi="['book:node:edit']" />
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button link type="danger" icon="Delete" aria-label="删除" @click="remove(row.node)" v-hasPermi="['book:node:remove']" />
              </el-tooltip>
            </span>
          </template>
        </div>
      </div>
    </div>

    <!-- 详情 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'view' ? '节点详情' : form.id ? '编辑节点' : '新增节点'"
      width="760px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <!-- 查看 -->
      <div v-if="dialogMode === 'view'" class="preview">
        <div class="preview-head">
          <span class="preview-icon" :class="'type-' + form.nodeType">
            <el-icon><component :is="typeIcon(form.nodeType)" /></el-icon>
          </span>
          <div class="preview-head-text">
            <div class="preview-title">{{ form.title }}</div>
            <div class="preview-sub">
              <span class="type-chip" :class="'type-' + form.nodeType">{{ NODE_TYPE_LABELS[form.nodeType] }}</span>
              <span v-if="previewMeta" class="preview-meta">{{ previewMeta }}</span>
            </div>
          </div>
          <el-image
            v-if="previewCover"
            :src="previewCover"
            fit="cover"
            class="preview-cover"
            :preview-src-list="[previewCover]"
            preview-teleported
          />
        </div>

        <div class="preview-body">
          <p v-for="(line, index) in previewLines" :key="index" class="preview-line">{{ line }}</p>

          <template v-if="form.nodeType === 'note' && previewBlocks.length">
            <div v-for="(block, index) in previewBlocks" :key="block.id || index" class="preview-block">
              <el-image
                v-if="block.type === 'image' && block.url"
                :src="block.url"
                fit="cover"
                class="preview-image"
                :preview-src-list="[block.url]"
                preview-teleported
              />
              <blockquote v-else-if="block.type === 'quote'" class="preview-quote">
                {{ block.text }}
                <span v-if="block.page" class="preview-page">— 第 {{ block.page }} 页</span>
              </blockquote>
              <audio v-else-if="block.type === 'audio' && block.url" :src="block.url" controls class="preview-audio" />
              <p v-else-if="block.text" class="preview-line">{{ block.text }}</p>
            </div>
          </template>
        </div>

        <div class="preview-foot">
          <span>创建人 ID：{{ form.userId ?? '-' }}</span>
          <span>创建于 {{ formatTime(form.createdAt) }}</span>
          <span>更新于 {{ formatTime(form.updatedAt) }}</span>
          <span>版本 {{ form.version ?? '-' }}</span>
          <span class="mono">{{ form.id }}</span>
        </div>
      </div>

      <!-- 编辑 -->
      <el-form v-else ref="formRef" :model="form" label-width="92px" class="node-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="节点类型">
              <el-select v-model="form.nodeType" disabled>
                <el-option v-for="item in NODE_TYPE_OPTIONS" :key="item.value" v-bind="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="父节点">
              <el-select v-model="form.parentId" filterable :loading="parentLoading" placeholder="选择父节点">
                <el-option v-if="form.nodeType === 'category'" label="顶层（作为分类）" value="0" />
                <el-option
                  v-for="item in parentOptions"
                  :key="item.id"
                  :label="NODE_TYPE_LABELS[item.nodeType] + ' · ' + item.title"
                  :value="item.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="分类名 / 书名 / 感悟标题" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="封面类型">
              <el-select v-model="form.coverKind" clearable placeholder="未设置">
                <el-option v-for="item in coverKinds" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="封面引用">
              <el-input v-model="form.coverRef" placeholder="图库路径或图片地址" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 分类 -->
        <template v-if="form.nodeType === 'category'">
          <el-form-item label="一句话说明">
            <el-input v-model="content.desc" maxlength="120" show-word-limit placeholder="展示在分类卡片上的说明" />
          </el-form-item>
        </template>

        <!-- 书本 -->
        <template v-else-if="form.nodeType === 'book'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="作者"><el-input v-model="content.author" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="译者"><el-input v-model="content.translator" /></el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="出版社"><el-input v-model="content.publisher" /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="出版年"><el-input v-model="content.year" /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="页数"><el-input v-model="content.pages" /></el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="ISBN"><el-input v-model="content.isbn" /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="评分"><el-input v-model="content.rating" /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="阅读状态"><el-input v-model="content.status" placeholder="想读 / 在读 / 已读完" /></el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="简介">
            <el-input v-model="content.intro" type="textarea" :rows="4" maxlength="2000" show-word-limit />
          </el-form-item>
        </template>

        <!-- 感悟 -->
        <template v-else-if="form.nodeType === 'note'">
          <el-form-item label="章节">
            <el-input v-model="content.chapter" placeholder="如：第五章 · 有庆之死" />
          </el-form-item>
          <el-form-item label="正文">
            <el-input
              v-model="content.text"
              type="textarea"
              :rows="6"
              :disabled="!!previewBlocks.length"
              :placeholder="previewBlocks.length ? '该感悟由读书端的分块内容组成，请在读书端修改正文' : '感悟正文'"
            />
            <div v-if="previewBlocks.length" class="form-tip">
              已有 {{ previewBlocks.length }} 个内容块（文字 / 引用 / 图片 / 语音），后台只修改标题与章节，正文请在读书端编辑。
            </div>
          </el-form-item>
        </template>

        <!-- 备注 -->
        <template v-else>
          <el-form-item label="正文">
            <el-input v-model="content.text" type="textarea" :rows="6" placeholder="备注内容" />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <template v-if="dialogMode === 'view'">
          <el-button v-if="canEdit" type="primary" icon="Edit" aria-label="切换到编辑" @click="switchToEdit">编辑</el-button>
        </template>
        <el-button v-else type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  addBookNode,
  delBookNode,
  getBookNode,
  getBookStats,
  listBookNodes,
  updateBookNode
} from '@/api/book/node'
import { checkPermi } from '@/utils/permission'
import {
  NODE_TYPE_LABELS,
  NODE_TYPE_OPTIONS,
  childTypeOf,
  excerptOf,
  parentTypesOf,
  parseNodeContent,
  truncate
} from './book-node-meta'
import type { BookNode, BookNodeContent, BookNodeType, BookShelfStats } from '@/types'

/** 树形行：普通节点行或「暂无子节点」提示行。 */
interface TreeRow {
  key: string
  depth: number
  node?: BookNode
  count: number
  loaded: boolean
  expandable: boolean
  hint?: boolean
}

/** 单层最多拉取条数，避免默认分页只返回 10 条。 */
const PAGE_SIZE = 500

const coverKinds = [
  { label: '主题封面', value: 'theme' },
  { label: '图库封面', value: 'local' },
  { label: '上传图片', value: 'public' },
  { label: '外链图片', value: 'external' }
]

const stats = reactive<BookShelfStats>({ categories: 0, books: 0, notes: 0, weekNotes: 0 })
const nodes = ref<BookNode[]>([])
const expanded = reactive(new Set<string>())
const loadedKeys = reactive(new Set<string>())
const loadingKeys = reactive(new Set<string>())
const rootLoading = ref(false)
const expanding = ref(false)

const searchText = ref('')
const searchRows = ref<BookNode[]>([])
const searchLoading = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | undefined
const searching = computed(() => !!searchText.value.trim())

const dialogVisible = ref(false)
const dialogMode = ref<'view' | 'edit'>('edit')
const form = ref<BookNode>(blankNode())
const content = ref<BookNodeContent>({})
const originalParentId = ref<string | null>(null)
const parentOptions = ref<BookNode[]>([])
const parentLoading = ref(false)
const saving = ref(false)
const canEdit = computed(() => checkPermi(['book:node:edit']))

/** 未保存的新节点默认值。 */
function blankNode(): BookNode {
  return { nodeType: 'category', title: '', sortOrder: 0, parentId: '0', coverKind: '', coverRef: '' }
}

/** 节点图标。 */
function typeIcon(type: BookNodeType | string) {
  return { category: 'Folder', book: 'Reading', note: 'ChatLineSquare', remark: 'Memo' }[type] || 'Document'
}

/** 时间格式化，只保留到分钟。 */
function formatTime(value?: string) {
  return (value || '').replace('T', ' ').slice(0, 16) || '-'
}

const childrenMap = computed(() => {
  const map = new Map<string, BookNode[]>()
  for (const node of nodes.value) {
    const key = node.parentId || ''
    const list = map.get(key) || []
    list.push(node)
    map.set(key, list)
  }
  for (const list of map.values()) {
    list.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.title.localeCompare(b.title, 'zh-CN'))
  }
  return map
})
const roots = computed(() => childrenMap.value.get('') || [])

const treeRows = computed<TreeRow[]>(() => {
  const rows: TreeRow[] = []
  const walk = (list: BookNode[], depth: number) => {
    for (const node of list) {
      const id = node.id || ''
      const children = childrenMap.value.get(id) || []
      const loaded = loadedKeys.has(id)
      const expandable = !loaded || children.length > 0
      rows.push({ key: id, node, depth, count: children.length, loaded, expandable })
      if (expanded.has(id)) {
        if (loaded && !children.length) {
          rows.push({ key: id + '-empty', depth: depth + 1, count: 0, loaded: true, expandable: false, hint: true })
        } else if (children.length) {
          walk(children, depth + 1)
        }
      }
    }
  }
  walk(roots.value, 0)
  return rows
})

function isExpanded(node: BookNode) {
  return !!node.id && expanded.has(node.id)
}

function isLoading(node: BookNode) {
  return !!node.id && loadingKeys.has(node.id)
}

/** 拉取根层分类。 */
async function loadRoots() {
  rootLoading.value = true
  try {
    const response = await listBookNodes({ parentId: '0', pageSize: PAGE_SIZE })
    nodes.value = [...nodes.value.filter(node => node.parentId), ...response.rows]
    if (response.total > response.rows.length) {
      ElMessage.warning(`顶层分类超过 ${PAGE_SIZE} 个，仅显示前 ${response.rows.length} 个`)
    }
  } finally {
    rootLoading.value = false
  }
}

/** 按需加载某个节点的下一层；已加载过则不重复请求。 */
async function ensureChildren(id: string) {
  if (!id || loadedKeys.has(id) || loadingKeys.has(id)) return
  loadingKeys.add(id)
  try {
    const response = await listBookNodes({ parentId: id, pageSize: PAGE_SIZE })
    nodes.value = [...nodes.value.filter(node => node.parentId !== id), ...response.rows]
    loadedKeys.add(id)
    if (response.total > response.rows.length) {
      const parent = nodes.value.find(node => node.id === id)
      ElMessage.warning(`「${parent?.title || '该节点'}」下还有 ${response.total - response.rows.length} 个节点未显示`)
    }
  } finally {
    loadingKeys.delete(id)
  }
}

/** 展开 / 折叠：首次展开时才请求下一层。 */
async function toggle(node: BookNode) {
  const id = node.id
  if (!id) return
  if (expanded.has(id)) {
    expanded.delete(id)
    return
  }
  expanded.add(id)
  await ensureChildren(id)
}

/** 重新加载根层，并恢复此前展开的层级。 */
async function reloadTree() {
  const keepExpanded = [...expanded]
  nodes.value = []
  loadedKeys.clear()
  expanded.clear()
  await Promise.all([loadRoots(), loadStats()])
  for (const id of keepExpanded) {
    if (nodes.value.some(node => node.id === id)) {
      expanded.add(id)
      await ensureChildren(id)
    }
  }
}

/** 展开全部已加载层级的子节点。 */
async function expandAll() {
  expanding.value = true
  try {
    const queue = [...roots.value]
    while (queue.length && nodes.value.length < 3000) {
      const node = queue.shift()!
      const id = node.id
      if (!id) continue
      expanded.add(id)
      await ensureChildren(id)
      queue.push(...(childrenMap.value.get(id) || []))
    }
  } finally {
    expanding.value = false
  }
}

/** 收起全部，只留分类列表。 */
function collapseAll() {
  expanded.clear()
}

/** 读取书架统计。 */
async function loadStats() {
  const response = await getBookStats()
  Object.assign(stats, response.data || {})
}

/** 搜索全库节点，输入停止 300ms 后发请求。 */
watch(searchText, value => {
  clearTimeout(searchTimer)
  const keyword = value.trim()
  if (!keyword) {
    searchRows.value = []
    searchLoading.value = false
    return
  }
  searchLoading.value = true
  searchTimer = setTimeout(async () => {
    try {
      const response = await listBookNodes({ title: keyword, pageSize: 100 })
      if (searchText.value.trim() === keyword) searchRows.value = response.rows
    } finally {
      if (searchText.value.trim() === keyword) searchLoading.value = false
    }
  }, 300)
})

/** 拉取可选父节点：按节点类型约束查询对应类型的全部节点。 */
async function loadParentOptions(type: BookNodeType) {
  parentLoading.value = true
  try {
    const results = await Promise.all(
      parentTypesOf(type).map(item => listBookNodes({ type: item, pageSize: 200 }))
    )
    parentOptions.value = results
      .flatMap(result => result.rows)
      .filter(node => node.id !== form.value.id)
  } finally {
    parentLoading.value = false
  }
}

/** 新增：从行内进入时父节点与类型已确定，从顶部进入则默认新增顶层分类。 */
async function openCreate(parent?: BookNode) {
  dialogMode.value = 'edit'
  const node = blankNode()
  node.nodeType = childTypeOf(parent)
  node.parentId = parent?.id || '0'
  form.value = node
  content.value = {}
  originalParentId.value = null
  dialogVisible.value = true
  await loadParentOptions(node.nodeType)
}

/** 编辑：读取完整节点后回填。 */
async function openEdit(row: BookNode) {
  const response = await getBookNode(row.id!)
  const node = response.data || row
  dialogMode.value = 'edit'
  form.value = { ...node, parentId: node.parentId || '0', coverKind: node.coverKind || '', coverRef: node.coverRef || '' }
  content.value = parseNodeContent(node)
  originalParentId.value = node.parentId || null
  dialogVisible.value = true
  await loadParentOptions(node.nodeType)
}

/** 查看：只读详情。 */
async function openView(row: BookNode) {
  const response = await getBookNode(row.id!)
  const node = response.data || row
  dialogMode.value = 'view'
  form.value = node
  content.value = parseNodeContent(node)
  dialogVisible.value = true
}

/** 详情切换到编辑。 */
async function switchToEdit() {
  await openEdit(form.value)
}

/** 保存前把「0」还原成 null，空串清掉。 */
function normalizeParentId(): string | null {
  const parentId = form.value.parentId
  return !parentId || parentId === '0' ? null : parentId
}

/** 组装正文：保留未改动的字段，去掉空值。 */
function buildContentJson(): string | null {
  const output: Record<string, unknown> = { ...content.value }
  for (const key of Object.keys(output)) {
    const value = output[key]
    if (value === undefined || value === '' || (Array.isArray(value) && !value.length)) {
      delete output[key]
    }
  }
  return Object.keys(output).length ? JSON.stringify(output) : null
}

/** 保存节点，成功后只刷新受影响的父层。 */
async function submit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  const parentId = normalizeParentId()
  if (!parentId && form.value.nodeType !== 'category') {
    ElMessage.warning('请选择父节点')
    return
  }
  const payload: BookNode = {
    ...form.value,
    title: form.value.title.trim(),
    parentId,
    coverKind: form.value.coverKind || null,
    coverRef: form.value.coverRef || null,
    contentJson: buildContentJson()
  }
  saving.value = true
  try {
    if (payload.id) {
      await updateBookNode(payload.id, payload)
    } else {
      await addBookNode(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await refreshScope(parentId)
    if (originalParentId.value && originalParentId.value !== parentId) {
      await refreshScope(originalParentId.value)
    }
    await loadStats()
  } finally {
    saving.value = false
  }
}

/**
 * 刷新某个父节点下的子列表；为空则整棵树重载。
 *
 * 旧子节点的后代也要一并清掉并标记为未加载，否则再次展开会残留「暂无子节点」的误判。
 */
async function forgetDescendants(parentId: string) {
  const removed = new Set<string>()
  for (const node of nodes.value) {
    if (node.parentId === parentId) removed.add(node.id!)
  }
  let changed = removed.size > 0
  while (changed) {
    changed = false
    for (const node of nodes.value) {
      if (node.parentId && removed.has(node.parentId) && !removed.has(node.id!)) {
        removed.add(node.id!)
        changed = true
      }
    }
  }
  nodes.value = nodes.value.filter(node => !removed.has(node.id!))
  for (const id of removed) loadedKeys.delete(id)
}

/** 刷新某个父节点下的子列表；为空则整棵树重载。 */
async function refreshScope(parentId: string | null) {
  if (!parentId || !nodes.value.some(node => node.id === parentId)) {
    await reloadTree()
    return
  }
  await forgetDescendants(parentId)
  expanded.add(parentId)
  loadedKeys.delete(parentId)
  await ensureChildren(parentId)
}

/** 删除节点及全部后代。 */
async function remove(node: BookNode) {
  try {
    await ElMessageBox.confirm(`「${node.title}」及其所有子节点都会被删除，确认继续？`, '删除节点', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await delBookNode(node.id!)
  const parentId = node.parentId || null
  // 先本地摘掉该节点及其后代，避免等整棵树重载
  const removed = new Set<string>([node.id!])
  let changed = true
  while (changed) {
    changed = false
    for (const item of nodes.value) {
      if (item.parentId && removed.has(item.parentId) && !removed.has(item.id!)) {
        removed.add(item.id!)
        changed = true
      }
    }
  }
  nodes.value = nodes.value.filter(item => !removed.has(item.id!))
  for (const id of removed) {
    expanded.delete(id)
    loadedKeys.delete(id)
  }
  ElMessage.success('已删除')
  await refreshScope(parentId)
  await loadStats()
}

const previewBlocks = computed(() => content.value.blocks || [])
const previewCover = computed(() => {
  const { coverKind, coverRef } = form.value
  if ((coverKind === 'public' || coverKind === 'external') && /^https?:\/\//i.test(coverRef || '')) {
    return coverRef as string
  }
  return ''
})
const previewMeta = computed(() => {
  const value = content.value
  if (form.value.nodeType === 'book') {
    return [value.author, value.publisher, value.year].filter(Boolean).join(' · ')
  }
  if (form.value.nodeType === 'note') {
    return [value.chapter, value.summary].filter(Boolean).join(' · ')
  }
  if (form.value.nodeType === 'category') {
    return value.desc || ''
  }
  return ''
})
const previewLines = computed(() => {
  const value = content.value
  if (form.value.nodeType === 'category') return value.desc ? [value.desc] : []
  if (form.value.nodeType === 'book') {
    const lines: string[] = []
    if (value.intro) lines.push(value.intro)
    const tags = Array.isArray(value.tags) ? value.tags : []
    if (tags.length) lines.push('标签：' + tags.join(' / '))
    return lines
  }
  // 感悟已有分块时按块展示，备注直接展示正文
  if (previewBlocks.value.length) return value.text && !value.blocks ? [value.text] : []
  return value.text ? [value.text] : []
})

onMounted(async () => {
  await reloadTree()
})
</script>

<style lang="scss" scoped>
.book-nodes {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: calc(100vh - 124px);
  min-height: 480px;
}

/* 顶部概览 */
.shelf-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 16px 20px;
  border-radius: 12px;
  background: linear-gradient(120deg, #fff 0%, #f7f9fc 100%);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 2px 10px rgba(31, 45, 61, 0.04);
  flex-shrink: 0;
}

.head-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 220px;
}

.head-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #8a5a2b;
  background: linear-gradient(140deg, #fdf1df 0%, #f4e2c8 100%);
}

.head-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  letter-spacing: 0.5px;
}

.head-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.head-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  padding: 6px 12px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);

  b {
    font-size: 18px;
    line-height: 1.2;
    color: var(--el-text-color-primary);
    font-variant-numeric: tabular-nums;
  }

  span {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }

  &.accent {
    background: #eef5ea;

    b {
      color: #4c6350;
    }
  }
}

.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 工具栏 */
.shelf-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.toolbar-search {
  max-width: 320px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 面板 */
.shelf-panel {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 2px 10px rgba(31, 45, 61, 0.04);

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--el-border-color);
    border-radius: 6px;
  }
}

/* 树 */
.tree {
  padding: 4px 2px 12px;
}

.node-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 6px 12px;
  border-radius: 10px;
  transition: background 0.16s ease;

  &:hover {
    background: var(--el-fill-color-light);

    .row-actions {
      opacity: 1;
    }
  }
}

.guide {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--el-border-color-lighter);
  pointer-events: none;
}

.node-toggle {
  width: 22px;
  height: 22px;
  padding: 0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: transform 0.18s ease, background 0.18s ease, color 0.18s ease;
  font-size: 13px;

  &:hover:not(:disabled) {
    background: var(--el-fill-color-dark);
    color: var(--el-color-primary);
  }

  &.open {
    transform: rotate(90deg);
  }

  &.leaf,
  &:disabled {
    cursor: default;
    transform: none;
  }
}

.leaf-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--el-border-color);
}

.node-icon {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

/* 类型配色 */
.type-category {
  background: #fbf1e3;
  color: #a4713a;
}
.type-book {
  background: #faeae5;
  color: #a9503f;
}
.type-note {
  background: #edf2ec;
  color: #4f6b55;
}
.type-remark {
  background: #eaf0f4;
  color: #3e5566;
}

.node-main {
  flex: 1;
  min-width: 0;
}

.node-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.node-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  background: transparent;
}

.count-chip {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-dark);
}

.node-excerpt {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-time {
  flex-shrink: 0;
  width: 108px;
  text-align: right;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-variant-numeric: tabular-nums;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
  opacity: 0.45;
  transition: opacity 0.16s ease;

  :deep(.el-button) {
    padding: 4px;
    height: auto;
  }
}

/* 暂无子节点提示 */
.node-row.hint {
  min-height: 30px;
  padding-top: 2px;
  padding-bottom: 2px;
  gap: 8px;
}

.hint-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--el-border-color);
  flex-shrink: 0;
  margin-left: 9px;
}

.hint-text {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* 搜索结果 */
.search-panel {
  padding: 6px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 10px;

  &:hover {
    background: var(--el-fill-color-light);

    .row-actions {
      opacity: 1;
    }
  }
}

.search-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 550;
  color: var(--el-text-color-primary);
}

.search-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* 详情预览 */
.preview-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.preview-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.preview-head-text {
  flex: 1;
  min-width: 0;
}

.preview-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

.preview-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.preview-cover {
  width: 64px;
  height: 88px;
  border-radius: 8px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.preview-body {
  max-height: 46vh;
  overflow-y: auto;
  padding: 14px 2px;
}

.preview-line {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-block {
  margin-bottom: 10px;
}

.preview-image {
  width: 100%;
  max-width: 320px;
  border-radius: 10px;
}

.preview-quote {
  margin: 0 0 10px;
  padding: 10px 14px;
  border-left: 3px solid #b08968;
  border-radius: 0 8px 8px 0;
  background: #fbf6ef;
  color: #6b4f35;
  font-size: 14px;
  line-height: 1.8;
}

.preview-page {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #9b8265;
}

.preview-audio {
  width: 100%;
}

.preview-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-placeholder);

  .mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    word-break: break-all;
  }
}

/* 表单 */
.node-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

/* 暗色适配 */
:global(html.dark) .shelf-head {
  background: linear-gradient(120deg, var(--el-bg-color-overlay) 0%, var(--el-fill-color-light) 100%);
}

:global(html.dark) .stat.accent {
  background: rgba(76, 99, 80, 0.28);
}

:global(html.dark) .preview-quote {
  background: rgba(176, 137, 104, 0.14);
  color: #d9c4ad;
}
</style>
