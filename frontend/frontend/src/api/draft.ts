import { http } from '@/api/http'
import type { ApiResult, PageResult } from '@/types/http'
import type { ArticleDraftVO, ArticleIdVO, DraftIdVO } from '@/types/models'

export interface SaveDraftPayload {
  articleId?: number
  title?: string
  summary?: string
  coverUrl?: string
  content?: string
}

export async function createDraft(payload: SaveDraftPayload) {
  const { data } = await http.post<ApiResult<DraftIdVO>>('/api/me/draft', payload)
  return data.data
}

export async function updateDraft(draftId: number, payload: SaveDraftPayload) {
  await http.put<ApiResult<null>>(`/api/me/draft/${draftId}`, payload)
}

export async function getDraft(draftId: number) {
  const { data } = await http.get<ApiResult<ArticleDraftVO>>(`/api/me/draft/${draftId}`)
  return data.data
}

export async function listDrafts(page = 0, size = 20) {
  const { data } = await http.get<ApiResult<PageResult<ArticleDraftVO>>>('/api/me/draft', {
    params: {
      page,
      size,
    },
  })
  return data.data
}

export async function publishDraft(draftId: number) {
  const { data } = await http.post<ApiResult<ArticleIdVO>>(`/api/me/draft/${draftId}/publish`)
  return data.data
}

export async function deleteDraft(draftId: number) {
  await http.delete<ApiResult<null>>(`/api/me/draft/${draftId}`)
}

