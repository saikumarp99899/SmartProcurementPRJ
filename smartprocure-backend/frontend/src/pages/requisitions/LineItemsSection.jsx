import { useRef, useEffect, useState, useCallback } from 'react'
import { PlusCircle, Trash2 } from 'lucide-react'
import clsx from 'clsx'
import toast from 'react-hot-toast'
import { aiApi } from '../../api/aiApi'
import VendorRecommendationPanel from './VendorRecommendationPanel'

const MAX_LINE_ITEMS = 50
const DEBOUNCE_MS = 250
const MIN_PREFIX_LENGTH = 2
const MAX_SUGGESTIONS = 10

export default function LineItemsSection({ fields, append, remove, register, errors, watch, setValue }) {
  const [animatingIndex, setAnimatingIndex] = useState(null)
  const [activeVendorIndex, setActiveVendorIndex] = useState(null)
  const itemNameRefs = useRef({})

  // AI autocomplete state per row
  const [suggestions, setSuggestions] = useState({})        // { [idx]: string[] }
  const [showDropdown, setShowDropdown] = useState({})      // { [idx]: boolean }
  const [priceSuggestions, setPriceSuggestions] = useState({}) // { [idx]: { min, max, suggested } | null }
  const debounceTimers = useRef({})

  const handleAddItem = () => {
    if (fields.length >= MAX_LINE_ITEMS) return
    const newIndex = fields.length
    append({ itemName: '', description: '', quantity: 1, unitPrice: '' })
    setAnimatingIndex(newIndex)
  }

  useEffect(() => {
    if (animatingIndex !== null && itemNameRefs.current[animatingIndex]) {
      const timer = setTimeout(() => {
        itemNameRefs.current[animatingIndex]?.focus()
      }, 50)
      const animTimer = setTimeout(() => {
        setAnimatingIndex(null)
      }, 350)
      return () => {
        clearTimeout(timer)
        clearTimeout(animTimer)
      }
    }
  }, [animatingIndex, fields.length])

  // Cleanup debounce timers on unmount
  useEffect(() => {
    return () => {
      Object.values(debounceTimers.current).forEach(clearTimeout)
    }
  }, [])

  // Fetch item suggestions with debounce
  const fetchSuggestions = useCallback((idx, prefix) => {
    // Clear existing timer for this index
    if (debounceTimers.current[idx]) {
      clearTimeout(debounceTimers.current[idx])
    }

    if (!prefix || prefix.length < MIN_PREFIX_LENGTH) {
      setSuggestions(prev => ({ ...prev, [idx]: [] }))
      setShowDropdown(prev => ({ ...prev, [idx]: false }))
      return
    }

    debounceTimers.current[idx] = setTimeout(async () => {
      try {
        const response = await aiApi.getItemSuggestions(prefix)
        const items = (response.data || []).slice(0, MAX_SUGGESTIONS)
        setSuggestions(prev => ({ ...prev, [idx]: items }))
        setShowDropdown(prev => ({ ...prev, [idx]: items.length > 0 }))
      } catch {
        // Non-blocking — allow manual entry
        toast.error('AI suggestions temporarily unavailable', { id: 'ai-suggest-error' })
        setSuggestions(prev => ({ ...prev, [idx]: [] }))
        setShowDropdown(prev => ({ ...prev, [idx]: false }))
      }
    }, DEBOUNCE_MS)
  }, [])

  // Fetch price suggestion for an item
  const fetchPriceSuggestion = useCallback(async (idx, itemName) => {
    if (!itemName || itemName.trim().length === 0) return

    try {
      const response = await aiApi.getPriceSuggestion(itemName.trim())
      const data = response.data

      if (data && !data.insufficientData) {
        // Auto-populate unit price with suggested price
        setValue(`items.${idx}.unitPrice`, data.suggestedPrice, { shouldValidate: true })
        setPriceSuggestions(prev => ({
          ...prev,
          [idx]: { min: data.minPrice, max: data.maxPrice, suggested: data.suggestedPrice }
        }))
      } else {
        // Insufficient data — no price populated, no error
        setPriceSuggestions(prev => ({ ...prev, [idx]: null }))
      }
    } catch {
      // Non-blocking — allow manual entry
      toast.error('AI suggestions temporarily unavailable', { id: 'ai-price-error' })
      setPriceSuggestions(prev => ({ ...prev, [idx]: null }))
    }
  }, [setValue])

  // Handle item name input change for autocomplete
  const handleItemNameChange = useCallback((idx, value) => {
    fetchSuggestions(idx, value)
  }, [fetchSuggestions])

  // Handle selecting a suggestion from the dropdown
  const handleSelectSuggestion = useCallback((idx, suggestion) => {
    setValue(`items.${idx}.itemName`, suggestion, { shouldValidate: true })
    setShowDropdown(prev => ({ ...prev, [idx]: false }))
    setSuggestions(prev => ({ ...prev, [idx]: [] }))
    // Trigger price suggestion on selection
    fetchPriceSuggestion(idx, suggestion)
  }, [setValue, fetchPriceSuggestion])

  // Handle item name blur — trigger price suggestion if user typed manually
  const handleItemNameBlur = useCallback((idx, value) => {
    // Close dropdown after a small delay (allow click on suggestion)
    setTimeout(() => {
      setShowDropdown(prev => ({ ...prev, [idx]: false }))
    }, 200)

    // Fetch price suggestion if value has content
    if (value && value.trim().length > 0) {
      fetchPriceSuggestion(idx, value)
    }
  }, [fetchPriceSuggestion])

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-gray-900">
          Line Items
          <span className="ml-2 text-sm font-normal text-gray-500">
            ({fields.length}/{MAX_LINE_ITEMS})
          </span>
        </h2>
        <button
          type="button"
          onClick={handleAddItem}
          disabled={fields.length >= MAX_LINE_ITEMS}
          className={clsx(
            'text-sm font-medium flex items-center gap-1 transition-colors duration-200',
            fields.length >= MAX_LINE_ITEMS
              ? 'text-gray-400 cursor-not-allowed'
              : 'text-primary-600 hover:text-primary-700'
          )}
          aria-label="Add line item"
        >
          <PlusCircle size={16} /> Add Item
        </button>
      </div>

      {fields.length >= MAX_LINE_ITEMS && (
        <p className="text-sm text-amber-600 mb-3">
          Maximum of {MAX_LINE_ITEMS} line items reached.
        </p>
      )}

      <div className="space-y-3">
        {fields.map((field, idx) => {
          const itemErrors = errors.items?.[idx]
          const quantity = watch(`items.${idx}.quantity`)
          const unitPrice = watch(`items.${idx}.unitPrice`)
          const lineTotal = (Number(quantity) || 0) * (Number(unitPrice) || 0)
          const priceInfo = priceSuggestions[idx]

          return (
            <div
              key={field.id}
              className={clsx(
                'bg-gray-50 p-4 rounded-lg border border-gray-200 transition-all',
                animatingIndex === idx
                  ? 'animate-fade-in opacity-0'
                  : 'opacity-100'
              )}
              style={
                animatingIndex === idx
                  ? { animation: 'fadeIn 300ms ease-out forwards' }
                  : undefined
              }
            >
              {/* Desktop/Tablet layout */}
              <div className="grid grid-cols-1 sm:grid-cols-12 gap-3 items-start">
                {/* Item Name with Autocomplete */}
                <div className="sm:col-span-4 relative">
                  <label className="block text-xs font-medium text-gray-500 mb-1">
                    Item Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    {...(() => {
                      const { ref, onChange, ...rest } = register(`items.${idx}.itemName`)
                      return {
                        ...rest,
                        ref: (el) => {
                          ref(el)
                          itemNameRefs.current[idx] = el
                        },
                        onChange: (e) => {
                          onChange(e)
                          handleItemNameChange(idx, e.target.value)
                        },
                        onFocus: () => {
                          setActiveVendorIndex(idx)
                        },
                        onBlur: (e) => {
                          handleItemNameBlur(idx, e.target.value)
                          // Delay clearing active vendor index so panel clicks register
                          setTimeout(() => {
                            setActiveVendorIndex((current) => current === idx ? null : current)
                          }, 300)
                        },
                      }
                    })()}
                    autoComplete="off"
                    className={clsx(
                      'input-field text-sm',
                      itemErrors?.itemName && 'border-red-500 focus:ring-red-500 focus:border-red-500'
                    )}
                    placeholder="Enter item name"
                  />
                  {itemErrors?.itemName && (
                    <p className="mt-1 text-xs text-red-600" role="alert">
                      {itemErrors.itemName.message}
                    </p>
                  )}

                  {/* Autocomplete dropdown */}
                  {showDropdown[idx] && suggestions[idx]?.length > 0 && (
                    <ul
                      className="absolute z-20 top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-y-auto"
                      role="listbox"
                      aria-label="Item name suggestions"
                    >
                      {suggestions[idx].map((suggestion, sIdx) => (
                        <li
                          key={sIdx}
                          role="option"
                          className="px-3 py-2 text-sm text-gray-700 hover:bg-primary-50 hover:text-primary-700 cursor-pointer transition-colors"
                          onMouseDown={(e) => {
                            e.preventDefault()
                            handleSelectSuggestion(idx, suggestion)
                          }}
                        >
                          {suggestion}
                        </li>
                      ))}
                    </ul>
                  )}

                  {/* Vendor Recommendation Panel */}
                  {activeVendorIndex === idx && (
                    <VendorRecommendationPanel
                      itemName={watch(`items.${idx}.itemName`)}
                      onSelect={(vendorName) => {
                        setValue(`items.${idx}.vendor`, vendorName, { shouldDirty: true })
                      }}
                    />
                  )}
                </div>

                {/* Description */}
                <div className="sm:col-span-3">
                  <label className="block text-xs font-medium text-gray-500 mb-1">Description</label>
                  <input
                    type="text"
                    {...register(`items.${idx}.description`)}
                    className="input-field text-sm"
                    placeholder="Optional description"
                  />
                </div>

                {/* Quantity */}
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-gray-500 mb-1">
                    Qty <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    min="1"
                    {...register(`items.${idx}.quantity`, { valueAsNumber: true })}
                    className={clsx(
                      'input-field text-sm',
                      itemErrors?.quantity && 'border-red-500 focus:ring-red-500 focus:border-red-500'
                    )}
                  />
                  {itemErrors?.quantity && (
                    <p className="mt-1 text-xs text-red-600" role="alert">
                      {itemErrors.quantity.message}
                    </p>
                  )}
                </div>

                {/* Unit Price with Price Range Helper */}
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-gray-500 mb-1">
                    Unit Price <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    {...register(`items.${idx}.unitPrice`, { valueAsNumber: true })}
                    className={clsx(
                      'input-field text-sm',
                      itemErrors?.unitPrice && 'border-red-500 focus:ring-red-500 focus:border-red-500'
                    )}
                  />
                  {itemErrors?.unitPrice && (
                    <p className="mt-1 text-xs text-red-600" role="alert">
                      {itemErrors.unitPrice.message}
                    </p>
                  )}
                  {priceInfo && (
                    <p className="mt-1 text-xs text-gray-500">
                      Range: ₹{Number(priceInfo.min).toLocaleString()} – ₹{Number(priceInfo.max).toLocaleString()}
                    </p>
                  )}
                </div>

                {/* Line Total + Remove */}
                <div className="sm:col-span-1 flex sm:flex-col items-center justify-between sm:justify-start gap-2 pt-5">
                  <span className="text-sm font-medium text-gray-700 whitespace-nowrap">
                    ₹{lineTotal.toLocaleString()}
                  </span>
                  {fields.length > 1 && (
                    <button
                      type="button"
                      onClick={() => remove(idx)}
                      className="text-red-500 hover:text-red-700 p-1 transition-colors"
                      aria-label={`Remove item ${idx + 1}`}
                    >
                      <Trash2 size={16} />
                    </button>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
