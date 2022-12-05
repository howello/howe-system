/* Layout */
import Layout from '@/layout'
import {getMenuList} from '@/api/user'

const dynamicRouter = {
  '/add-menu': getViews('menu/admin-menu/add-menu/index'),
}

// 生成路由懒加载组件地址
function getViews(path) {
  return resolve => {
    require.ensure([], require => {
      resolve(require('@/views/' + path + '.vue'))
    })
  }
}

function getParentView() {
  return resolve => {
    require.ensure([], require => {
      resolve(require('@/components/ParentView/index.vue'))
    })
  }
}

// 递归处理后端返回的路由数据
function generate(arr, isTopParent = true) {
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
        component: isTopParent ? Layout : getParentView(),
        // 展开菜单加入标记 防止同时折叠
        path: '/' + item.menuId,
        meta: {
          title: item.menuName,
          icon: item.icon
        },
        children: generate(item.children, false)
      }
    }
  })
}

// 从后端获取路由数据
let menu = null
export const getRoutes = async (path, fresh = false) => {
  if (menu === null || fresh || path === '/Home') {
    const res = await getMenuList()
    menu = generate(res.data || [])
  }
  return menu
}

export default dynamicRouter
