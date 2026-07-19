import { env } from '../config/env'

// 백엔드가 내려주는 이미지 경로(/uploads/xxx.png)는 백엔드 origin
// 기준 상대 경로다 - 로컬 개발(프론트 3001, 백엔드 8080)에선 그대로
// <img src>에 쓰면 프론트 origin으로 잘못 풀린다. 운영(같은 origin,
// VITE_API_BASE_URL="")에서는 apiBaseUrl이 빈 문자열이라 그대로도 맞는다.
export function resolveUploadUrl(path: string): string {
  return `${env.apiBaseUrl}${path}`
}
