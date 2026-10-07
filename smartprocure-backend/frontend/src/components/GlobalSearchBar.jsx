import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, X, AlertCircle, RefreshCw, LogIn } from 'lucide-react'
import { searchApi } from '../api/searchApi'
import { authApi } from '../api/authApi'
import { userApi } from '../api/userApi'
import { useAuth } from '../context/AuthContext'

const DEBOUNCE_MS = 300
const API_TIMEOUT_MS = 5000
const MAX_RESULTS_PER_CATEGORY = 5

const ENTITY_ROUTES = {
  requisitions: (id) => `/requisitions/${id}`,
  purchaseOrders: (id) => `/purchase-orders/${id}`,
  items: (id) => `/requisitions/${id}`,
  vendors: (id) => `/vendors/${id}`,
}

const CATEGORY_LABELS = {
  requisitions: 'Requisitions',
  purchaseOrders: 'Purchase Orders',
  items: 'Items',
  vendors: 'Vendors',
}

export default function GlobalSearchBar() {
  const { user, hasRole, startProxySession } = useAuth()
  const navigate = useNavigate()

  const [query, setQuery] = useState('')
  const [results, setResults] = useState(null)
  const [proxyUsers, setProxyUsers] = useState(null)
  const [matchedUsers, setMatchedUsers] = useState([]) // Users found in regular search (for proxy icon)
  const [isOpen, setIsOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [selectedIndex, setSelectedIndex] = useState(-1)

  const inputRef = useRef(null)
  const containerRef = useRef(null)
  const debounceRef = useRef(null)
  const abortRef = useRef(null)

  const isAdmin = hasRole('ROLE_ADMIN')

  // Detect proxy login intent
  const isProxyQuery = useCallback((q) => {
    if (!isAdmin) return false
    const lower = q.toLowerCase()
    return lower.startsWith('login as ') || lower.startsWith('@')
  }, [isAdmin])

  const getProxySearchTerm = useCallback((q) => {
    const lower = q.toLowerCase()
    if (lower.startsWith('login as ')) return q.slice(9).trim()
    if (lower.startsWith('@')) return q.slice(1).trim()
    return ''
  }, [])

  // Build flat list for keyboard navigation
  const getFlatResults = useCallback(() => {
    if (proxyUsers && proxyUsers.length > 0) {
      return proxyUsers.map((u) => ({ type: 'proxy', data: u }))
    }
    if (!results) return []
    const flat = []
    // Include matched users first (for ADMIN)
    if (isAdmin && matchedUsers.length > 0) {
      matchedUsers.forEach((u) => {
        flat.push({ type: 'proxy', data: u })
      })
    }
    for (const category of Object.keys(CATEGORY_LABELS)) {
      const items = results[category] || []
      items.slice(0, MAX_RESULTS_PER_CATEGORY).forEach((item) => {
        flat.push({ type: category, data: item })
      })
    }
    return flat
  }, [results, proxyUsers, matchedUsers, isAdmin])

  // Perform search
  const performSearch = useCallback(async (searchQuery) => {
    if (searchQuery.length < 2) {
      setResults(null)
      setProxyUsers(null)
      setMatchedUsers([])
      setIsOpen(false)
      setError(null)
      return
    }

    // Handle proxy login search for ADMIN
    if (isProxyQuery(searchQuery)) {
      const term = getProxySearchTerm(searchQuery)
      if (term.length < 1) {
        setProxyUsers([])
        setResults(null)
        setIsOpen(true)
        setError(null)
        return
      }

      setIsLoading(true)
      setError(null)
      try {
        const res = await userApi.getAll({ search: term, status: 'ACTIVE' })
        const users = (res.data?.content || res.data || [])
          .filter((u) => {
            const roles = u.roles || []
            return !roles.includes('ADMIN') && !roles.includes('ROLE_ADMIN')
          })
          .slice(0, 10)
        setProxyUsers(users)
        setResults(null)
        setIsOpen(true)
      } catch {
        setError('Unable to load user list')
        setProxyUsers(null)
      } finally {
        setIsLoading(false)
      }
      return
    }

    // Regular search
    setIsLoading(true)
    setError(null)
    setProxyUsers(null)
    setMatchedUsers([])

    // Cancel previous request
    if (abortRef.current) {
      abortRef.current.abort()
    }
    const controller = new AbortController()
    abortRef.current = controller

    try {
      const timeoutId = setTimeout(() => controller.abort(), API_TIMEOUT_MS)

      // Run entity search + user search (for ADMIN) in parallel
      const searchPromises = [searchApi.search(searchQuery)]
      if (isAdmin) {
        searchPromises.push(
          userApi.getAll({ search: searchQuery, status: 'ACTIVE' }).catch(() => ({ data: [] }))
        )
      }

      const responses = await Promise.all(searchPromises)
      clearTimeout(timeoutId)

      if (controller.signal.aborted) return

      setResults(responses[0].data)

      // Process user results for ADMIN — filter out ADMIN users, limit to 5
      if (isAdmin && responses[1]) {
        const users = (responses[1].data?.content || responses[1].data || [])
          .filter((u) => {
            const roles = u.roles || []
            return !roles.includes('ADMIN') && !roles.includes('ROLE_ADMIN')
          })
          .slice(0, 5)
        setMatchedUsers(users)
      }

      setIsOpen(true)
    } catch (err) {
      if (controller.signal.aborted || err?.name === 'CanceledError') {
        setError('Search timed out. Please try again.')
      } else {
        setError('Search temporarily unavailable')
      }
      setResults(null)
      setMatchedUsers([])
      setIsOpen(true)
    } finally {
      setIsLoading(false)
    }
  }, [isProxyQuery, getProxySearchTerm])

  // Debounced input handler
  const handleInputChange = (e) => {
    const value = e.target.value
    setQuery(value)
    setSelectedIndex(-1)

    if (debounceRef.current) {
      clearTimeout(debounceRef.current)
    }

    if (value.length < 2) {
      setResults(null)
      setProxyUsers(null)
      setMatchedUsers([])
      setIsOpen(false)
      setError(null)
      return
    }

    debounceRef.current = setTimeout(() => {
      performSearch(value)
    }, DEBOUNCE_MS)
  }

  // Retry on error
  const handleRetry = () => {
    if (query.length >= 2) {
      performSearch(query)
    }
  }

  // Handle proxy login
  const handleProxySelect = async (targetUser) => {
    try {
      const res = await authApi.proxyLogin(targetUser.id)
      const { proxyToken, ...targetData } = res.data
      startProxySession(proxyToken, targetData)
      setIsOpen(false)
      setQuery('')
      navigate('/')
    } catch (err) {
      // If "nested proxy sessions" error, end existing session and retry
      const message = err?.response?.data?.message || ''
      if (message.toLowerCase().includes('nested') || message.toLowerCase().includes('not permitted')) {
        try {
          await authApi.proxyLogout()
          // Retry after ending the stale session
          const res = await authApi.proxyLogin(targetUser.id)
          const { proxyToken, ...targetData } = res.data
          startProxySession(proxyToken, targetData)
          setIsOpen(false)
          setQuery('')
          navigate('/')
        } catch {
          setError('Proxy login failed')
        }
      } else {
        setError('Proxy login failed')
      }
    }
  }

  // Navigate to result detail
  const handleResultClick = (category, item) => {
    const route = ENTITY_ROUTES[category]
    if (route) {
      navigate(route(item.id))
    }
    setIsOpen(false)
  }

  // Keyboard navigation
  const handleKeyDown = (e) => {
    const flatResults = getFlatResults()

    if (e.key === 'Escape') {
      setIsOpen(false)
      inputRef.current?.blur()
      return
    }

    if (!isOpen || flatResults.length === 0) return

    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex((prev) =>
        prev < flatResults.length - 1 ? prev + 1 : 0
      )
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex((prev) =>
        prev > 0 ? prev - 1 : flatResults.length - 1
      )
    } else if (e.key === 'Enter' && selectedIndex >= 0) {
      e.preventDefault()
      const selected = flatResults[selectedIndex]
      if (selected.type === 'proxy') {
        handleProxySelect(selected.data)
      } else {
        handleResultClick(selected.type, selected.data)
      }
    }
  }

  // Ctrl+K / Cmd+K hotkey
  useEffect(() => {
    const handleHotkey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault()
        inputRef.current?.focus()
        inputRef.current?.select()
      }
    }
    document.addEventListener('keydown', handleHotkey)
    return () => document.removeEventListener('keydown', handleHotkey)
  }, [])

  // Click outside to close dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
      if (abortRef.current) abortRef.current.abort()
    }
  }, [])

  // Render grouped results
  const renderResults = () => {
    if (error) {
      return (
        <div className="p-4 text-center">
          <div className="flex items-center justify-center gap-2 text-red-600 mb-2">
            <AlertCircle size={16} />
            <span className="text-sm">{error}</span>
          </div>
          <button
            onClick={handleRetry}
            className="inline-flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 font-medium"
          >
            <RefreshCw size={14} />
            Retry
          </button>
        </div>
      )
    }

    if (isLoading) {
      return (
        <div className="p-4 text-center text-sm text-gray-500">
          Searching...
        </div>
      )
    }

    // Proxy user list for ADMIN
    if (proxyUsers !== null) {
      if (proxyUsers.length === 0) {
        return (
          <div className="p-4 text-center text-sm text-gray-500">
            No matching users found
          </div>
        )
      }

      let flatIndex = 0
      return (
        <div className="py-2">
          <div className="px-3 py-1 text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Proxy Login As
          </div>
          {proxyUsers.map((u) => {
            const currentIdx = flatIndex++
            return (
              <button
                key={u.id}
                onClick={() => handleProxySelect(u)}
                className={`w-full text-left px-3 py-2 text-sm hover:bg-primary-50 flex items-center gap-2 ${
                  selectedIndex === currentIdx ? 'bg-primary-50' : ''
                }`}
              >
                <span className="font-medium text-gray-900">
                  {u.firstName} {u.lastName}
                </span>
                <span className="text-gray-500">{u.email}</span>
              </button>
            )
          })}
        </div>
      )
    }

    // Regular search results
    if (!results) return null

    const categories = Object.keys(CATEGORY_LABELS)
    const hasAnyResults = categories.some(
      (cat) => results[cat] && results[cat].length > 0
    )

    if (!hasAnyResults && matchedUsers.length === 0) {
      return (
        <div className="p-4 text-center text-sm text-gray-500">
          No results found. Try refining your search query.
        </div>
      )
    }

    let flatIndex = 0
    return (
      <div className="py-2 max-h-96 overflow-y-auto">
        {/* Users section with proxy login icon (ADMIN only) */}
        {isAdmin && matchedUsers.length > 0 && (
          <div className="mb-1">
            <div className="px-3 py-1 text-xs font-semibold text-gray-500 uppercase tracking-wide">
              Users
            </div>
            {matchedUsers.map((u) => {
              const currentIdx = flatIndex++
              return (
                <div
                  key={`user-${u.id}`}
                  className={`w-full flex items-center justify-between px-3 py-2 text-sm hover:bg-primary-50 ${
                    selectedIndex === currentIdx ? 'bg-primary-50' : ''
                  }`}
                >
                  <div className="flex-1 min-w-0">
                    <span className="font-medium text-gray-900">
                      {u.firstName} {u.lastName}
                    </span>
                    <span className="ml-2 text-xs text-gray-500">{u.email}</span>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      handleProxySelect(u)
                    }}
                    className="ml-2 p-1.5 rounded-md text-indigo-600 hover:bg-indigo-100 hover:text-indigo-800 transition-colors"
                    title={`Login as ${u.firstName} ${u.lastName}`}
                    aria-label={`Proxy login as ${u.firstName} ${u.lastName}`}
                  >
                    <LogIn size={15} />
                  </button>
                </div>
              )
            })}
          </div>
        )}

        {categories.map((category) => {
          const items = results[category]
          if (!items || items.length === 0) return null

          const displayed = items.slice(0, MAX_RESULTS_PER_CATEGORY)
          const hasMore = items.length > MAX_RESULTS_PER_CATEGORY

          return (
            <div key={category} className="mb-1">
              <div className="px-3 py-1 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                {CATEGORY_LABELS[category]}
              </div>
              {displayed.map((item) => {
                const currentIdx = flatIndex++
                return (
                  <button
                    key={`${category}-${item.id}`}
                    onClick={() => handleResultClick(category, item)}
                    className={`w-full text-left px-3 py-2 text-sm hover:bg-primary-50 ${
                      selectedIndex === currentIdx ? 'bg-primary-50' : ''
                    }`}
                  >
                    <div className="font-medium text-gray-900">{item.title}</div>
                    {item.subtitle && (
                      <div className="text-xs text-gray-500">{item.subtitle}</div>
                    )}
                  </button>
                )
              })}
              {hasMore && (
                <button
                  onClick={() => {
                    navigate(`/search?q=${encodeURIComponent(query)}&type=${category}`)
                    setIsOpen(false)
                  }}
                  className="w-full text-left px-3 py-1 text-xs text-primary-600 hover:text-primary-700 font-medium"
                >
                  View all {CATEGORY_LABELS[category]}
                </button>
              )}
            </div>
          )
        })}
      </div>
    )
  }

  return (
    <div ref={containerRef} className="relative flex-1 max-w-md mx-4">
      <div className="relative">
        <Search
          size={16}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
        />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onFocus={() => {
            if (query.length >= 2 && (results || proxyUsers || error)) {
              setIsOpen(true)
            }
          }}
          placeholder="Search... (Ctrl+K)"
          className="w-full pl-9 pr-8 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500 bg-gray-50 hover:bg-white transition-colors"
          aria-label="Global search"
          aria-expanded={isOpen}
          role="combobox"
          aria-autocomplete="list"
        />
        {query && (
          <button
            onClick={() => {
              setQuery('')
              setResults(null)
              setProxyUsers(null)
              setMatchedUsers([])
              setIsOpen(false)
              setError(null)
              inputRef.current?.focus()
            }}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
            aria-label="Clear search"
          >
            <X size={14} />
          </button>
        )}
      </div>

      {/* Dropdown */}
      {isOpen && (
        <div
          className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50"
          role="listbox"
        >
          {renderResults()}
        </div>
      )}
    </div>
  )
}
