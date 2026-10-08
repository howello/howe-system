/**
 * API 类型统一导出
 */
export * from "./common";

// 登录模块
export * from "./login";
export * from "./menu";

// System 模块
export * from "./system/user";
export * from "./system/role";
export * from "./system/menu";
export * from "./system/dept";
export * from "./system/post";
export * from "./system/dict";
export * from "./system/config";
export * from "./system/notice";

// monitor 模块
export * from "./monitor/cache";
export * from "./monitor/job";
export * from "./monitor/jobLog";
export * from "./monitor/logininfor";
export * from "./monitor/operlog";
export * from "./monitor/online";
export * from "./monitor/server";

// 博客模块
export * from "./blog/article";
export * from "./blog/draft";
export * from "./blog/link";
export * from "./blog/feed";
export * from "./blog/talk";

// AI 调用网关
export * from "./ai/channel";
export * from "./ai/model";
export * from "./ai/route";
export * from "./ai/calllog";
export * from "./ai/playground";

// 家庭点餐
export * from "./meal/category";
export * from "./meal/dish";
export * from "./meal/order";
export * from "./meal/review";
export * from "./meal/proposal";

// 首页工作台
export * from "./home/stats";

// 代码生成模块
export * from "./tool/gen";
