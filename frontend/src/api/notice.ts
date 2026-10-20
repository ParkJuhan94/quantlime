import { apiClient } from './client'
import type {
  CreateNoticeRequest,
  CreateNoticeResponse,
  Notice,
  NoticeSummary,
  UpdateNoticeRequest,
} from '../types/notice'
import type { PageResponse } from '../types/stock'

// 열람은 로그인 없이도 가능(백엔드 GET /api/notices/** permitAll)
export async function getNotices(page = 0, size = 20): Promise<PageResponse<NoticeSummary>> {
  const { data } = await apiClient.get<PageResponse<NoticeSummary>>('/api/notices', { params: { page, size } })
  return data
}

export async function getNotice(noticeId: number): Promise<Notice> {
  const { data } = await apiClient.get<Notice>(`/api/notices/${noticeId}`)
  return data
}

// ---- 관리자 ----

export async function createNotice(request: CreateNoticeRequest): Promise<CreateNoticeResponse> {
  const { data } = await apiClient.post<CreateNoticeResponse>('/api/admin/notices', request)
  return data
}

export async function updateNotice(noticeId: number, request: UpdateNoticeRequest): Promise<Notice> {
  const { data } = await apiClient.put<Notice>(`/api/admin/notices/${noticeId}`, request)
  return data
}

export async function deleteNotice(noticeId: number): Promise<void> {
  await apiClient.delete(`/api/admin/notices/${noticeId}`)
}
