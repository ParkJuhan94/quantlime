import { describe, expect, it, vi } from 'vitest'

// env.apiBaseUrl은 빌드 타임 환경변수(import.meta.env)라 모듈 자체를 대체한다
vi.mock('../config/env', () => ({ env: { apiBaseUrl: 'http://localhost:8080' } }))

import { resolveUploadUrl } from './uploadUrl'

describe('resolveUploadUrl', () => {
  it('백엔드가 준 상대 경로 앞에 API origin을 붙인다(로컬 개발에서 프론트 origin으로 풀리지 않게)', () => {
    expect(resolveUploadUrl('/uploads/a.png')).toBe('http://localhost:8080/uploads/a.png')
  })
})
