import request from '@/utils/request'

const path = `/dic`

export function getAllDicList() {
  return request({
    url: `${path}/getAllDicList`,
    method: 'get'
  })
}

export function getDicPage(params) {
  return request({
    url: `${path}/getDicPage`,
    method: 'get',
    params
  })
}

export function getDicDataByDictCode(params) {
  return request({
    url: `${path}/getDicDataByDictCode`,
    method: 'get',
    params: {dicCode: params}
  })
}

export function getDicDataListByType(params) {
  return request({
    url: `${path}/getDicDataListByType`,
    method: 'get',
    params
  })
}

export function saveDicData(data) {
  return request({
    url: `${path}/saveDicData`,
    method: 'post',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function updateDicDate(data) {
  return request({
    url: `${path}/updateDicDate`,
    method: 'post',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function delDicData(data) {
  return request({
    url: `${path}/delDicData`,
    method: 'delete',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function getAllDicTypeList() {
  return request({
    url: `${path}/getAllDicTypeList`,
    method: 'GET'
  })
}

export function getDicTypePage(params) {
  return request({
    url: `${path}/getDicTypePage`,
    method: 'GET',
    params
  })
}

export function getDicTypeById(params) {
  return request({
    url: `${path}/getDicTypeById`,
    method: 'GET',
    params: {dicTypeId: params}
  })
}

export function updateDicType(data) {
  return request({
    url: `${path}/updateDicType`,
    method: 'POST',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function saveDicType(data) {
  return request({
    url: `${path}/saveDicType`,
    method: 'POST',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function delDicType(data) {
  return request({
    url: `${path}/delDicType`,
    method: 'DELETE',
    data,
    confirmBefore: true,
    loading: true
  })
}

export function refreshCache() {
  return request({
    url: `${path}/refreshCache`,
    method: 'get'
  })
}
