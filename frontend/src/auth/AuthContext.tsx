import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, TOKEN_KEY } from '../api/client'
import type { Role, UserProfile } from '../api/types'

interface AuthState {
  user: UserProfile | null
  ready: boolean
  login: (username: string, password: string) => Promise<UserProfile>
  logout: () => void
  hasRole: (...roles: Role[]) => boolean
}

const AuthContext = createContext<AuthState | null>(null)
const USER_KEY = 'ciq.user'

function store(key: string, value: string | null) {
  try {
    if (value == null) sessionStorage.removeItem(key)
    else sessionStorage.setItem(key, value)
  } catch {
    /* storage unavailable – session lasts for this page only */
  }
}

function loadUser(): UserProfile | null {
  try {
    const raw = sessionStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserProfile) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(loadUser)
  const [ready, setReady] = useState(false)
  const qc = useQueryClient()

  const logout = useCallback(() => {
    store(TOKEN_KEY, null)
    store(USER_KEY, null)
    setUser(null)
    qc.clear()
  }, [qc])

  useEffect(() => {
    // Validate a restored session against the server once on load.
    if (!user) {
      setReady(true)
      return
    }
    api.get<UserProfile>('/auth/me')
      .then((r) => setUser(r.data))
      .catch(() => logout())
      .finally(() => setReady(true))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const onUnauthorized = () => logout()
    window.addEventListener('ciq:unauthorized', onUnauthorized)
    return () => window.removeEventListener('ciq:unauthorized', onUnauthorized)
  }, [logout])

  const login = useCallback(async (username: string, password: string) => {
    const { data } = await api.post<{ token: string; user: UserProfile }>('/auth/login', { username, password })
    store(TOKEN_KEY, data.token)
    store(USER_KEY, JSON.stringify(data.user))
    setUser(data.user)
    return data.user
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, ready, login, logout, hasRole: (...roles) => !!user && roles.includes(user.role) }),
    [user, ready, login, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
