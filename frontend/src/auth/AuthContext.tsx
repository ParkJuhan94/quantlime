import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { tokenStorage } from './tokenStorage'
import type { TokenResponse } from '../types/auth'

interface AuthContextValue {
  isAuthenticated: boolean
  setTokens: (tokens: TokenResponse) => void
  clearAuth: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(
    () => tokenStorage.getAccessToken() !== null,
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated,
      setTokens: (tokens) => {
        tokenStorage.setTokens(tokens)
        setIsAuthenticated(true)
      },
      clearAuth: () => {
        tokenStorage.clearTokens()
        setIsAuthenticated(false)
      },
    }),
    [isAuthenticated],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth는 AuthProvider 내부에서만 사용할 수 있습니다.')
  }
  return context
}
