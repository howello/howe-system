<template>
  <HomeCard title="公告" icon-text="告" :link-text="'管理'" :link-to="'/system/notice'" link-icon-name="manage" link-title="进入公告管理">
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <template #default>
        <div class="notice-head">
          <span v-if="unreadCount > 0" class="notice-badge">{{ unreadCount }} 未读</span>
          <span v-else class="notice-badge is-read">全部已读</span>
        </div>
        <div v-if="list.length === 0" class="notice-empty">暂无公告</div>
        <div v-else>
          <div
            v-for="item in list"
            :key="item.noticeId"
            class="notice-item"
            :class="{ 'is-read': item.isRead }"
            @click="openNotice(item)"
          >
            <span class="notice-dot" :class="{ 'is-read': item.isRead }" />
            <span class="notice-title" :class="{ 'is-read': item.isRead }">{{ item.noticeTitle }}</span>
            <span class="notice-time">{{ formatDate(item.createTime) }}</span>
          </div>
        </div>
        <div class="notice-tip">点击任一条标记为已读（已读状态刷新后保持）</div>
      </template>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="NoticeCard">
// 公告卡：置顶与最新公告 + 未读徽标 + 点击标记已读
// 未读状态取自既有 sys_notice_read，标记后本地同步更新，无需整页刷新
import { ElMessage, ElMessageBox } from 'element-plus'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import request from '@/utils/request'
import type { AjaxResult, HomeNotice, HomeNoticeResult } from '@/types'
import { useCardData } from '../../use-card-data'

/** 首页公告展示条数（与后端 listTop 默认一致） */
const NOTICE_LIMIT = 5

const list = ref<HomeNotice[]>([])
const unreadCount = ref(0)

async function load(): Promise<void> {
    const res: HomeNoticeResult = await request({
      url: '/system/notice/listTop',
      method: 'get'
    })
    // listTop 用 AjaxResult.success(list) 包装，rows 或 data 都可能承载列表
    list.value = (res.rows ?? res.data ?? []) as HomeNotice[]
    unreadCount.value = res.unreadCount ?? list.value.filter((n: HomeNotice) => !n.isRead).length
}

/** 点击查看详情并标记已读 */
async function openNotice(item: HomeNotice): Promise<void> {
  if (!item.isRead) {
    try {
      await request({
        url: '/system/notice/markRead',
        method: 'post',
        params: { noticeId: item.noticeId }
      })
      item.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      ElMessage.warning('标记已读失败')
    }
  }
  if (item.noticeContent) {
    ElMessageBox.alert(item.noticeContent, item.noticeTitle, {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '知道了'
    }).catch(() => {})
  }
}

/** 时间显示为 MM-DD */
function formatDate(value?: string): string {
  if (!value) return ''
  const d = new Date(value.replace(/-/g, '/'))
  if (Number.isNaN(d.getTime())) return ''
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}-${dd}`
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload })
</script>

<style scoped lang="scss">
.notice-head {
  display: flex;
  align-items: center;
}

.notice-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;
  color: var(--home-card-bg);
  background: var(--home-accent-orange);

  &.is-read {
    color: var(--home-text-muted);
    background: var(--home-tile-bg);
    border: 1px solid var(--home-line);
    font-weight: 400;
  }
}

.notice-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  // 与待办行、键值行统一：浅底圆角行 + 透明边框，hover 时白底 + 阴影
  background: var(--home-tile-bg);
  border: 1px solid transparent;
  transition: all 0.16s;

  &:hover {
    border-color: var(--home-line);
    background: var(--home-card-bg);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }

  & + & {
    margin-top: 6px;
  }
}

.notice-dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-top: 7px;
  background: var(--home-accent-orange);

  &.is-read {
    background: transparent;
  }
}

.notice-title {
  color: var(--home-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.is-read {
    color: var(--home-text-muted);
  }
}

.notice-time {
  margin-left: auto;
  flex: none;
  font-size: 12px;
  color: var(--home-text-muted);
}

.notice-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--home-text-muted);
}

.notice-tip {
  font-size: 12px;
  color: var(--home-text-muted);
}
</style>
