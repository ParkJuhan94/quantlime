import { useMemo, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue } from './context'
import { tokenStorage } from './tokenStorage'

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
