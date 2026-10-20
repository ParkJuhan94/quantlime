export interface NoticeSummary {
  id: number
  title: string
  // 줄바꿈·연속 공백을 합쳐 앞부분만 자른 미리보기(목록용)
  preview: string
  createdAt: string
}

export interface Notice {
  id: number
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface CreateNoticeRequest {
  title: string
  content: string
  // true면 등록과 함께 전체 사용자에게 알림(링크 /notices/{id})을 보낸다
  sendNotification: boolean
}

export interface CreateNoticeResponse {
  id: number
  notificationRequested: boolean
  // 요청했는데 false면 공지는 등록됐지만 알림 발송이 실패한 것이다
  notificationSent: boolean
}

export interface UpdateNoticeRequest {
  title: string
  content: string
}
