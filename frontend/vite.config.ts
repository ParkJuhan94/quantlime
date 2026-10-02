import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // sockjs-client(STOMP WebSocket 폴백)가 Node.js 전역 `global`을
  // 참조하는데 브라우저엔 없어 "global is not defined"로 깨진다.
  // Vite는 webpack과 달리 Node 전역을 자동 폴리필하지 않으므로 직접 지정.
  define: {
    global: 'globalThis',
  },
  // 순수 함수(utils) 중심 단위 테스트 - 컴포넌트 렌더링 테스트는 도입하지 않았다(UI가
  // 자주 바뀌어 유지 비용이 이득보다 크다고 판단, 2026-10-01). Node 환경이면 충분하다.
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      include: ['src/utils/**/*.ts'],
      exclude: ['src/**/*.test.ts'],
      reporter: ['text', 'lcov'],
    },
  },
  server: {
    // 백엔드 OAuth 리다이렉트 URI(.env의 GOOGLE_REDIRECT_URI 등)가
    // localhost:3001을 전제로 하므로, 프로바이더 콘솔 재등록을 피하기
    // 위해 이 포트를 고정한다(로컬에 Grafana가 3000번을 이미 점유해
    // Vite 기본값도 아니고 3000도 아닌 3001로 정함).
    port: 3001,
    strictPort: true,
  },
})
