import { Navigate, Route, Routes } from 'react-router-dom'
import { AppHeader } from './components/layout/AppHeader'
import { LoginPage } from './pages/LoginPage'
import { OAuthCallbackPage } from './pages/OAuthCallbackPage'
import { HomePage } from './pages/HomePage'
import { FeedPlaceholderPage } from './pages/FeedPlaceholderPage'
import { StockDetailPage } from './pages/StockDetailPage'
import { DashboardPage } from './pages/DashboardPage'
import { ProtectedRoute } from './auth/ProtectedRoute'

function App() {
  return (
    <div className="min-h-screen bg-gray-50">
      <AppHeader />
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/oauth/callback/:provider" element={<OAuthCallbackPage />} />
          <Route path="/stocks/:stockCode" element={<StockDetailPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/feed" element={<FeedPlaceholderPage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
