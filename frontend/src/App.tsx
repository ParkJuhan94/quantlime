import { Navigate, Route, Routes } from 'react-router-dom'
import { AppHeader } from './components/layout/AppHeader'
import { PlaceholderPage } from './components/common/PlaceholderPage'
import { LoginPage } from './pages/LoginPage'
import { OAuthCallbackPage } from './pages/OAuthCallbackPage'
import { WatchlistPage } from './pages/WatchlistPage'
import { ProtectedRoute } from './auth/ProtectedRoute'

function App() {
  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader />
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/oauth/callback/:provider" element={<OAuthCallbackPage />} />
          <Route path="/stocks/:stockCode" element={<PlaceholderPage title="종목 상세" />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<WatchlistPage />} />
            <Route path="/dashboard" element={<PlaceholderPage title="대시보드" />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
