<template>
  <div class="app-container home">
    <WelcomeBanner />

    <!-- 统计卡（文章/草稿/说说 + 在线）：blog 不可用时由 PanelGroup 内部隐藏三张博客卡，
         在线卡不受影响；在线卡显隐由 :online-total 传 null 控制（无权限时传 null） -->
    <PanelGroup
      :blog-available="blogAvailable"
      :article-total="blog?.articleTotal ?? 0"
      :draft-total="blog?.draftTotal ?? 0"
      :talk-total="blog?.talkTotal ?? 0"
      :today="blog?.today"
      :online-total="hasOnline ? onlineTotal : null"
    />

    <!-- 趋势折线（整行）：博客可用才渲染；trend 为空时由组件自显空态 -->
    <div v-if="blogAvailable" class="home-trend">
      <div class="home-card">
        <TrendChart :trend="blog?.trend ?? []" />
      </div>
    </div>

    <!-- 三列：分类饼图 | 服务器雷达（无权限/无数据显示占位） | 最近动态 -->
    <div class="home-middle">
      <div v-if="blogAvailable" class="home-middle__col">
        <div class="home-card">
          <CategoryPie :category-counts="blog?.categoryCounts ?? []" />
        </div>
      </div>

      <div class="home-middle__col">
        <div class="home-card">
          <template v-if="hasServer">
            <template v-if="server">
              <ResourceRadar :server="server" />
            </template>
            <div v-else class="home-block-empty">暂无服务器数据</div>
          </template>
          <div v-else class="home-block-empty">无权限</div>
        </div>
      </div>

      <div class="home-middle__col">
        <DynamicList
          :blog-available="blogAvailable"
          :articles="articles"
          :drafts="drafts"
          :operlogs="operlogs"
          :can-operlog="hasOperlog"
        />
      </div>
    </div>

    <!-- 底部两栏：最近公告（2/3） | 服务器信息折叠卡（1/3）；≤768px 上下堆叠 -->
    <div class="home-bottom">
      <div class="home-bottom__notice">
        <RecentNotice :notices="notices" />
      </div>
      <div v-if="hasServer && server" class="home-bottom__server">
        <ServerInfoCard :server="server" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" name="Index">
// 仓库惯例：useUserStore 非 auto-import，需显式 import
import useUserStore from '@/store/modules/user'
import { getHomeStats } from '@/api/home/stats'
import { list as listOnline } from '@/api/monitor/online'
import { getServer } from '@/api/monitor/server'
import { listNoticeTop } from '@/api/system/notice'
import { listArticle } from '@/api/blog/article'
import { listDraft } from '@/api/blog/draft'
import { list as listOperlog } from '@/api/monitor/operlog'
import type {
  AjaxResult,
  TableDataInfo,
  HomeStats,
  HomeStatsBlog,
  MonitorServer,
  SysNotice,
  BlogArticle,
  BlogDraft,
  SysOperLog
} from '@/types'
import WelcomeBanner from './index/components/WelcomeBanner.vue'
import PanelGroup from './index/components/PanelGroup.vue'
import TrendChart from './index/components/TrendChart.vue'
import CategoryPie from './index/components/CategoryPie.vue'
import ResourceRadar from './index/components/ResourceRadar.vue'
import DynamicList from './index/components/DynamicList.vue'
import RecentNotice from './index/components/RecentNotice.vue'
import ServerInfoCard from './index/components/ServerInfoCard.vue'

// 权限判断（脚本层本地实现）：仓库 plugins/auth.ts 只提供 v-hasPermi 指令，
// 模板表达式与指令无法组合；按插件同语义直接读 user store（*:*:* 通配或逐项包含）
function hasPermi(perms: string[]): boolean {
  const permissions = useUserStore().permissions
  if (permissions.includes('*:*:*')) return true
  return perms.some((p: string) => permissions.includes(p))
}

const hasOnline = hasPermi(['monitor:online:list'])
const hasServer = hasPermi(['monitor:server:list'])
const hasOperlog = hasPermi(['monitor:operlog:list'])
const hasArticle = hasPermi(['blog:article:list'])
const hasDraft = hasPermi(['blog:draft:list'])

// 并发取数：Promise.allSettled 各自降级，任一接口失败只影响对应区块、不整页报错
const homeStats = ref<HomeStats | null>(null)
const onlineTotal = ref(0)
const server = ref<MonitorServer | null>(null)
const notices = ref<SysNotice[]>([])
const articles = ref<BlogArticle[]>([])
const drafts = ref<BlogDraft[]>([])
const operlogs = ref<SysOperLog[]>([])

/** 博客数据源是否可用；不可用时整组隐藏博客区块 */
const blogAvailable = computed<boolean>(() => homeStats.value?.blogAvailable ?? false)

/** 博客聚合块；blogAvailable=false 时为 null */
const blog = computed<HomeStatsBlog | null>(() =>
  blogAvailable.value ? (homeStats.value?.blog ?? null) : null
)

onMounted(() => {
  // 三个 monitor 接口带 @PreAuthorize，无对应权限会 403 弹窗；按权限门控，
  // 无权限时不发起请求，在线卡/服务器/操作日志区块各自走占位展示
  const jobs: Promise<unknown>[] = [
    listNoticeTop().then((r) => {
      notices.value = r.data ?? []
    }),
    // 博客列取数由 getHomeStats 结果驱动：blog 表未建（blogAvailable=false）或缺
    // blog:article:list / blog:draft:list 权限时不发起请求，避免 blog 接口 500/403 toast
    getHomeStats().then((r: AjaxResult<HomeStats>) => {
      homeStats.value = r.data ?? null
      if (homeStats.value?.blogAvailable) {
        const blogJobs: Promise<unknown>[] = []
        if (hasArticle) {
          blogJobs.push(
            listArticle({ pageNum: 1, pageSize: 5 }).then((r: TableDataInfo<BlogArticle[]>) => {
              articles.value = r.rows ?? []
            })
          )
        }
        if (hasDraft) {
          blogJobs.push(
            listDraft({ pageNum: 1, pageSize: 5 }).then((r: TableDataInfo<BlogDraft[]>) => {
              drafts.value = r.rows ?? []
            })
          )
        }
        return Promise.allSettled(blogJobs)
      }
    })
  ]
  if (hasOnline) {
    jobs.push(listOnline({}).then((r) => {
      onlineTotal.value = r.total ?? 0
    }))
  }
  if (hasServer) {
    jobs.push(getServer().then((r) => {
      server.value = (r.data as MonitorServer) ?? null
    }))
  }
  if (hasOperlog) {
    jobs.push(listOperlog({ pageNum: 1, pageSize: 5 }).then((r) => {
      operlogs.value = r.rows ?? []
    }))
  }
  Promise.allSettled(jobs)
})
</script>

<style scoped lang="scss">
.home {
  // .app-container 已提供 padding: 20px，这里补背景与区块间距
  min-height: calc(100vh - 84px);
  background: #f0f2f5;

  // 统一卡片容器：图表类子组件（TrendChart/CategoryPie/ResourceRadar）为纯渲染，
  // 卡片外框由组装台提供，与 DynamicList/RecentNotice 自带卡片观感一致
  .home-card {
    padding: 16px;
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    box-sizing: border-box;
  }

  .home-trend {
    margin-bottom: 20px;
  }

  .home-middle {
    display: flex;
    gap: 20px;
    margin-bottom: 20px;

    &__col {
      flex: 1;
      min-width: 0;
    }
  }

  .home-bottom {
    display: flex;
    gap: 20px;

    &__notice {
      flex: 2;
      min-width: 0;
    }

    &__server {
      flex: 1;
      min-width: 0;
    }
  }

  // 服务器雷达区的「无权限 / 暂无数据」占位（高度与 320px 图表一致）
  .home-block-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 320px;
    color: #909399;
    font-size: 14px;
  }
}

// 三列 ≤991px 上下堆叠
@media (max-width: 991px) {
  .home {
    .home-middle {
      flex-direction: column;
    }
  }
}

// 底部两栏 ≤768px 上下堆叠
@media (max-width: 768px) {
  .home {
    .home-bottom {
      flex-direction: column;
    }
  }
}
</style>
