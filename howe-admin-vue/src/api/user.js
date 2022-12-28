import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/login/login',
    method: 'post',
    data
  })
}

export function getInfo() {
  return request({
    url: '/user/info',
    method: 'get',
    isToken: true
  })
}

export function logout() {
  return request({
    url: '/login/logout',
    method: 'post'
  })
}

export function getValidCode(params) {
  return request({
    url: '/login/getValidCode',
    method: 'post',
    data: params
  })
}

export function getUserList(params) {
  return request({
    url: '/user/getUserList',
    method: 'get',
    data: params
  })
}

export function getUserPage(params) {
  return request({
    url: '/user/getUserPage',
    method: 'get',
    data: params
  })
}

export function getUser(params) {
  return request({
    url: '/user/getUser',
    method: 'get',
    params: {userId: params}
  })
}

export function changeUserStatus(params) {
  return request({
    url: '/user/changeUserStatus',
    method: 'put',
    params: {userId: params},
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}

export function resetUserPwd(data) {
  return request({
    url: '/user/resetUserPwd',
    method: 'post',
    data: data,
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}

export function updateUser(data) {
  return request({
    url: '/user/updateUser',
    method: 'put',
    data,
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}

export function addUser(data) {
  return request({
    url: '/user/addUser',
    method: 'put',
    data,
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}

export function delUser(data) {
  return request({
    url: '/user/delUser',
    method: 'delete',
    data,
    loading: true,
    confirmBefore: true,
    errorAlert: true
  })
}
