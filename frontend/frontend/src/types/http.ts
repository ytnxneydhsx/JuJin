export interface ApiResult<T> {
  code: string
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
  hasNext: boolean
}
