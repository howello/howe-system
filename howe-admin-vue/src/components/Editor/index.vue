<template>
  <div>
    <mavon-editor
      v-model="value"
      :language="language"
      :font-size="fontSize"
      :toolbars-background="toolbarsBackground"
      :preview-background="previewBackground"
      :subfield="subfield"
      :default-open="defaultOpen"
      :placeholder="placeholder"
      :editable="editable"
      :code-style="codeStyle"
      :toolbars-flag="toolbarsFlag"
      :navigation="navigation"
      :short-cut="shortCut"
      :autofocus="autofocus"
      :ishljs="ishljs"
      :image-filter="imageFilter"
      :image-click="imageClick"
      :tab-size="tabSize"
      :html="html"
      :toolbars="toolbars"
    />
  </div>
</template>

<script>


export default {
  name: "Editor",
  props: {
    /* 编辑器的内容 */
    value: {
      type: String,
      default: "",
    },
    /* 高度 */
    height: {
      type: Number,
      default: null,
    },
    /* 最小高度 */
    minHeight: {
      type: Number,
      default: null,
    },
    /* 只读 */
    readOnly: {
      type: Boolean,
      default: false,
    },
    // 上传文件大小限制(MB)
    fileSize: {
      type: Number,
      default: 5,
    },
    /* 类型（base64格式、url格式） */
    type: {
      type: String,
      default: "url",
    },
    fontSize: { // 字体大小
      type: String,
      default: '14px'
    },
    toolbarsBackground: { // 工具栏背景色
      type: String,
      default: '#ffffff'
    },
    editorBackground: { // TODO: 编辑栏背景色
      type: String,
      default: '#ffffff'
    },
    previewBackground: { // 预览栏背景色
      type: String,
      default: '#fbfbfb'
    },
    boxShadowStyle: { // 阴影样式
      type: String,
      default: '0 2px 12px 0 rgba(0, 0, 0, 0.1)'
    },
    help: {
      type: String,
      default: null
    },
    language: {  // 初始语言
      type: String,
      default: 'zh-CN'
    },
    subfield: {
      type: Boolean,
      default: true
    },
    navigation: {
      type: Boolean,
      default: false
    },
    defaultOpen: {
      type: String,
      default: null
    },
    editable: { // 是否开启编辑
      type: Boolean,
      default: true
    },
    toolbarsFlag: { // 是否开启工具栏
      type: Boolean,
      default: true
    },
    toolbars: { // 工具栏
      type: Object,
      default: {
        'bold': true,
        'italic': true,
        'header': true,
        'underline': true,
        'strikethrough': true,
        'mark': true,
        'superscript': true,
        'subscript': true,
        'quote': true,
        'ol': true,
        'ul': true,
        'link': true,
        'imagelink': true,
        'code': true,
        'table': true,
        'undo': true,
        'redo': true,
        'trash': true,
        'save': true,
        'alignleft': true,
        'aligncenter': true,
        'alignright': true,
        'navigation': true,
        'subfield': true,
        'fullscreen': true,
        'readmodel': true,
        'htmlcode': true,
        'help': true,
        'preview': true
      }
    },
    html: {// Enable HTML tags in source
      type: Boolean,
      default: false
    },
    xssOptions: { // XSS 选项
      type: [Object, Boolean],
      default() {
        return {}
      }
    },
    codeStyle: { // <code></code> 样式
      type: String,
      default() {
        return 'github';
      }
    },
    placeholder: { // 编辑器默认内容
      type: String,
      default: "请输入内容"
    },
    ishljs: {
      type: Boolean,
      default: true
    },
    autofocus: {
      type: Boolean,
      default: true
    },
    externalLink: {
      type: [Object, Boolean],
      default: true
    },
    imageFilter: {
      type: Function,
      default: null
    },
    imageClick: {
      type: Function,
      default: null
    },
    tabSize: {
      type: Number,
      default: 0
    },
    shortCut: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      uploadUrl: process.env.VUE_APP_BASE_API + "/common/upload", // 上传的图片服务器地址
      currentValue: "",
      options: {
        theme: "snow",
        bounds: document.body,
        debug: "warn",
        modules: {
          // 工具栏配置
          toolbar: [
            ["bold", "italic", "underline", "strike"],       // 加粗 斜体 下划线 删除线
            ["blockquote", "code-block"],                    // 引用  代码块
            [{list: "ordered"}, {list: "bullet"}],       // 有序、无序列表
            [{indent: "-1"}, {indent: "+1"}],            // 缩进
            [{size: ["small", false, "large", "huge"]}],   // 字体大小
            [{header: [1, 2, 3, 4, 5, 6, false]}],         // 标题
            [{color: []}, {background: []}],             // 字体颜色、字体背景颜色
            [{align: []}],                                 // 对齐方式
            ["clean"],                                       // 清除文本格式
            ["link", "image", "video"]                       // 链接、图片、视频
          ],
        },
        placeholder: "请输入内容",
        readOnly: this.readOnly,
      },
    };
  },
  computed: {
    styles() {
      let style = {};
      if (this.minHeight) {
        style.minHeight = `${this.minHeight}px`;
      }
      if (this.height) {
        style.height = `${this.height}px`;
      }
      return style;
    },
  },
  methods: {}
};
</script>

<style>
.editor, .ql-toolbar {
  white-space: pre-wrap !important;
  line-height: normal !important;
}
</style>
