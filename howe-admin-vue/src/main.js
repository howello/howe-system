import Vue from 'vue'
import App from './App'

import Cookies from 'js-cookie'

import 'normalize.css/normalize.css' // a modern alternative to CSS resets
import Element from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css';
import './styles/element-variables.scss'
import {download} from '@/utils/request'

import '@/styles/index.scss' // global css
import store from './store'
import router from './router'

import i18n from './lang' // internationalization
import './icons' // icon
import './permission' // permission control
import './utils/error-log' // error log
import * as filters from './filters' // global filters
// 自定义表格工具组件
import RightToolbar from "@/components/RightToolbar"
// 字典标签组件
import DictTag from '@/components/DictTag'
// 字典数据组件
import DictData from '@/components/DictData'
import {parseTime, resetForm} from '@/utils'
import tab from '@/utils/tab'
import JsonSchemaEditor from 'json-schema-editor-vue'
import 'json-schema-editor-vue/lib/json-schema-editor-vue.css'
import VueForm from '@lljj/vue-json-schema-form';


// 全局方法挂载
Vue.prototype.parseTime = parseTime
Vue.prototype.resetForm = resetForm
Vue.prototype.download = download
Vue.prototype.$tab = tab

Vue.component('RightToolbar', RightToolbar)
Vue.component('DictTag', DictTag)
Vue.component('VueForm', VueForm);
DictData.install()

Vue.use(Element, {
  size: Cookies.get('size') || 'medium', // set element-ui default size
  i18n: (key, value) => i18n.t(key, value)
})
Vue.use(JsonSchemaEditor)

// register global utility filters
Object.keys(filters).forEach(key => {
  Vue.filter(key, filters[key])
})

Vue.config.productionTip = false

new Vue({
  el: '#app',
  router,
  store,
  i18n,
  render: h => h(App)
}).$mount('#app')
