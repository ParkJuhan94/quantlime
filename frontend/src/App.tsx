import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AppHeader } from './components/layout/AppHeader'
import { AppSidePanel } from './components/layout/AppSidePanel'
import { Toast } from './components/common/Toast'
import { OAuthCallbackPage } from './pages/OAuthCallbackPage'
import { HomePage } from './pages/HomePage'
import { FeedPage } from './pages/FeedPage'
import { StockDetailPage } from './pages/StockDetailPage'
import { IndexDetailPage } from './pages/IndexDetailPage'
import { MyInfoPage } from './pages/MyInfoPage'
import { SubscribePage } from './pages/SubscribePage'
import { PaymentResultPage } from './pages/PaymentResultPage'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { logoutToast } from './auth/logoutToast'

// OAuth 콜백/결제 체크아웃 화면에는 관심종목 패널을 띄우지 않는다.
const ROUTE_PREFIXES_WITHOUT_SIDE_PANEL = ['/oauth/callback', '/subscribe']

function App() {
  const [panelWidth, setPanelWidth] = useState(0)
  const [showLogoutToast, setShowLogoutToast] = useState(false)
  const location = useLocation()
  const showSidePanel = !ROUTE_PREFIXES_WITHOUT_SIDE_PANEL.some((prefix) =>
    location.pathname.startsWith(prefix),
  )

  // 세션 만료로 인한 강제 로그아웃(api/client.ts)은 React 상태 밖에서
  // window.location.assign으로 풀 페이지 이동을 하므로 sessionStorage
  // 플래그를 이 마운트 시점에 소비해 토스트를 띄운다. 명시적 로그아웃
  // (AppHeader)은 SPA 내 navigate()라 App이 리마운트되지 않으므로
  // onLoggedOut 콜백으로 직접 처리한다(아래 AppHeader).
  useEffect(() => {
    if (logoutToast.consumeLogoutToast()) {
      setShowLogoutToast(true)
    }
  }, [])

  return (
    <div className="min-h-screen bg-gray-100">
      <div
        className="transition-[margin-right] duration-200 ease-in-out"
        style={{ marginRight: showSidePanel ? panelWidth : 0 }}
      >
        <AppHeader onLoggedOut={() => setShowLogoutToast(true)} />
        <main className="mx-auto max-w-7xl px-4 py-6">
          <Routes>
            <Route path="/oauth/callback/:provider" element={<OAuthCallbackPage />} />
            <Route path="/stocks/:stockCode" element={<StockDetailPage />} />
            <Route path="/indices/:code" element={<IndexDetailPage />} />
            <Route path="/" element={<HomePage />} />
            <Route path="/feed" element={<FeedPage />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/me" element={<MyInfoPage />} />
              <Route path="/subscribe" element={<SubscribePage />} />
              <Route path="/subscribe/result" element={<PaymentResultPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>

      {showSidePanel && <AppSidePanel onWidthChange={setPanelWidth} />}
      {showLogoutToast && (
        <Toast message="퀀트라임에서 로그아웃 됐어요" onDismiss={() => setShowLogoutToast(false)} />
      )}
    </div>
  )
}

export default App
