<template>
  <div v-if="showBlog || showOperlog" class="dynamic-list">
    <el-row :gutter="20">
      <!-- 左列：博客动态（最近文章 + 待发草稿） -->
      <el-col v-if="showBlog" :xs="24" :sm="24" :md="colSpan" :lg="colSpan">
        <div class="dynamic-list__panel">
          <div class="dynamic-list__header">
            <span class="dynamic-list__title">最近动态 · 博客</span>
          </div>

          <!-- 最近文章 -->
          <div class="dynamic-list__section">
            <div class="dynamic-list__section-title">
              <el-icon><Document /></el-icon>
              <span>最近文章</span>
            </div>
            <div v-if="topArticles.length === 0" class="dynamic-list__empty">暂无文章</div>
            <ul v-else class="dynamic-list__items">
              <li
                v-for="(item, index) in topArticles"
                :key="item.articleId ?? index"
                class="dynamic-list__item"
                @click="goTo('/blog/article')"
              >
                <span class="dynamic-list__item-text">{{ item.title || '（无标题）' }}</span>
                <span class="dynamic-list__item-time">{{ formatTime(item.publishDate) }}</span>
              </li>
            </ul>
          </div>

          <!-- 待发草稿 -->
          <div class="dynamic-list__section">
            <div class="dynamic-list__section-title">
              <el-icon><EditPen /></el-icon>
              <span>待发草稿</span>
            </div>
            <div v-if="topDrafts.length === 0" class="dynamic-list__empty">暂无草稿</div>
            <ul v-else class="dynamic-list__items">
              <li
                v-for="(item, index) in topDrafts"
                :key="item.draftId ?? index"
                class="dynamic-list__item"
                @click="goTo('/blog/draft')"
              >
                <span class="dynamic-list__item-text">{{ item.title || '（无标题）' }}</span>
                <span class="dynamic-list__item-time">{{ formatTime(item.updateTime || item.createTime) }}</span>
              </li>
            </ul>
          </div>
        </div>
      </el-col>

      <!-- 右列：操作日志 -->
      <el-col v-if="showOperlog" :xs="24" :sm="24" :md="colSpan" :lg="colSpan">
        <div class="dynamic-list__panel">
          <div class="dynamic-list__header">
            <span class="dynamic-list__title">最近动态 · 操作日志</span>
          </div>

          <div class="dynamic-list__section">
            <div class="dynamic-list__section-title">
              <el-icon><Tickets /></el-icon>
              <span>最近操作</span>
            </div>
            <div v-if="topOperlogs.length === 0" class="dynamic-list__empty">暂无操作日志</div>
            <ul v-else class="dynamic-list__items">
              <li
                v-for="(item, index) in topOperlogs"
                :key="item.operId ?? index"
                class="dynamic-list__item"
                @click="goTo('/monitor/operlog')"
              >
                <span class="dynamic-list__item-oper">{{ item.operName || '未知' }}</span>
                <span class="dynamic-list__item-text">{{ item.title || '—' }}</span>
                <span class="dynamic-list__item-time">{{ formatTime(item.operTime) }}</span>
              </li>
            </ul>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="DynamicList">
// 图标未全局注册（main.ts 未注册 @element-plus/icons-vue），需按需引入
import { Document, EditPen, Tickets } from '@element-plus/icons-vue'
import type { BlogArticle, BlogDraft, SysOperLog } from '@/types'

const props = defineProps<{
  blogAvailable: boolean
  articles: BlogArticle[]
  drafts: BlogDraft[]
  operlogs: SysOperLog[]
  canOperlog: boolean
}>()

const router = useRouter()

/** 每个列表最多展示条数 */
const MAX_ITEMS = 5

const topArticles = computed<BlogArticle[]>(() => props.articles.slice(0, MAX_ITEMS))
const topDrafts = computed<BlogDraft[]>(() => props.drafts.slice(0, MAX_ITEMS))
const topOperlogs = computed<SysOperLog[]>(() => props.operlogs.slice(0, MAX_ITEMS))

/** 博客列：blog 可用且至少有一条文章或草稿 */
const showBlog = computed<boolean>(
  () => props.blogAvailable && (props.articles.length > 0 || props.drafts.length > 0)
)

/** 操作日志列：有权限且有数据 */
const showOperlog = computed<boolean>(() => props.canOperlog && props.operlogs.length > 0)

/** 两列显隐互相独立；只剩一列时铺满整行 */
const colSpan = computed<number>(() => (showBlog.value && showOperlog.value ? 12 : 24))

/** 跳转到对应管理页，鉴权由目标页与后端兜底 */
function goTo(path: string): void {
  router.push(path)
}

/**
 * 时间格式化为 YYYY-MM-DD HH:mm。
 * 后端 operTime 声明为 Date、实际下发字符串，两种都要兼容；
 * 带 '-' 的字符串先换成 '/' 以规避部分浏览器解析失败。
 */
function formatTime(value?: Date | string): string {
  if (!value) return ''
  const date = value instanceof Date ? value : new Date(String(value).replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<style lang="scss" scoped>
.dynamic-list {
  &__panel {
    padding: 16px;
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    height: 100%;
    box-sizing: border-box;
  }

  &__header {
    padding-bottom: 10px;
    margin-bottom: 4px;
    border-bottom: 1px solid #f0f2f5;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  &__section + &__section {
    margin-top: 12px;
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 4px 6px;
    font-size: 13px;
    font-weight: 600;
    color: #909399;
  }

  &__empty {
    padding: 18px 0;
    text-align: center;
    color: #909399;
    font-size: 13px;
  }

  &__items {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px;
    border-radius: 4px;
    cursor: pointer;
    transition: background 0.15s;

    &:hover {
      background: #f5f7fa;
    }
  }

  /* 操作人：定宽并省略，避免长用户名挤掉标题 */
  &__item-oper {
    flex-shrink: 0;
    max-width: 84px;
    font-size: 13px;
    color: #409eff;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__item-text {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: #606266;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__item-time {
    flex-shrink: 0;
    font-size: 12px;
    color: #c0c4cc;
  }
}

/* 窄屏两列纵向堆叠时补间距 */
@media (max-width: 991px) {
  .dynamic-list {
    .el-col + .el-col {
      margin-top: 20px;
    }
  }
}
</style>
