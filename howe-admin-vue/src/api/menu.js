import request from '@/utils/request'


export function getMenuList() {
  return request({
    url: '/menu/getMenuList',
    method: 'post'
  })
}

export function getMenuPage(data) {
  return request({
    url: '/menu/getMenuPage',
    method: 'post',
    data
  })
}
