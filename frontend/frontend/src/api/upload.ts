import { http } from '@/api/http'
import type { ApiResult } from '@/types/http'
import type { UploadImageVO } from '@/types/models'

export async function uploadImage(bizType: string, file: File) {
  const form = new FormData()
  form.append('bizType', bizType)
  form.append('file', file)
  const { data } = await http.post<ApiResult<UploadImageVO>>('/api/me/upload/image', form, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return data.data
}

