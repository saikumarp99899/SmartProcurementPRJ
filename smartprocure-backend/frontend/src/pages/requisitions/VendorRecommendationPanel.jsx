import { useState, useEffect, useRef } from 'react'
import { aiApi } from '../../api/aiApi'
import { Sparkles, AlertCircle, CheckCircle2, Loader2 } from 'lucide-react'

/**
 * VendorRecommendationPanel
 *
 * Displays AI-powered vendor recommendations for a given item name.
 * Triggers API call when itemName has >= 3 characters.
 * Shows up to 5 vendor cards with name, avg price, delivery %, confidence score, and reasoning.
 *
 * Props:
 *  - itemName: string — current item name input value
 *  - onSelect: (vendorName: string) => void — called when a vendor card is selected
 */
export default function VendorRecommendationPanel({ itemName, onSelect }) {
  const [recommendations, setRecommendations] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)
  const [hasSearched, setHasSearched] = useState(false)
  const debounceRef = useRef(null)
  const abortControllerRef = useRef(null)

  useEffect(() => {
    // Clear previous debounce timer
    if (debounceRef.current) {
      clearTimeout(debounceRef.current)
    }

    // Reset if itemName is too short
    if (!itemName || itemName.trim().length < 3) {
      setRecommendations([])
      setError(false)
      setHasSearched(false)
      setLoading(false)
      return
    }

    // Debounce the API call by 500ms
    debounceRef.current = setTimeout(() => {
      fetchRecommendations(itemName.trim())
    }, 500)

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }
    }
  }, [itemName])

  const fetchRecommendations = async (name) => {
    // Cancel any in-flight request
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
    }

    const controller = new AbortController()
    abortControllerRef.current = controller

    setLoading(true)
    setError(false)

    try {
      const response = await aiApi.getVendorRecommendations(name)
      // Only update if this request wasn't aborted
      if (!controller.signal.aborted) {
        const data = response.data || []
        setRecommendations(data.slice(0, 5))
        setHasSearched(true)
        setLoading(false)
      }
    } catch (err) {
      if (!controller.signal.aborted) {
        setError(true)
        setRecommendations([])
        setHasSearched(true)
        setLoading(false)
      }
    }
  }

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
      }
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }
    }
  }, [])

  // Don't render anything if itemName is too short and we haven't searched
  if (!itemName || itemName.trim().length < 3) {
    return null
  }

  return (
    <div className="mt-2 bg-white border border-indigo-100 rounded-lg shadow-sm p-3">
      {/* Header */}
      <div className="flex items-center gap-2 mb-2">
        <Sparkles size={14} className="text-indigo-500" />
        <span className="text-xs font-medium text-indigo-700">AI Vendor Recommendations</span>
      </div>

      {/* Loading state */}
      {loading && (
        <div className="flex items-center gap-2 py-3 justify-center">
          <Loader2 size={16} className="animate-spin text-indigo-500" />
          <span className="text-xs text-gray-500">Finding best vendors...</span>
        </div>
      )}

      {/* Error state — non-blocking */}
      {!loading && error && (
        <div className="flex items-center gap-2 py-2 px-3 bg-amber-50 rounded text-amber-700">
          <AlertCircle size={14} />
          <span className="text-xs">Temporarily unavailable</span>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && hasSearched && recommendations.length === 0 && (
        <p className="text-xs text-gray-500 py-2 text-center">
          No recommendations available
        </p>
      )}

      {/* Recommendation cards */}
      {!loading && !error && recommendations.length > 0 && (
        <div className="space-y-2">
          {recommendations.map((vendor) => (
            <div
              key={vendor.vendorId}
              className="border border-gray-200 rounded-lg p-3 hover:border-indigo-300 hover:bg-indigo-50/30 transition-colors"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-900 truncate">
                      {vendor.vendorName}
                    </span>
                    <span className="shrink-0 inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-indigo-100 text-indigo-700">
                      {vendor.confidenceScore}%
                    </span>
                  </div>

                  <div className="flex flex-wrap items-center gap-3 mt-1 text-xs text-gray-600">
                    <span>
                      Avg Price: <span className="font-medium">₹{Number(vendor.averagePrice).toLocaleString()}</span>
                    </span>
                    <span>
                      Delivery: <span className="font-medium">{vendor.deliveryPerformance}%</span>
                    </span>
                  </div>

                  {vendor.reasoning && (
                    <p className="mt-1 text-xs text-gray-500 line-clamp-2">
                      {vendor.reasoning}
                    </p>
                  )}
                </div>

                <button
                  type="button"
                  onClick={() => onSelect(vendor.vendorName)}
                  className="shrink-0 inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-md hover:bg-indigo-100 transition-colors"
                  aria-label={`Select vendor ${vendor.vendorName}`}
                >
                  <CheckCircle2 size={12} />
                  Select
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
