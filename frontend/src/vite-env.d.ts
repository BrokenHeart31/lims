/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 基础路径（禁止硬编码接口地址，统一从环境变量读取） */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
