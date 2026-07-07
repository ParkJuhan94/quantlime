import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 백엔드 OAuth 리다이렉트 URI(.env의 GOOGLE_REDIRECT_URI 등)가
    // localhost:3001을 전제로 하므로, 프로바이더 콘솔 재등록을 피하기
    // 위해 이 포트를 고정한다(로컬에 Grafana가 3000번을 이미 점유해
    // Vite 기본값도 아니고 3000도 아닌 3001로 정함).
    port: 3001,
    strictPort: true,
  },
})
