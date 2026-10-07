import { useState } from 'react'
import { useAuth } from '../context/AuthContext'

export default function ProxyBanner() {
  const { isProxy, proxyUser, endProxySession } = useAuth()
  const [ending, setEnding] = useState(false)

  if (!isProxy || !proxyUser) return null

  const displayName = `${proxyUser.firstName} ${proxyUser.lastName}`
  const displayRole = proxyUser.roles
    ?.map(r => r.replace('ROLE_', ''))
    .join(', ') || 'Unknown'

  const handleEndSession = async () => {
    setEnding(true)
    try {
      await endProxySession()
    } catch {
      // endProxySession already handles errors internally
    } finally {
      setEnding(false)
    }
  }

  return (
    <div
      className="fixed top-0 left-0 right-0 z-50 bg-amber-500 text-amber-950 shadow-md"
      role="alert"
      aria-live="polite"
    >
      <div className="flex items-center justify-between px-4 py-2 max-w-full">
        <div className="flex items-center gap-2 text-sm font-medium">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="h-5 w-5 flex-shrink-0"
            viewBox="0 0 20 20"
            fill="currentColor"
            aria-hidden="true"
          >
            <path
              fillRule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
              clipRule="evenodd"
            />
          </svg>
          <span>
            Viewing as <strong>{displayName}</strong> ({displayRole})
          </span>
        </div>
        <button
          onClick={handleEndSession}
          disabled={ending}
          className="ml-4 rounded bg-amber-900 px-3 py-1 text-xs font-semibold text-white hover:bg-amber-800 focus:outline-none focus:ring-2 focus:ring-amber-700 focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {ending ? 'Ending...' : 'End Proxy Session'}
        </button>
      </div>
    </div>
  )
}
