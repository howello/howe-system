import axios from 'axios'
import {Loading, Message, MessageBox} from 'element-ui'
import store from '@/store'
import {isJson} from '@/utils/validate'

let downloadLoadingInstance;

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
axios.defaults.withCredentials = false
// create an axios instance
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API, // url = base url + request url
  withCredentials: false, // send cookies when cross-domain requests
  timeout: 50000 // request timeout
})

const loadingUrls = new Set()
let loadingInstance = {
  close: () => {
  }
}

// request interceptor
service.interceptors.request.use(
  async config => {
    // 是否需要设置 token
    const isToken = config.isToken || false
    // 需要用户确认的请求
    if (config.confirmBefore) {
      await MessageBox.confirm(config.confirmBeforeContent ? config.confirmBeforeContent : '您当前正在进行数据变更操作,请确定是否继续？', '操作提示', {
        type: 'warning',
        customClass: 'primary-box'
      })
        .catch(err => {
          throw new Error("已取消该请求")
        })
    }
    config.headers['Accept'] = 'application/json;charset=utf-8';
    config.headers['Content-Type'] = 'application/json;charset=utf-8';
    // do something before request is sent
    // 设置请求头
    let token = store.getters.token
    let method = config.method
    if (token && (method !== "get" || isToken)) {
      config.headers['token'] = token
    }
    if (config.loading === true) {
      const text = config.loadingText
      if (text) {
        loadingInstance = Loading.service({
          fullscreen: true,
          text
        })
      } else {
        loadingInstance = Loading.service({
          fullscreen: true
        })
      }
      loadingUrls.add(config.baseURL + config.url)
    }
    return config
  },
  error => {
    if (error === 'cancel') {
      return {
        code: -1
      }
    }
    // do something with request error
    console.log('request:' + error) // for debug
    return Promise.reject(error)
  }
)

// response interceptor
service.interceptors.response.use(
  /**
   * If you want to get http information such as headers or status
   * Please return  response => response
   */

  /**
   * Determine the request status by custom code
   * Here is just an example
   * You can also judge the status by HTTP Status Code
   */
  response => {
    if (loadingUrls.has(response.config.url)) {
      loadingUrls.delete(response.config.url)

      // 使用宏任务检查是否关闭，避免微任务内重复打开
      setTimeout(() => {
        if (loadingUrls.size === 0) {
          loadingInstance.close()
        }
      }, 0)
    }
    const res = response.data
    if (res.code !== 0) {
      Message({
        message: res.message || 'Error',
        type: 'error',
        duration: 5 * 1000
      })

      // 50008: Illegal token; 50012: Other clients logged in; 50014: Token expired;
      if (res.code === 1012 || res.code === 50012 || res.code === 50014) {
        // to re-login
        MessageBox.confirm('You have been logged out, you can cancel to stay on this page, or log in again', 'Confirm logout', {
          confirmButtonText: 'Re-Login',
          cancelButtonText: 'Cancel',
          type: 'warning'
        }).then(() => {
          store.dispatch('user/resetToken').then(() => {
            location.reload()
          })
        })
      }
      return Promise.reject(new Error(res.message || 'Error'))
    } else {
      return res
    }
  },
  error => {
    Message({
      message: error.message,
      type: 'error',
      duration: 5 * 1000
    })
    return Promise.reject(error)
  }
)


// 通用下载方法
export function download(url, params, filename, config) {
  downloadLoadingInstance = Loading.service({text: `正在下载${filename}，请稍候...`, spinner: "el-icon-loading", background: "rgba(0, 0, 0, 0.7)",})
  return service.post(url, params, {
    transformRequest: params,
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    responseType: 'blob',
    ...config
  }).then(async (data) => {
    let text = await data.text()
    //TODO 有问题
    const isLogin = isJson(text);
    if (isLogin) {
      const blob = new Blob([data])
      saveAs(blob, filename)
    } else {
      const rspObj = JSON.parse(text);
      const errMsg = rspObj.code + rspObj.msg
      Message.error(errMsg);
    }
    downloadLoadingInstance.close();
  }).catch((r) => {
    console.error(r)
    Message.error('下载文件出现错误，请联系管理员！')
    downloadLoadingInstance.close();
  })
}

export default service
