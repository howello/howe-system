<template>
  <div class="recent-notice">
    <!-- 头部：标题 + 全部已读 -->
    <div class="recent-notice__header">
      <span class="recent-notice__title">最近公告</span>
      <el-button
        v-if="hasUnread"
        type="primary"
        link
        size="small"
        class="recent-notice__mark-all"
        @click="handleMarkAllRead"
      >
        全部已读
      </el-button>
    </div>

    <!-- 空态 -->
    <div v-if="notices.length === 0" class="recent-notice__empty">暂无公告</div>

    <!-- 公告列表 -->
    <ul v-else class="recent-notice__list">
      <li
        v-for="item in notices"
        :key="item.noticeId"
        class="recent-notice__item"
        :class="{ 'is-unread': !item.isRead }"
        @click="handlePreview(item)"
      >
        <span class="recent-notice__dot" />
        <el-tag size="small" :type="item.noticeType === '1' ? 'warning' : 'success'" disable-transitions>
          {{ item.noticeType === '1' ? '通知' : '公告' }}
        </el-tag>
        <span class="recent-notice__item-title">{{ item.noticeTitle }}</span>
        <span class="recent-notice__item-date">{{ item.createTime }}</span>
      </li>
    </ul>

    <!-- 正文弹窗：公告来自后台可信富文本编辑器，允许原样渲染 -->
    <el-dialog v-model="detailVisible" :title="currentNotice?.noticeTitle" width="640px" append-to-body>
      <div class="recent-notice__content" v-html="currentNotice?.noticeContent" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="RecentNotice">
import { markNoticeRead, markNoticeReadAll } from '@/api/system/notice'
import type { SysNotice } from '@/types'

const props = defineProps<{ notices: SysNotice[] }>()

const detailVisible = ref<boolean>(false)
const currentNotice: Ref<SysNotice | null> = ref<SysNotice | null>(null)

/** 未读公告 id 列表 */
const unreadIds = computed<number[]>(() =>
  props.notices.filter((n: SysNotice) => !n.isRead).map((n: SysNotice) => n.noticeId!)
)

/** 是否存在未读公告 */
const hasUnread = computed<boolean>(() => unreadIds.value.length > 0)

/** 点击公告：未读则标记已读（接口 + 本地置位），并展开正文 */
function handlePreview(item: SysNotice): void {
  if (!item.isRead) {
    markNoticeRead(item.noticeId!).catch(() => {})
    item.isRead = true
  }
  currentNotice.value = item
  detailVisible.value = true
}

/** 全部已读：ids 为未读 noticeId 逗号拼接 */
function handleMarkAllRead(): void {
  const ids = unreadIds.value.join(',')
  if (!ids) return
  markNoticeReadAll(ids).catch(() => {})
  props.notices.forEach((n: SysNotice) => {
    n.isRead = true
  })
}
</script>

<style lang="scss" scoped>
.recent-notice {
  padding: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  min-height: 200px;
}

.recent-notice__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 4px;
  border-bottom: 1px solid #f0f2f5;
}

.recent-notice__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.recent-notice__mark-all {
  padding: 0;
}

.recent-notice__empty {
  padding: 40px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.recent-notice__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.recent-notice__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.recent-notice__item:hover {
  background: #f5f7fa;
}

/* 未读高亮：浅底色 + 标题加粗 + 红点 */
.recent-notice__item.is-unread {
  background: #f0f7ff;

  .recent-notice__item-title {
    font-weight: 600;
    color: #303133;
  }

  .recent-notice__dot {
    display: inline-block;
  }
}

.recent-notice__dot {
  display: none;
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f56c6c;
}

.recent-notice__item:not(.is-unread) {
  .recent-notice__item-title {
    color: #909399;
  }

  .recent-notice__item-date {
    color: #dcdfe6;
  }
}

.recent-notice__item-title {
  flex: 1;
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.recent-notice__item-date {
  flex-shrink: 0;
  font-size: 12px;
  color: #c0c4cc;
}

/* 正文：可信富文本，仅基础排版兜底 */
.recent-notice__content {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  word-break: break-word;
}

.recent-notice__content :deep(p) {
  margin: 0 0 1em;
}

.recent-notice__content :deep(img) {
  max-width: 100%;
}

.recent-notice__content :deep(a) {
  color: #409eff;
}
</style>