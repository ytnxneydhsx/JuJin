import { http } from '@/api/http'
import type { ApiResult, PageResult } from '@/types/http'
import type { ArticleCommentVO, CommentIdVO, CommentLikeToggleVO } from '@/types/models'

export interface ListCommentQuery {
  page?: number
  size?: number
}

export interface CreateCommentPayload {
  articleId: number
  parentId?: number
  content: string
}

export async function listRootComments(articleId: number, query: ListCommentQuery) {
  const { data } = await http.get<ApiResult<PageResult<ArticleCommentVO>>>(
    `/api/article/${articleId}/comment`,
    {
      params: query,
    },
  )
  return data.data
}

export async function listChildComments(articleId: number, commentId: number, query: ListCommentQuery) {
  const { data } = await http.get<ApiResult<PageResult<ArticleCommentVO>>>(
    `/api/article/${articleId}/comment/${commentId}/children`,
    {
      params: query,
    },
  )
  return data.data
}

export async function createComment(payload: CreateCommentPayload) {
  const { data } = await http.post<ApiResult<CommentIdVO>>('/api/me/comment', payload)
  return data.data
}

export async function deleteComment(commentId: number) {
  await http.delete<ApiResult<null>>(`/api/me/comment/${commentId}`)
}

export async function toggleCommentLike(commentId: number) {
  const { data } = await http.post<ApiResult<CommentLikeToggleVO>>(`/api/me/comment/${commentId}/like`)
  return data.data
}
