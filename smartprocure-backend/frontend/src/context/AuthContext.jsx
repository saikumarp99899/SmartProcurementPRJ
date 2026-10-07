import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react'
import { authApi } from '../api/authApi'

const AuthContext = createContext(null)

const PROXY_SESSION_DURATION_MS = 60 * 60 * 1000 // 60 minutes

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user')
    return stored ? JSON.parse(stored) : null
  })
  const [loading, setLoading] = useState(true)

  // Proxy session state
  const [isProxy, setIsProxy] = useState(() => {
    return localStorage.getItem('isProxy') === 'true'
  })
  const [proxyUser, setProxyUser] = useState(() => {
    const stored = localStorage.getItem('proxyUser')
    return stored ? JSON.parse(stored) : null
  })

  const proxyTimerRef = useRef(null)

  // Clear any existing proxy expiry timer
  const clearProxyTimer = useCallback(() => {
    if (proxyTimerRef.current) {
      clearTimeout(proxyTimerRef.current)
      proxyTimerRef.current = null
    }
  }, [])

  // Restore admin session (shared between endProxySession and expiry handler)
  const restoreAdminSession = useCallback(async () => {
    clearProxyTimer()

    const originalToken = localStorage.getItem('originalToken')
    if (!originalToken) return

    // Restore original admin token
    localStorage.setItem('token', originalToken)
    localStorage.removeItem('originalToken')
    localStorage.removeItem('isProxy')
    localStorage.removeItem('proxyUser')
    localStorage.removeItem('proxyExpiresAt')

    // Clear proxy state
    setIsProxy(false)
    setProxyUser(null)

    // Reload admin user profile
    try {
      const res = await authApi.getCurrentUser()
      const userData = { ...res.data, roles: res.data.roles.map(r => `ROLE_${r}`) }
      setUser(userData)
      localStorage.setItem('user', JSON.stringify(userData))
    } catch {
      // If token is invalid, force logout
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      setUser(null)
    }
  }, [clearProxyTimer])

  // Handle proxy session expiry
  const handleProxyExpiry = useCallback(async () => {
    await restoreAdminSession()
    // Show notification to admin that proxy session expired
    if (window.alert) {
      window.alert('Proxy session expired. Your admin session has been restored.')
    }
  }, [restoreAdminSession])

  // Start the proxy expiry timer
  const startProxyTimer = useCallback((expiresAt) => {
    clearProxyTimer()
    const remaining = expiresAt - Date.now()
    if (remaining <= 0) {
      handleProxyExpiry()
      return
    }
    proxyTimerRef.current = setTimeout(handleProxyExpiry, remaining)
  }, [clearProxyTimer, handleProxyExpiry])

  // On mount: if a proxy session was active, resume the expiry timer
  useEffect(() => {
    if (isProxy) {
      const expiresAt = localStorage.getItem('proxyExpiresAt')
      if (expiresAt) {
        const expiry = parseInt(expiresAt, 10)
        if (Date.now() >= expiry) {
          // Session already expired while away — restore immediately
          handleProxyExpiry()
        } else {
          startProxyTimer(expiry)
        }
      }
    }
    return () => clearProxyTimer()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const token = localStorage.getItem('token')
    if (token && !user) {
      authApi.getCurrentUser()
        .then(res => {
          const userData = { ...res.data, roles: res.data.roles.map(r => `ROLE_${r}`) }
          setUser(userData)
          localStorage.setItem('user', JSON.stringify(userData))
        })
        .catch(() => {
          localStorage.removeItem('token')
          localStorage.removeItem('user')
        })
        .finally(() => setLoading(false))
    } else {
      setLoading(false)
    }
  }, [])

  const login = async (email, password) => {
    const res = await authApi.login({ email, password })
    const { accessToken, userId, firstName, lastName, roles } = res.data
    localStorage.setItem('token', accessToken)
    const userData = {
      id: userId,
      email: res.data.email,
      firstName,
      lastName,
      roles: roles.map(r => r.startsWith('ROLE_') ? r : `ROLE_${r}`),
    }
    setUser(userData)
    localStorage.setItem('user', JSON.stringify(userData))
    return userData
  }

  const logout = () => {
    // If in proxy session, clean up proxy state too
    clearProxyTimer()
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('originalToken')
    localStorage.removeItem('isProxy')
    localStorage.removeItem('proxyUser')
    localStorage.removeItem('proxyExpiresAt')
    setUser(null)
    setIsProxy(false)
    setProxyUser(null)
  }

  /**
   * Start a proxy session: store admin's token, switch to proxy token,
   * update user state to target user, and start expiry timer.
   * @param {string} proxyToken - The proxy JWT token from the backend
   * @param {object} targetUser - The target user's profile info
   */
  const startProxySession = (proxyToken, targetUser) => {
    const currentToken = localStorage.getItem('token')

    // Store admin's original token for later restoration
    localStorage.setItem('originalToken', currentToken)

    // Set the proxy token as the active token
    localStorage.setItem('token', proxyToken)

    // Store proxy state
    const proxyUserData = {
      id: targetUser.id || targetUser.targetUserId,
      email: targetUser.email || targetUser.targetEmail,
      firstName: targetUser.firstName || targetUser.targetFirstName,
      lastName: targetUser.lastName || targetUser.targetLastName,
      roles: (targetUser.roles || targetUser.targetRoles || []).map(
        r => r.startsWith('ROLE_') ? r : `ROLE_${r}`
      ),
    }

    setIsProxy(true)
    setProxyUser(proxyUserData)
    setUser(proxyUserData)

    localStorage.setItem('isProxy', 'true')
    localStorage.setItem('proxyUser', JSON.stringify(proxyUserData))
    localStorage.setItem('user', JSON.stringify(proxyUserData))

    // Set expiry timer (60 minutes from now)
    const expiresAt = Date.now() + PROXY_SESSION_DURATION_MS
    localStorage.setItem('proxyExpiresAt', expiresAt.toString())
    startProxyTimer(expiresAt)
  }

  /**
   * End the proxy session: call backend logout, restore admin session.
   */
  const endProxySession = async () => {
    try {
      await authApi.proxyLogout()
    } catch {
      // Even if backend call fails, still restore admin session locally
    }
    await restoreAdminSession()
  }

  const isAuthenticated = !!user && !!localStorage.getItem('token')

  const hasRole = (role) => user?.roles?.includes(role)

  if (loading) {
    return (
      <div className="h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    )
  }

  return (
    <AuthContext.Provider value={{
      user,
      login,
      logout,
      isAuthenticated,
      hasRole,
      isProxy,
      proxyUser,
      startProxySession,
      endProxySession,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
