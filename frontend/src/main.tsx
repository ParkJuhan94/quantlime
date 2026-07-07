import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthContext'

// React Query 기본값(재시도 3회, 지수 백오프)을 그대로 두면 백엔드가
// 실제로 다운됐을 때 사용자가 ErrorState를 보기까지 7초 이상 로딩
// 스피너만 보게 된다(직접 검증하며 확인) - 1회 재시도로 줄여 일시적인
// 네트워크 blip은 여전히 흡수하되, 진짜 장애는 더 빨리 알린다.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
