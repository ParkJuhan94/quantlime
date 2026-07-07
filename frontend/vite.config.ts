import { defineConfig } from 'vite'
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
  server: {
    // 백엔드 OAuth 리다이렉트 URI(.env의 GOOGLE_REDIRECT_URI 등)가
    // localhost:3001을 전제로 하므로, 프로바이더 콘솔 재등록을 피하기
    // 위해 이 포트를 고정한다(로컬에 Grafana가 3000번을 이미 점유해
    // Vite 기본값도 아니고 3000도 아닌 3001로 정함).
    port: 3001,
    strictPort: true,
  },
})
