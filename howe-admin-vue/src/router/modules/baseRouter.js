/**
 * 默认路由，不用配置
 */
import Layout from '@/layout'

export const baseRouter = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login/index'),
    hidden: true
  },
  {
    path: '/auth-redirect',
    component: () => import('@/views/login/auth-redirect'),
    hidden: true
  },
  {
    path: '/404',
    component: () => import('@/views/error-page/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error-page/401'),
    hidden: true
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        name: 'Dashboard',
        meta: { title: 'dashboard', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/documentation',
    component: Layout
    // children: [
    //   {
    //     path: 'index',
    //     component: () => import('@/views/documentation/index'),
    //     name: 'Documentation',
    //     meta: { title: 'documentation', icon: 'documentation', affix: true }
    //   }
    // ]
  },
  {
    path: '/guide',
    component: Layout,
    redirect: '/guide/index'
    // children: [
    //   {
    //     path: 'index',
    //     component: () => import('@/views/guide/index'),
    //     name: 'Guide',
    //     meta: { title: 'guide', icon: 'guide', noCache: true }
    //   }
    // ]
  },
  {
    path: '/profile',
    component: Layout,
    redirect: '/profile/index',
    hidden: true
    // children: [
    //   {
    //     path: 'index',
    //     component: () => import('@/views/profile/index'),
    //     name: 'Profile',
    //     meta: { title: 'profile', icon: 'user', noCache: true }
    //   }
    // ]
  }
]

export default baseRouter
