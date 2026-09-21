/**
 * AI 接口调试台类型
 *
 * 这些类型与后端 com.howe.ai.api.dto 下的契约一一对应：
 * 调试台发出去的请求就是业务模块调用 AiClient 时传的对象，
 * 所以这里的字段名不能改，改了就不再是真实契约的验证。
 */

/** 对话消息 */
export interface AiMessage {
  /** 角色（system / user / assistant） */
  role: string;
  /** 消息内容 */
  content: string;
}

/** 用量信息 */
export interface AiUsage {
  /** 输入 token 数 */
  inputTokens?: number;
  /** 输出 token 数 */
  outputTokens?: number;
  /** 总 token 数 */
  totalTokens?: number;
}

/** 文本对话结果 */
export interface AiChatResult {
  /** 生成的文本 */
  text?: string;
  /** 结构化输出结果，非结构化调用时为 null */
  structured?: unknown;
  /** 实际生效的服务商协议 */
  provider?: string;
  /** 实际生效的模型名 */
  model?: string;
  /** 用量信息 */
  usage?: AiUsage;
  /** 本次调用耗时（毫秒） */
  elapsedMs?: number;
  /** 结束原因 */
  finishReason?: string;
  /** 调用记录ID */
  callLogId?: number;
}

/** 流式输出的文本分片 */
export interface AiTextChunk {
  /** 本次增量文本 */
  delta?: string;
  /** 本次增量的思考内容（推理模型的 reasoning_content），协议不返回时为空 */
  reasoningDelta?: string;
  /** 是否结束 */
  done: boolean;
  /** 结束时的完整结果与用量 */
  result?: AiChatResult;
  /** 出错时的错误码，此时 done 为 true */
  errorCode?: string;
  /** 出错时的可读说明，如厂商返回的「模型不存在」 */
  errorMessage?: string;
}

/** 生成图 */
export interface AiImage {
  /** 永久访问地址 */
  url?: string;
  /** 对象存储中的对象键 */
  key?: string;
  /** 宽度（像素） */
  width?: number;
  /** 高度（像素） */
  height?: number;
}

/** 文生图结果 */
export interface AiImageResult {
  /** 生成图列表 */
  images?: AiImage[];
  /** 实际使用的完整 prompt，含风格后缀 */
  prompt?: string;
  /** 实际生效的服务商协议 */
  provider?: string;
  /** 实际生效的模型名 */
  model?: string;
  /** 本次调用耗时（毫秒） */
  elapsedMs?: number;
  /** 调用记录ID */
  callLogId?: number;
  /** 异步任务ID，第一版恒为 null */
  taskId?: string;
}

/** 文本向量结果 */
export interface AiEmbedResult {
  /** 向量列表，顺序与请求的 texts 一致 */
  embeddings?: number[][];
  /** 实际生效的服务商协议 */
  provider?: string;
  /** 实际生效的模型名 */
  model?: string;
  /** 本次调用耗时（毫秒） */
  elapsedMs?: number;
  /** 调用记录ID */
  callLogId?: number;
}

/** 调试台请求的公共字段 */
export interface AiPlaygroundBase {
  /** 指定模型名，留空走场景路由或全局默认 */
  model?: string;
  /** 指定渠道ID（后端按字符串接收），留空走场景路由或全局默认 */
  channel?: string;
  /** 场景标识，留空表示绕开场景路由直接指定渠道与模型 */
  scene?: string;
}

/** 文本对话请求 */
export interface AiChatPayload extends AiPlaygroundBase {
  /** 单轮简写 */
  prompt?: string;
  /** 系统提示词 */
  system?: string;
  /** 多轮消息列表，与 prompt 二选一 */
  messages?: AiMessage[];
  /** 采样温度 */
  temperature?: number;
  /** 最大输出 token 数 */
  maxTokens?: number;
}

/** 图片理解请求 */
export interface AiVisionPayload extends AiChatPayload {
  /** 图片列表，公网 URL 或 data:image/...;base64, 形式 */
  images?: string[];
}

/** 文生图请求 */
export interface AiImagePayload extends AiPlaygroundBase {
  /** 正向提示词 */
  prompt?: string;
  /** 负向提示词 */
  negativePrompt?: string;
  /** 尺寸，如 1024*1024 */
  size?: string;
  /** 生成张数 */
  count?: number;
  /** 随机种子 */
  seed?: number;
}

/** 文本向量请求 */
export interface AiEmbedPayload extends AiPlaygroundBase {
  /** 待向量化的文本列表 */
  texts?: string[];
  /** 目标维度，留空用模型默认 */
  dimensions?: number;
}
