/** 服务器-CPU */
export interface ServerCpu {
  cpuNum: number
  /** CPU 型号 */
  name?: string
  /** 系统 1/5/15 分钟平均负载 */
  loadAverage?: number[]
  total: number
  sys: number
  used: number
  wait: number
  free: number
}

/** 服务器-内存 */
export interface ServerMem {
  total: number
  used: number
  free: number
  usage: number
}

/** 服务器-JVM */
export interface ServerJvm {
  total: number
  used: number
  free: number
  usage: number
  name?: string
  version?: string
  home?: string
  startTime?: string
  runTime?: string
  inputArgs?: string
  /** 非堆内存：已用（MB） */
  nonHeapUsed?: number
  /** 非堆内存：已提交总量（MB） */
  nonHeapTotal?: number
  /** 非堆内存使用率（%） */
  nonHeapUsage?: number
  /** GC 总次数 */
  gcCount?: number
  /** GC 总耗时（毫秒） */
  gcTime?: number
}

/** 服务器-磁盘分区 */
export interface ServerFile {
  dirName: string
  sysTypeName: string
  typeName: string
  total: string
  free: string
  used: string
  usage: number
}

/** 服务器-操作系统 */
export interface ServerSys {
  computerName: string
  computerIp: string
  osName: string
  osArch: string
  userDir?: string
}

/** 服务器监控详情（GET /monitor/server 的 data） */
export interface MonitorServer {
  cpu: ServerCpu
  mem: ServerMem
  jvm: ServerJvm
  sys: ServerSys
  sysFiles: ServerFile[]
}
