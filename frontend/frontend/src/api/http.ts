import axios from 'axios'
import type { ApiResult } from '@/types/http'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export const http = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 15000,
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (!result || typeof result.code !== 'string') {
      return response
    }
    if (result.code !== 'SUCCESS') {
      return Promise.reject(new Error(result.message || 'Request failed'))
    }
    return response
  },
  (error) => {
    const message =
      error?.response?.data?.message ||
      error?.message ||
      'Network error'
    return Promise.reject(new Error(message))
  },
)
