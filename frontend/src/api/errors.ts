import { AxiosError } from 'axios'
import type { ErrorResponseTemplate } from '../types/error'

function extractErrorBody(error: unknown): ErrorResponseTemplate | undefined {
  if (error instanceof AxiosError) {
    return error.response?.data as ErrorResponseTemplate | undefined
  }
  return undefined
}

/** 백엔드 도메인 에러 코드(예: SC_000, ST_000)를 꺼낸다. 없으면 null. */
export function getErrorCode(error: unknown): string | null {
  return extractErrorBody(error)?.code ?? null
}

export function getErrorMessage(error: unknown, fallback = '알 수 없는 오류가 발생했습니다.'): string {
  return extractErrorBody(error)?.message ?? fallback
}

export function isNotFoundStatus(error: unknown): boolean {
  return error instanceof AxiosError && error.response?.status === 404
}
