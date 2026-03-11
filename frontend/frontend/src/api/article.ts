import { http } from '@/api/http'
import type { ApiResult, PageResult } from '@/types/http'
import type {
  ArticleDetailVO,
  ArticleFavoriteVO,
  ArticleLikeVO,
  ArticleSummaryVO,
} from '@/types/models'

export interface ListArticleQuery {
  userId?: number
  sortBy?: 'publishedAt' | 'viewCount'
  sortOrder?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface UpdateArticlePayload {
  title: string
  summary?: string
  coverUrl?: string
  content: string
  status?: 1 | 2
}

export async function listArticles(query: ListArticleQuery) {
  const { data } = await http.get<ApiResult<PageResult<ArticleSummaryVO>>>('/api/article', {
    params: query,
  })
  return data.data
}

export async function getArticle(articleId: number) {
  const { data } = await http.get<ApiResult<ArticleDetailVO>>(`/api/article/${articleId}`)
  return data.data
}

export async function toggleLikeArticle(articleId: number) {
  const { data } = await http.post<ApiResult<ArticleLikeVO>>(`/api/me/article/${articleId}/like`)
  return data.data
}

export async function toggleFavoriteArticle(articleId: number) {
  const { data } = await http.post<ApiResult<ArticleFavoriteVO>>(
    `/api/me/article/${articleId}/favorite`,
  )
  return data.data
}

export async function listMyArticles(page = 0, size = 20) {
  const { data } = await http.get<ApiResult<PageResult<ArticleSummaryVO>>>('/api/me/article', {
    params: { page, size },
  })
  return data.data
}

export async function getMyArticle(articleId: number) {
  const { data } = await http.get<ApiResult<ArticleDetailVO>>(`/api/me/article/${articleId}`)
  return data.data
}

export async function updateMyArticle(articleId: number, payload: UpdateArticlePayload) {
  await http.put<ApiResult<null>>(`/api/me/article/${articleId}`, payload)
}

export async function deleteMyArticle(articleId: number) {
  await http.delete<ApiResult<null>>(`/api/me/article/${articleId}`)
}
