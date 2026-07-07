import { createContext } from 'react'
import type { TokenResponse } from '../types/auth'

export interface AuthContextValue {
  isAuthenticated: boolean
  setTokens: (tokens: TokenResponse) => void
  clearAuth: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
