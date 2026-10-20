// 관리자 트리거 실패를 사람이 읽을 문구로 바꾼다. axios를 import하지 않고 오류 모양만 본다(순수 함수라 단위 테스트가 쉽다).
interface MaybeHttpError {
  code?: string
  response?: { status?: number; data?: { message?: string } | string }
}

export function describeAdminError(error: unknown): string {
  const e = (error ?? {}) as MaybeHttpError
  const status = e.response?.status

  // nginx 기본 읽기 제한(60초)에 걸리면 응답만 끊기고 서버 쪽 처리는 계속되는 경우가 많다(백테스트·수집 등)
  if (status === 502 || status === 503 || status === 504 || e.code === 'ECONNABORTED') {
    return '응답이 지연돼 연결이 끊겼어요. 서버에서는 계속 처리 중일 수 있으니 잠시 뒤 결과를 확인해 주세요.'
  }
  if (e.code === 'ERR_NETWORK') {
    return '서버에 연결하지 못했어요. 네트워크나 서버 상태를 확인해 주세요.'
  }
  const data = e.response?.data
  if (data && typeof data === 'object' && typeof data.message === 'string' && data.message) {
    return data.message
  }
  if (status === 403) return '관리자 권한이 필요해요.'
  if (status === 401) return '로그인이 만료됐어요. 다시 로그인해 주세요.'
  return '요청에 실패했어요. 잠시 후 다시 시도해 주세요.'
}
