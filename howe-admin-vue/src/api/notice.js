import request from '@/utils/request'

export function getNoticeList(params) {
  return request({
    url: '/notice/getNoticeList',
    method: 'get',
    data: params
  })
}

export function getNoticePage(params) {
  return request({
    url: '/notice/getNoticePage',
    method: 'get',
    data: params
  })
}

export function getNotice(params) {
  return request({
    url: '/notice/getNotice',
    method: 'get',
    params: {noticeId: params}
  })
}

export function updateNotice(data) {
  return request({
    url: '/notice/updateNotice',
    method: 'put',
    data,
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}

export function addNotice(data) {
  return request({
    url: '/notice/addNotice',
    method: 'put',
    data,
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}

export function delNotice(data) {
  return request({
    url: '/notice/delNotice',
    method: 'delete',
    data,
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}
