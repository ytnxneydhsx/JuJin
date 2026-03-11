import { http } from '@/api/http'
import type { ApiResult } from '@/types/http'
import type { UserPublicProfileVO, UserRegisterVO } from '@/types/models'

export interface RegisterPayload {
  account: string
  password: string
  name?: string
}

export interface LoginPayload {
  account: string
  password: string
}

export interface UpdateNamePayload {
  name: string
}

export interface UpdateSignPayload {
  sign: string
}

export interface UpdateAvatarPayload {
  avatarUrl: string
}

export interface UpdatePasswordPayload {
  oldPassword: string
  newPassword: string
}

export async function register(payload: RegisterPayload) {
  const { data } = await http.post<ApiResult<UserRegisterVO>>(
    '/api/me/user/register',
    payload,
  )
  return data.data
}

export async function login(payload: LoginPayload) {
  const { data } = await http.post<ApiResult<UserRegisterVO>>(
    '/api/me/user/login',
    payload,
  )
  return data.data
}

export async function logout() {
  await http.post<ApiResult<null>>('/api/me/user/logout')
}

export async function getUserProfile(userId: number) {
  const { data } = await http.get<ApiResult<UserPublicProfileVO>>(`/api/user/${userId}`)
  return data.data
}

export async function updateName(payload: UpdateNamePayload) {
  await http.put<ApiResult<null>>('/api/me/user/profile/name', payload)
}

export async function updateSign(payload: UpdateSignPayload) {
  await http.put<ApiResult<null>>('/api/me/user/profile/sign', payload)
}

export async function updateAvatar(payload: UpdateAvatarPayload) {
  await http.put<ApiResult<null>>('/api/me/user/profile/avatar', payload)
}

export async function updatePassword(payload: UpdatePasswordPayload) {
  await http.put<ApiResult<null>>('/api/me/user/password', payload)
}
