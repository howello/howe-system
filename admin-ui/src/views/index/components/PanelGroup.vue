<template>
  <el-row :gutter="20" class="panel-group">
    <!-- 文章 -->
    <el-col v-if="props.blogAvailable ?? true" :xs="12" :sm="12" :md="6" :lg="6" class="card-panel-col">
      <div class="card-panel" @click="goTo('/blog/article')">
        <div class="card-panel-icon-wrapper icon-article">
          <el-icon class="card-panel-icon"><Document /></el-icon>
        </div>
        <div class="card-panel-description">
          <div class="card-panel-text">文章</div>
          <div class="card-panel-num">{{ Math.round(articleNum) }}</div>
          <div class="card-panel-today">今日 +{{ todayArticles }}</div>
        </div>
      </div>
    </el-col>

    <!-- 草稿 -->
    <el-col v-if="props.blogAvailable ?? true" :xs="12" :sm="12" :md="6" :lg="6" class="card-panel-col">
      <div class="card-panel" @click="goTo('/blog/draft')">
        <div class="card-panel-icon-wrapper icon-draft">
          <el-icon class="card-panel-icon"><EditPen /></el-icon>
        </div>
        <div class="card-panel-description">
          <div class="card-panel-text">草稿</div>
          <div class="card-panel-num">{{ Math.round(draftNum) }}</div>
          <div class="card-panel-today">今日 +{{ todayDrafts }}</div>
        </div>
      </div>
    </el-col>

    <!-- 说说 -->
    <el-col v-if="props.blogAvailable ?? true" :xs="12" :sm="12" :md="6" :lg="6" class="card-panel-col">
      <div class="card-panel" @click="goTo('/blog/talk')">
        <div class="card-panel-icon-wrapper icon-talk">
          <el-icon class="card-panel-icon"><ChatDotRound /></el-icon>
        </div>
        <div class="card-panel-description">
          <div class="card-panel-text">说说</div>
          <div class="card-panel-num">{{ Math.round(talkNum) }}</div>
          <div class="card-panel-today">今日 +{{ todayTalks }}</div>
        </div>
      </div>
    </el-col>

    <!-- 在线用户：无 monitor:online:list 权限时父组件传 null，此卡整体隐藏；无今日增量 -->
    <el-col v-if="showOnline" :xs="12" :sm="12" :md="6" :lg="6" class="card-panel-col">
      <div class="card-panel" @click="goTo('/monitor/online')">
        <div class="card-panel-icon-wrapper icon-online">
          <el-icon class="card-panel-icon"><User /></el-icon>
        </div>
        <div class="card-panel-description">
          <div class="card-panel-text">在线用户</div>
          <div class="card-panel-num">{{ Math.round(onlineNum) }}</div>
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup lang="ts" name="PanelGroup">
import { useTransition } from '@vueuse/core'
// 图标未全局注册（main.ts 未注册 @element-plus/icons-vue），需按需引入
import { Document, EditPen, ChatDotRound, User } from '@element-plus/icons-vue'
import type { HomeStatsToday } from '@/types'

const props = defineProps<{
  /** 文章总数 */
  articleTotal: number
  /** 草稿总数 */
  draftTotal: number
  /** 说说总数 */
  talkTotal: number
  /** 在线用户数；无 monitor:online:list 权限时为 null，此时隐藏在线卡 */
  onlineTotal: number | null
  /** 今日新增计数，接口未返回时按 0 展示 */
  today?: HomeStatsToday
  /** blog 表不可用时 index 传 false，隐藏三张博客统计卡、保留在线卡；未传默认 true，兼容旧调用 */
  blogAvailable?: boolean
}>()

const router = useRouter()

/** 数字滚动时长（毫秒） */
const DURATION = 800

// 数字滚动：@vueuse/core 的 useTransition，零新增依赖
const articleNum = useTransition(toRef(props, 'articleTotal'), { duration: DURATION })
const draftNum = useTransition(toRef(props, 'draftTotal'), { duration: DURATION })
const talkNum = useTransition(toRef(props, 'talkTotal'), { duration: DURATION })
// onlineTotal 可能为 null，用 getter 归一成 0 再交给 useTransition（卡片本身由 showOnline 控制显隐）
const onlineNum = useTransition(() => props.onlineTotal ?? 0, { duration: DURATION })

// 今日增量：仅文章/草稿/说说三卡展示，缺省为 0（显示 +0，不隐藏）
const todayArticles = computed<number>(() => props.today?.articles ?? 0)
const todayDrafts = computed<number>(() => props.today?.drafts ?? 0)
const todayTalks = computed<number>(() => props.today?.talks ?? 0)

/** 仅当父组件传入数字时展示在线卡；null/undefined 表示无权限 */
const showOnline = computed<boolean>(() => props.onlineTotal !== null && props.onlineTotal !== undefined)

/** 点击卡片跳转到对应列表页 */
function goTo(path: string) {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.panel-group {
  .card-panel-col {
    margin-bottom: 20px;
  }

  .card-panel {
    display: flex;
    align-items: center;
    height: 108px;
    padding: 0 18px;
    cursor: pointer;
    font-size: 12px;
    position: relative;
    overflow: hidden;
    background: var(--el-bg-color-overlay, #fff);
    border: 1px solid var(--el-border-color-lighter, rgba(0, 0, 0, 0.05));
    border-radius: 4px;
    box-shadow: 4px 4px 40px rgba(0, 0, 0, 0.05);
    transition: box-shadow 0.28s ease-out, transform 0.28s ease-out;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 4px 4px 40px rgba(0, 0, 0, 0.12);

      .card-panel-icon-wrapper {
        color: #fff;
      }

      .icon-article {
        background: #40c9c6;
      }

      .icon-draft {
        background: #36a3f7;
      }

      .icon-talk {
        background: #34bfa3;
      }

      .icon-online {
        background: #f4516c;
      }
    }
  }

  .icon-article {
    color: #40c9c6;
  }

  .icon-draft {
    color: #36a3f7;
  }

  .icon-talk {
    color: #34bfa3;
  }

  .icon-online {
    color: #f4516c;
  }

  .card-panel-icon-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 14px;
    border-radius: 6px;
    transition: all 0.38s ease-out;
  }

  .card-panel-icon {
    font-size: 40px;
  }

  .card-panel-description {
    flex: 1;
    min-width: 0;
    margin-left: 12px;
    text-align: right;
    font-weight: bold;
  }

  .card-panel-text {
    line-height: 18px;
    color: var(--el-text-color-secondary, rgba(0, 0, 0, 0.45));
    font-size: 15px;
    margin-bottom: 8px;
  }

  .card-panel-num {
    font-size: 22px;
    line-height: 26px;
    color: var(--el-text-color-primary, #303133);
  }

  .card-panel-today {
    margin-top: 6px;
    font-size: 12px;
    font-weight: normal;
    color: var(--el-text-color-secondary, #909399);
  }
}

/* ≤991px：两列（由 el-col 的 xs/sm 跨度 12 提供） */
/* ≤550px：单列 */
@media (max-width: 550px) {
  .panel-group {
    .card-panel-col {
      width: 100% !important;
      max-width: 100% !important;
      flex: 0 0 100% !important;
      margin-bottom: 14px;
    }

    .card-panel {
      height: 88px;
    }

    .card-panel-icon {
      font-size: 34px;
    }
  }
}
</style>
