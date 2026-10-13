import type { PageDomain, BaseEntity } from "../common";

/** App 版本分页查询参数 */
export interface MealAppVersionQueryParams extends PageDomain {
  /** 版本名称（模糊匹配） */
  versionName?: string;
  /** 发布状态（0已发布 1停用） */
  status?: string;
}

/**
 * 点餐-App 版本（wgt 热更新）
 *
 * 一条记录 = 一个已上传到对象存储（R2）的 wgt 应用资源包；只服务 wgt 资源热更新，
 * 不承载整包（apk/ipa）升级。
 */
export interface MealAppVersion extends BaseEntity {
  /** 版本记录ID */
  versionId?: number;
  /** 版本名称（展示用） */
  versionName?: string;
  /** 版本序号（比较基准，整数递增） */
  versionCode?: number;
  /** wgt 资源包地址（R2） */
  wgtUrl?: string;
  /** 资源包字节大小 */
  wgtSize?: number;
  /** 上传时的原始文件名 */
  fileName?: string;
  /** 更新说明（换行分隔） */
  updateLog?: string;
  /** 是否强制更新（0否 1是） */
  forceUpdate?: "0" | "1";
  /** 发布状态（0已发布 1停用） */
  status?: "0" | "1";
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}

/**
 * wgt 资源包上传结果
 *
 * 与后端 `POST /meal/app/version/upload` 一致：地址、大小、文件名放在响应的 `data` 里，
 * 与打包脚本 `scripts/publish-wgt.mjs` 读取的字段保持一致。
 */
export interface WgtUploadResult {
  /** 可访问的资源包地址 */
  url: string;
  /** 字节大小 */
  size: number;
  /** 原始文件名 */
  fileName: string;
}
