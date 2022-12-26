<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="业务类型" prop="bizTypeId">
        <el-select v-model="queryParams.bizTypeId" placeholder="业务类型" clearable>
          <el-option
            v-for="bizeType in bizTypeList"
            :key="bizeType.code"
            :label="bizeType.desc"
            :value="bizeType.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="配置是否可用" prop="configType">
        <el-select v-model="queryParams.enable" placeholder="配置是否可用" clearable>
          <el-option
            v-for="dict in dict.type.data_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
        >新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
        >修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
        >删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
        >导出
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-refresh"
          size="mini"
          @click="handleRefreshCache"
        >刷新缓存
        </el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="configList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column type="index" label="序号" />
      <el-table-column label="业务类型Id" align="center" prop="bizTypeId" />
      <el-table-column label="业务类型描述" align="center" prop="bizTypeDesc" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="enable">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.data_status" :value="scope.row.enable" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" align="center" prop="createTime">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="230px" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-detail"
            @click="handleDetails(scope.row)"
          >详情
          </el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
          >修改
          </el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
          >删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改参数配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="50%" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="180px" :disabled="formProps.disabled">
        <el-form-item label="业务类型Id" prop="bizTypeId">
          <el-input v-model="form.bizTypeId" placeholder="请输入业务类型Id" />
        </el-form-item>
        <el-form-item label="业务类型描述" prop="bizTypeDesc">
          <el-input v-model="form.bizTypeDesc" placeholder="请输入业务类型描述" />
        </el-form-item>
        <el-form-item label="状态" prop="enable">
          <el-radio-group v-model="form.enable">
            <el-radio
              v-for="dict in dict.type.data_status"
              :key="dict.value"
              :label="dict.value"
            >{{ dict.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="schema" prop="schema">
          <el-link
            size="mini"
            @click="handleChangeSchema()"
          >查看/ 添加/ 修改结构
          </el-link>
        </el-form-item>
        <el-form-item label="业务规则数据" prop="data">
          <vue-form
            v-model="form.data"
            :form-props="formProps"
            :schema="form.schema"
            :form-footer="{show:false}"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm" v-show="!formProps.disabled">确 定</el-button>
        <el-button @click="cancel">{{ formProps.disabled ? "关 闭" : "取 消" }}</el-button>
      </div>
    </el-dialog>
    <!-- 添加或修改结构对话框 -->
    <el-dialog
      title="结构详情"
      :visible.sync="schemaVisible"
      width="80%"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <schema-edit :schema-data="form.schema" @finish="finishedSchema($event)"></schema-edit>
    </el-dialog>
  </div>
</template>

<script>

import {delConfig, getBizTypeMap, getConfigByRuleId, getConfigPage, saveConfig, updateConfig} from '@/api/config'
import {refreshCache} from '@/api/dic'
import Pagination from '@/components/Pagination/index.vue'
import SchemaEdit from '@/components/SchemaEdit/index.vue'

export default {
  name: "Config",
  components: {SchemaEdit, Pagination},
  dicts: ['data_status'],
  data() {
    return {
      formProps: {
        layoutColumn: 3, // 1 2 3 ，支持 1 2 3 列布局，如果使用inline表单这里配置无效
        inline: true, // 行内表单模式，建议：开启时labelPosition不要配置top, antd不要配置labelCol wrapperCol
        inlineFooter: false, // 如果想要保存按钮和表单元素一行显示，需要配置 true
        labelSuffix: '：', // label后缀
        labelPosition: 'left', // 表单域标签的位置
        isMiniDes: false, // 是否优先mini形式显示描述信息（label文字和描述信息同行显示）
        defaultSelectFirstOption: true, // 单选框必填，是否默认选中第一个
        disabled: false
      },
      //业务类型
      bizTypeList: [],
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 参数表格数据
      configList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示schema弹出层
      schemaVisible: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        bizTypeId: undefined,
        enable: '1'
      },
      // 表单参数
      form: {
        schema: ''
      },
      // 表单校验
      rules: {
        enable: [
          {required: true, message: "状态不能为空", trigger: "blur"}
        ],
        bizTypeId: [
          {required: true, message: "业务类型不能为空", trigger: "blur"}
        ],
        bizTypeDesc: [
          {required: true, message: "业务类型描述不能为空", trigger: "blur"}
        ],
        data: [
          {required: true, message: "数据不能为空", trigger: "blur"}
        ],
        schema: [
          {required: true, message: "schema不能为空", trigger: "blur"}
        ]
      },
    };
  },
  created() {
    this.getBizTypeList();
    this.getList();
  },
  methods: {
    /** 查询参数列表 */
    getList() {
      this.loading = true;
      getConfigPage(this.queryParams).then(response => {
          let list = response.data.list
          this.configList = this.handleDataAndSchema(list)
          this.total = response.data.total;
          this.loading = false;
        }
      );
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        configId: undefined,
        configName: undefined,
        configKey: undefined,
        configValue: undefined,
        configType: "Y",
        remark: undefined
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = [];
      this.resetForm("queryForm");
      this.handleQuery();
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.formProps.disabled = false
      this.title = "添加参数";
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.configId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    /** 修改按钮操作 */
    handleDetails(row) {
      this.reset();
      const configId = row.ruleId || this.ids
      getConfigByRuleId(configId).then(response => {
        let data = response.data
        this.form = this.handleDataAndSchema(data)
        this.formProps.disabled = true
        this.open = true;
        this.title = "查看详情";
      });
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const configId = row.ruleId || this.ids
      getConfigByRuleId(configId).then(response => {
        let data = response.data
        this.form = this.handleDataAndSchema(data)
        this.open = true;
        this.formProps.disabled = false
        this.title = "修改参数";
      });
    },
    /** 提交按钮 */
    submitForm: function () {
      this.$refs["form"].validate(valid => {
        if (valid) {
          let data = this.form.data
          this.form.data = {default: data}
          this.form.data = JSON.stringify(this.form.data)
          this.form.schema = JSON.stringify(this.form.schema)
          if (this.form.ruleId !== undefined) {
            updateConfig(this.form).then(response => {
              this.$message.success("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            saveConfig(this.form).then(response => {
              this.$message.success("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const configIds = row.ruleId ? [row.ruleId] : this.ids;
      this.$confirm('是否确认删除参数编号为"' + configIds + '"的数据项？').then(function () {
        return delConfig(configIds);
      }).then(() => {
        this.getList();
        this.$message.success("删除成功");
      }).catch(() => {
      });
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('system/config/export', {
        ...this.queryParams
      }, `config_${new Date().getTime()}.xlsx`)
    },
    /** 刷新缓存按钮操作 */
    handleRefreshCache() {
      refreshCache().then(() => {
        this.$message.success("刷新成功");
      });
    },
    getBizTypeList() {
      getBizTypeMap()
        .then(res => {
          this.bizTypeList = res.data
        })
    },
    handleDataAndSchema(data) {
      if (Array.isArray(data)) {
        for (let item of data) {
          item.data = JSON.parse(item.data)
          item.schema = JSON.parse(item.schema)
        }
      } else {
        data.data = JSON.parse(data.data)
        data.schema = JSON.parse(data.schema)
      }
      return data
    },
    handleChangeSchema() {
      this.schemaVisible = true
    },
    finishedSchema(schema) {
      let s = this.form.schema

      this.form.schema = JSON.parse(JSON.stringify(schema))
      this.schemaVisible = false
    }
  }
};
</script>
