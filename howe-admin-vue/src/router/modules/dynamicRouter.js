/* Layout */
import Layout from '@/layout'

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
  arr = arr.filter(item => item.menuStas === '1')
  return arr.map(item => {
    if (item.childList.length === 0) {
      const path = item.menuUrl.replace('#', '')
      return {
        path: path || '#',
        name: path,
        component: dynamicRouter[path],
        meta: {
          title: item.menuName,
          icon: item.menuIcon
        }
      }
    } else {
      return {
        component: Layout,
        // 展开菜单加入标记 防止同时折叠
        path: '#/TAG/' + item.menuId,
        meta: {
          title: item.menuName,
          icon: item.menuIcon
        },
        children: generate(item.childList)
      }
    }
  })
}

// 从后端获取路由数据
let menu = null
export const getRoutes = async(comMenuName, path, fresh = false) => {
  if (menu === null || fresh || path === '/Home') {
    // const res = await selectCwMenu({
    //   cwMenuCodg: comMenuName
    // })
    // TODO selectMenu 获取菜单接口
    const res = {}
    menu = generate(res.data || [])
  }
  return menu
}
