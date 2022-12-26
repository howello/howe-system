import request from '@/utils/request'

export function getBizTypeMap() {
  return request({
    url: '/config/getBizTypeMap',
    method: 'get',
  })
}

export function getConfigPage(params) {
  return request({
    url: '/config/getConfigPage',
    method: 'get',
    params,
    loading: true
  })
}

export function getConfigByRuleId(params) {
  return request({
    url: '/config/getConfigByRuleId',
    method: 'get',
    params: {ruleId: params},
    loading: true
  })
}

export function updateConfig(data) {
  return request({
    url: '/config/updateConfig',
    method: 'put',
    data,
    loading: true,
    confirmBefore: true
  })
}

export function saveConfig(data) {
  return request({
    url: '/config/saveConfig',
    method: 'put',
    data,
    loading: true,
    confirmBefore: true
  })
}

export function delConfig(data) {
  return request({
    url: '/config/delConfig',
    method: 'delete',
    data,
    loading: true,
    confirmBefore: true
  })
}

export function refreshCache() {
  return request({
    url: '/config/refreshCache',
    method: 'get',
    confirmBefore: true
  })
}
