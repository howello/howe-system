import request from '@/utils/request'

export function getRoleList(params) {
  return request({
    url: '/role/getRoleList',
    method: 'get',
    params,
    loading: true
  })
}

export function getRolePage(params) {
  return request({
    url: '/role/getRolePage',
    method: 'get',
    params,
    loading: true
  })
}

export function getRole(params) {
  return request({
    url: '/role/getRole',
    method: 'get',
    params: {roleId: params},
    loading: true
  })
}

export function changeRoleStatus(data) {
  return request({
    url: '/role/changeRoleStatus',
    method: 'post',
    data,
    loading: true,
    confirmBefore: true
  })
}

export function addRole(data) {
  return request({
    url: '/role/addRole',
    method: 'post',
    data,
    loading: true,
    confirmBefore: true
  })
}

export function updateRole(data) {
  return request({
    url: `/role/updateRole`,
    method: 'put',
    data,
    loading: true,
    confirmBefore: true
  })
}

export function deleteRole(data) {
  return request({
    url: `/role/delRole`,
    method: 'DELETE',
    data,
    loading: true,
    confirmBefore: true
  })
}
