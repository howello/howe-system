import {getInfo, login, logout} from '@/api/user'
import {getToken, removeToken, setToken} from '@/utils/auth'
import {resetRouter} from '@/router'

const state = {
  token: getToken(),
  name: '',
  avatar: '',
  introduction: '',
  router: false
}

const mutations = {
  SET_TOKEN: (state, token) => {
    state.token = token
  },
  SET_NICK_NAME: (state, introduction) => {
    state.introduction = introduction
  },
  SET_NAME: (state, name) => {
    state.name = name
  },
  SET_AVATAR: (state, avatar) => {
    state.avatar = avatar
  },
  SET_ROUTER: (state, router) => {
    state.router = router
  }
}

const actions = {
  // user login
  login({commit}, userInfo) {
    return new Promise((resolve, reject) => {
      login(userInfo).then(response => {
        var token = response.data
        commit('SET_TOKEN', token)
        setToken(token)
        resolve()
      }).catch(error => {
        reject(error)
      })
    })
  },

  // get user info
  getInfo({commit}) {
    return new Promise((resolve, reject) => {
      getInfo().then(response => {
        const {data} = response
        if (!data) {
          reject('Verification failed, please Login again.')
        }
        commit('SET_NAME', username)
        commit('SET_AVATAR', avatar)
        commit('SET_NICK_NAME', nickName)
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },

  // user logout
  logout({commit, state, dispatch}) {
    return new Promise((resolve, reject) => {
      logout(state.token).then(() => {
        commit('SET_TOKEN', '')
        removeToken()
        resetRouter()

        // reset visited views and cached views
        // to fixed https://github.com/PanJiaChen/issues/2485
        dispatch('tagsView/delAllViews', null, {root: true})

        resolve()
      }).catch(error => {
        reject(error)
      })
    })
  },

  // remove token
  resetToken({commit}) {
    return new Promise(resolve => {
      commit('SET_TOKEN', '')
      removeToken()
      resolve()
    })
  },

  setRouter({commit}) {
    return new Promise(resolve => {
      commit('SET_ROUTER', true)
      resolve()
    })

  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
