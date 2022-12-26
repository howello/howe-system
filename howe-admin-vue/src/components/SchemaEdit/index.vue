<template>
  <div>
    <json-schema-editor class="schema" :value="tree"></json-schema-editor>
    <el-button type="success" @click="save">保存并关闭</el-button>
  </div>
</template>

<script>

export default {
  name: 'SchemaEdit',
  props: {
    schemaData: {
      type: Object,
      default: undefined
    }
  },
  created() {
    if (this.schemaData) {
      this.tree.root = this.schemaData
    }
  },
  data() {
    return {
      tree: {
        root: {}
      }
    }
  },
  methods: {
    save() {
      this.$emit('finish', this.tree.root)
    }
  }
}
</script>

<style scoped>
.schema {
  margin-left: 20px;
  border: 1px solid rgba(0, 0, 0, .1);
  border-radius: 8px;
  padding: 12px;
}
</style>
