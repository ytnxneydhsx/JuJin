import { http } from '@/api/http'
import type { ApiResult, PageResult } from '@/types/http'
import type { ArticleSearchVO, UserSearchVO } from '@/types/models'

export interface SearchQuery {
  q?: string
  userId?: number
  page?: number
  size?: number
}

export async function searchArticles(query: SearchQuery) {
  const { data } = await http.get<ApiResult<PageResult<ArticleSearchVO>>>('/api/search/articles', {
    params: query,
  })
  return data.data
}

export async function searchUsers(query: SearchQuery) {
  const { data } = await http.get<ApiResult<PageResult<UserSearchVO>>>('/api/search/users', {
    params: query,
  })
  return data.data
}

