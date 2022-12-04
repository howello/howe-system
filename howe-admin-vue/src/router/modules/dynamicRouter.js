/* Layout */
import Layout from '@/layout'
import { getMenuList } from '@/api/user'

const dynamicRouter = {
  'test': getViews('test')
}

// 生成路由懒加载组件地址
function getViews(path) {
  return resolve => {
    require.ensure([], require => {
      resolve(require('@/views/' + path + '.vue'))
    })
  }
}

// 递归处理后端返回的路由数据
function generate(arr) {
  arr = arr.filter(item => item.status === '0')
  return arr.map(item => {
    if (!item.children || item.children.length === 0) {
      const path = item.path.replace('#', '')
      return {
        path: path || '#',
        name: path,
        component: dynamicRouter[path],
        meta: {
          title: item.menuName,
          icon: item.icon
        }
      }
    } else {
      return {
        component: Layout,
        // 展开菜单加入标记 防止同时折叠
        path: '#/TAG/' + item.menuId,
        meta: {
          title: item.menuName,
          icon: item.icon
        },
        children: generate(item.children)
      }
    }
  })
}

// 从后端获取路由数据
let menu = null
export const getRoutes = async(path, fresh = false) => {
  if (menu === null || fresh || path === '/Home') {
    const res = await getMenuList()
    menu = generate(res.data || [])
  }
  return menu
}

export default dynamicRouter
