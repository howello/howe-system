/** 服务器-CPU */
export interface ServerCpu {
  cpuNum: number
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
