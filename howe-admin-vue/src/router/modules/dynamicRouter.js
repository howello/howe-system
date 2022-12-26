/* Layout */
import Layout from '@/layout'
import {getMenuListWithPermit} from '@/api/menu'
import store from '@/store'

const dynamicRouter = {
  '/setting/menu': getViews('system-setting/menu/index'),
  '/setting/dic': getViews('system-setting/dict/index'),
  '/setting/dic/data': getViews('system-setting/dict/data'),
  '/setting/role': getViews('system-setting/role/index'),
  '/setting/user': getViews('system-setting/user/index'),
  '/setting/config': getViews('system-setting/config/index')
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
    const path = item.path.replace('#', '')
    if (!item.children || item.children.length === 0) {
      return {
        path: path || '#',
        name: path,
        component: dynamicRouter[path],
        hidden: item.visible === '1',
        meta: {
          title: item.menuName,
          icon: item.icon,
        }
      }
    } else {
      return {
        component: isTopParent ? Layout : getParentView(),
        // 展开菜单加入标记 防止同时折叠
        path: path,
        hidden: item.visible === '1',
        meta: {
          title: item.menuName,
          icon: item.icon,
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
    const res = await getMenuListWithPermit()
    menu = generate(res.data || [])
  }
  await store.dispatch("user/setRouter")
  return menu
}

export default dynamicRouter
