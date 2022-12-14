import request from '@/utils/request'


export function getMenuListWithPermit() {
  return request({
    url: '/menu/getMenuListWithPermit',
    method: 'post'
  })
}

export function getMenuList(params) {
  return request({
    url: '/menu/getMenuList',
    method: 'get',
    params,
    loading: true
  })
}

export function getMenuListByRoleId(params) {
  return request({
    url: '/menu/getMenuListByRoleId',
    method: 'get',
    params: {roleId: params}
  })
}

export function getMenu(params) {
  return request({
    url: '/menu/getMenu',
    method: 'get',
    params: {menuId: params}
  })
}

export function updateMenu(data) {
  return request({
    url: '/menu/updateMenu',
    method: 'post',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function saveMenu(data) {
  return request({
    url: '/menu/saveMenu',
    method: 'post',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function delMenu(params) {
  return request({
    url: '/menu/delMenu',
    method: 'delete',
    params: {menuId: params},
    confirmBefore: true,
    loading: true
  })
}
