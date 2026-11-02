import 'vue'

/** main.ts 与 plugins/index.ts 中实际注册的全局方法。 */
declare module 'vue' {
  interface ComponentCustomProperties {
    useDict: typeof import('@/utils/dict')['useDict']
    download: typeof import('@/utils/request')['download']
    parseTime: typeof import('@/utils/commonUtils')['parseTime']
    resetForm: typeof import('@/utils/commonUtils')['resetForm']
    handleTree: typeof import('@/utils/commonUtils')['handleTree']
    addDateRange: typeof import('@/utils/commonUtils')['addDateRange']
    getConfigKey: typeof import('@/api/system/config')['getConfigKey']
    selectDictLabel: typeof import('@/utils/commonUtils')['selectDictLabel']
    selectDictLabels: typeof import('@/utils/commonUtils')['selectDictLabels']
    $tab: typeof import('@/plugins/tab')['default']
    $auth: typeof import('@/plugins/auth')['default']
    $cache: typeof import('@/plugins/cache')['default']
    $modal: typeof import('@/plugins/modal')['default']
    $download: typeof import('@/plugins/download')['default']
  }
}
