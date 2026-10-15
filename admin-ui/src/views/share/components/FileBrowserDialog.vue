<template>
  <el-dialog
    :title="title"
    :model-value="modelValue"
    width="720px"
    append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div class="fb-crumbs">
      <el-button link type="primary" @click="goRoot">{{ rootLabel }}</el-button>
      <template v-for="(c, i) in crumbs" :key="i">
        <span class="fb-sep">/</span>
        <el-button link @click="goTo(i)">{{ c.name }}</el-button>
      </template>
      <el-button class="fb-refresh" link :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" height="360" @row-click="onRowClick">
      <el-table-column label="名称" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link v-if="row.__up__" :underline="false" type="primary" class="fb-name">
            <el-icon class="fb-up-ic"><Back /></el-icon>上一页
          </el-link>
          <template v-else>
            <el-tag size="small" :type="row.directory ? 'warning' : 'info'" effect="plain" class="fb-tag">
              {{ row.directory ? '目录' : '文件' }}
            </el-tag>
            <el-link :underline="false" class="fb-name" :type="row.directory ? 'primary' : 'info'">{{ row.name }}</el-link>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="110">
        <template #default="{ row }">{{ row.__up__ || row.directory ? '-' : fmtSize(row.size) }}</template>
      </el-table-column>
      <el-table-column v-if="!selectable" label="操作" width="90">
        <template #default="{ row }">
          <span class="fb-hint">{{ row.__up__ ? '返回上一层' : '点击进入' }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="该目录为空" :image-size="80" />
      </template>
    </el-table>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="ShareFileBrowser">
import { browseSource } from '@/api/share/source'
import type { WebDavEntry } from '@/types'
import { Back } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance() as any

const props = defineProps<{
  /** 是否显示 */
  modelValue: boolean
  /** 源ID */
  sourceId?: number
  /** 源名称（标题用） */
  sourceName?: string
  /** 源默认根目录 */
  rootPath?: string
  /** 是否可选择文件（false 时只浏览） */
  selectable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', entry: WebDavEntry): void
}>()

const loading = ref(false)
const entries = ref<WebDavEntry[]>([])
const currentPath = ref('/')

const title = computed(() => (props.sourceName ? `浏览目录 - ${props.sourceName}` : '浏览目录'))
const rootLabel = computed(() => (props.rootPath && props.rootPath !== '/' ? props.rootPath : '根目录'))

/** 是否已在根目录（决定是否显示「上一页」行） */
const upDisabled = computed(() => {
  const root = normalizedRoot()
  const cur = (currentPath.value || '/').replace(/\/+$/, '')
  return cur === root || cur === ''
})

/** 表格数据：非根目录时首行为「上一页」虚拟行 */
const rows = computed(() => {
  if (upDisabled.value) {
    return entries.value
  }
  return [{ __up__: true } as unknown as WebDavEntry, ...entries.value]
})

const crumbs = computed(() => {
  const root = props.rootPath && props.rootPath !== '/' ? props.rootPath : ''
  let rel = currentPath.value || '/'
  if (root && rel.startsWith(root)) {
    rel = rel.slice(root.length)
  }
  return rel
    .split('/')
    .filter((s: string) => s)
    .map((name: string, index: number, arr: string[]) => {
      // 面包屑指向的都是目录，路径统一带末尾 /（部分 WebDAV 服务要求集合地址以 / 结尾）
      const prefix = root + '/' + arr.slice(0, index + 1).join('/') + '/'
      return { name, path: prefix }
    })
    // 只显示祖先目录，当前所在层不重复显示（列表上方不再出现当前文件夹名）
    .slice(0, -1)
})

function normalizedRoot() {
  const root = props.rootPath && props.rootPath !== '/' ? props.rootPath : '/'
  return root.replace(/\/+$/, '') || '/'
}

function onOpen() {
  loadInto(props.rootPath && props.rootPath !== '/' ? props.rootPath : '/')
}

function load() {
  loadInto(currentPath.value)
}

/** 读取目标目录（成功才更新当前路径与列表；失败保持原状态，错误提示由 request 拦截器统一弹出） */
function loadInto(path: string) {
  if (!props.sourceId) {
    entries.value = []
    return
  }
  loading.value = true
  browseSource(props.sourceId, path)
    .then((response) => {
      currentPath.value = path
      entries.value = response.data || []
      if (!entries.value.length) {
        proxy.$modal.msgInfo('该文件夹为空')
      }
    })
    .finally(() => {
      loading.value = false
    })
}

function enter(row: WebDavEntry) {
  loadInto(row.path)
}

function onRowClick(row: WebDavEntry) {
  if ((row as unknown as { __up__?: boolean }).__up__) {
    goUp()
    return
  }
  if (row.directory) {
    enter(row)
  } else if (props.selectable) {
    choose(row)
  }
}

function goRoot() {
  loadInto(props.rootPath && props.rootPath !== '/' ? props.rootPath : '/')
}

function goTo(index: number) {
  loadInto(crumbs.value[index].path)
}

function goUp() {
  if (upDisabled.value) {
    return
  }
  const root = normalizedRoot()
  const cur = (currentPath.value || '/').replace(/\/+$/, '')
  const idx = cur.lastIndexOf('/')
  let parent = idx <= 0 ? '/' : cur.slice(0, idx + 1)
  // 不越过源根目录（根目录本身可能不是 /）
  if (root !== '/' && (parent === '/' || !parent.startsWith(root))) {
    parent = root
  }
  loadInto(parent)
}

function choose(row: WebDavEntry) {
  emit('select', row)
  close()
}

function close() {
  emit('update:modelValue', false)
}

function fmtSize(bytes: number) {
  if (bytes == null) return ''
  const units = ['B', 'KB', 'MB', 'GB']
  let n = bytes
  let i = 0
  while (n >= 1024 && i < units.length - 1) {
    n = n / 1024
    i++
  }
  return (i === 0 ? n : n.toFixed(1)) + ' ' + units[i]
}
</script>

<style scoped>
.fb-crumbs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  margin-bottom: 8px;
  font-size: 13px;
}
.fb-sep {
  color: var(--el-text-color-placeholder);
}
.fb-refresh {
  margin-left: auto;
}
.fb-up-ic {
  margin-right: 4px;
}
.fb-tag {
  margin-right: 6px;
}
.fb-name {
  word-break: break-all;
  vertical-align: middle;
}
.fb-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
