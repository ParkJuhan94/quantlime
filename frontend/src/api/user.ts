import { apiClient } from './client'
import type { UserMeResponse } from '../types/user'

export async function getMe(): Promise<UserMeResponse> {
  const { data } = await apiClient.get<UserMeResponse>('/api/users/me')
  return data
}
