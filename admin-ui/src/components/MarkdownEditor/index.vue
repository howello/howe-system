<template>
   <div class="markdown-editor">
      <MdEditor
         v-model="content"
         :theme="theme"
         :preview="preview"
         :toolbars="toolbars"
         :style="{ height: height + 'px' }"
         language="zh-CN"
         :disabled="disabled"
         @onUploadImg="handleUploadImg"
      />
   </div>
</template>

<script setup lang="ts" name="MarkdownEditor">
import axios from "axios"
import { MdEditor } from "md-editor-v3"
import "md-editor-v3/lib/style.css"
import { getToken } from "@/utils/auth"

const props = defineProps({
  /** 绑定值（markdown 原文） */
  modelValue: {
    type: String,
    default: ""
  },
  /** 编辑器高度（px） */
  height: {
    type: Number,
    default: 520
  },
  /** 是否显示预览 */
  preview: {
    type: Boolean,
    default: true
  },
  /** 是否禁用 */
  disabled: {
    type: Boolean,
    default: false
  },
  /** 单张图片大小限制（MB） */
  imageSize: {
    type: Number,
    default: 5
  }
})

const emit = defineEmits(["update:modelValue"])

const { proxy } = getCurrentInstance()

// 固定浅色：与 blog-ui 的 shiki github-light 代码高亮主题保持一致，预览效果贴近线上
const theme = "light"

const content = computed({
  get: () => props.modelValue ?? "",
  set: (val: string) => emit("update:modelValue", val)
})

// 去掉不适用于静态博客的按钮：保存到本地、修改历史等
const toolbars: any[] = [
  "bold", "underline", "italic", "strikeThrough", "-",
  "title", "sub", "sup", "quote", "unorderedList", "orderedList", "task", "-",
  "codeRow", "code", "link", "image", "table", "mermaid", "katex", "-",
  "revoke", "next", "=", "pageFullscreen", "fullscreen", "preview", "previewOnly", "catalog"
]

/**
 * 图片上传：直接传到图床，编辑器里插入返回的外链
 *
 * 文章最终要提交到 GitHub 由 Astro 静态构建，正文里必须是绝对地址，
 * 所以取后端返回的 url（R2 模式下就是 img 域名的完整链接）。
 */
async function handleUploadImg(files: File[], callback: (urls: string[]) => void) {
  const urls: string[] = []
  for (const file of files) {
    if (file.size / 1024 / 1024 > props.imageSize) {
      proxy.$modal.msgError(`图片大小不能超过 ${props.imageSize}MB`)
      continue
    }
    const formData = new FormData()
    formData.append("file", file)
    try {
      const res = await axios.post(import.meta.env.VITE_APP_BASE_API + "/common/upload", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
          Authorization: "Bearer " + getToken()
        }
      })
      if (res.data.code === 200) {
        urls.push(res.data.url)
      } else {
        proxy.$modal.msgError(res.data.msg || "图片上传失败")
      }
    } catch (e: any) {
      proxy.$modal.msgError("图片上传失败：" + (e.message || e))
    }
  }
  callback(urls)
}
</script>

<style scoped>
.markdown-editor :deep(.md-editor) {
   border-radius: 4px;
}
</style>
