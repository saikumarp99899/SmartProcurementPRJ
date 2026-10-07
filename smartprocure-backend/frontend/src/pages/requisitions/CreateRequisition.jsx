import { useState, useCallback, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm, useFieldArray } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { requisitionApi } from '../../api/requisitionApi'
import toast from 'react-hot-toast'
import { ArrowLeft, Save } from 'lucide-react'

import RequisitionStepper from './RequisitionStepper'
import RequisitionHeader from './RequisitionHeader'
import LineItemsSection from './LineItemsSection'
import RequisitionSummary from './RequisitionSummary'

// --- Zod validation schema ---
const lineItemSchema = z.object({
  itemName: z.string().min(1, 'Item name is required'),
  description: z.string().optional().default(''),
  quantity: z.coerce.number().int().min(1, 'Quantity must be at least 1'),
  unitPrice: z.coerce.number().min(0.01, 'Unit price must be greater than 0'),
})

const requisitionSchema = z.object({
  departmentId: z.string().min(1, 'Department is required'),
  costCenterId: z.string().min(1, 'Cost center is required'),
  description: z.string().min(1, 'Description is required'),
  items: z.array(lineItemSchema).min(1, 'At least one line item is required').max(50, 'Maximum 50 items allowed'),
})

// --- Helpers ---
const DRAFT_STORAGE_KEY = 'requisition_draft'
const MAX_DRAFT_SIZE_BYTES = 1 * 1024 * 1024 // 1 MB
const AUTO_SAVE_INTERVAL_MS = 30000 // 30 seconds

function getStepCompletionStatus(formValues, errors) {
  const completedSteps = []

  // Step 1: Header — department, costCenter, description all valid
  const headerValid =
    formValues.departmentId &&
    formValues.costCenterId &&
    formValues.description &&
    !errors.departmentId &&
    !errors.costCenterId &&
    !errors.description

  if (headerValid) completedSteps.push(1)

  // Step 2: Items — at least one item with all required fields valid
  const itemsValid =
    formValues.items?.length > 0 &&
    formValues.items.every(
      item => item.itemName && item.quantity >= 1 && item.unitPrice > 0
    ) &&
    !errors.items

  if (itemsValid) completedSteps.push(2)

  // Step 3: Review — all valid (both header + items)
  if (headerValid && itemsValid) completedSteps.push(3)

  return completedSteps
}

function calculateTotal(items) {
  return items.reduce((sum, item) => {
    return sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)
  }, 0)
}

export default function CreateRequisition() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [currentStep, setCurrentStep] = useState(1)
  const [showCancelDialog, setShowCancelDialog] = useState(false)
  const formRef = useRef(null)

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isDirty },
  } = useForm({
    resolver: zodResolver(requisitionSchema),
    defaultValues: {
      departmentId: '',
      costCenterId: '',
      description: '',
      items: [{ itemName: '', description: '', quantity: 1, unitPrice: '' }],
    },
    mode: 'onBlur', // Inline validation on blur
  })

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'items',
  })

  const watchedValues = watch()
  const completedSteps = getStepCompletionStatus(watchedValues, errors)
  const totalAmount = calculateTotal(watchedValues.items || [])
  const itemCount = fields.length

  // Auto-save state
  const [draftSavedAt, setDraftSavedAt] = useState(null)
  const [autoSaveWarning, setAutoSaveWarning] = useState(null)
  const draftRestoredRef = useRef(false)

  // Restore draft from localStorage on mount
  useEffect(() => {
    if (draftRestoredRef.current) return
    draftRestoredRef.current = true

    try {
      const savedDraft = localStorage.getItem(DRAFT_STORAGE_KEY)
      if (savedDraft) {
        const parsed = JSON.parse(savedDraft)
        if (parsed && parsed.data) {
          reset(parsed.data, { keepDefaultValues: true })
          setDraftSavedAt(parsed.savedAt ? new Date(parsed.savedAt) : null)
          toast.success('Draft restored from previous session')
        }
      }
    } catch {
      // If parsing fails, remove corrupted draft
      localStorage.removeItem(DRAFT_STORAGE_KEY)
    }
  }, [reset])

  // Auto-save to localStorage every 30 seconds while form has unsaved changes
  useEffect(() => {
    const interval = setInterval(() => {
      if (!isDirty) return

      const formData = watchedValues
      const draft = {
        data: formData,
        savedAt: new Date().toISOString(),
      }

      try {
        const serialized = JSON.stringify(draft)

        // Check size limit (1 MB)
        if (new Blob([serialized]).size > MAX_DRAFT_SIZE_BYTES) {
          setAutoSaveWarning('Draft exceeds 1 MB limit — auto-save skipped.')
          return
        }

        localStorage.setItem(DRAFT_STORAGE_KEY, serialized)
        setDraftSavedAt(new Date())
        setAutoSaveWarning(null)
      } catch (err) {
        // Handle QuotaExceededError
        if (err?.name === 'QuotaExceededError' || err?.code === 22) {
          setAutoSaveWarning('Storage quota exceeded — auto-save is unavailable.')
        }
      }
    }, AUTO_SAVE_INTERVAL_MS)

    return () => clearInterval(interval)
  }, [isDirty, watchedValues])

  // Helper to clear draft from localStorage
  const clearDraft = useCallback(() => {
    localStorage.removeItem(DRAFT_STORAGE_KEY)
    setDraftSavedAt(null)
    setAutoSaveWarning(null)
  }, [])

  // Determine current step based on user interaction
  useEffect(() => {
    if (watchedValues.items?.some(item => item.itemName || item.unitPrice)) {
      setCurrentStep(2)
    }
    if (completedSteps.includes(1) && completedSteps.includes(2)) {
      setCurrentStep(3)
    }
  }, [completedSteps, watchedValues.items])

  // Handle form submission
  const onSubmit = async (data) => {
    setLoading(true)
    try {
      const payload = {
        description: data.description,
        departmentId: Number(data.departmentId),
        costCenterId: Number(data.costCenterId),
        items: data.items.map(item => ({
          itemName: item.itemName,
          description: item.description || '',
          quantity: Number(item.quantity),
          unitPrice: Number(item.unitPrice),
        })),
      }
      await requisitionApi.create(payload)
      clearDraft()
      toast.success('Requisition created successfully!')
      navigate('/requisitions/my')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create requisition')
    } finally {
      setLoading(false)
    }
  }

  // On submit with errors — scroll to first invalid field
  const onError = (formErrors) => {
    // Find the first error field and scroll to it
    const firstErrorKey = Object.keys(formErrors)[0]
    let selector = null

    if (firstErrorKey === 'items' && formErrors.items) {
      // Find first item with error
      const itemIdx = formErrors.items.findIndex(item => item)
      if (itemIdx !== -1) {
        const itemErrorKey = Object.keys(formErrors.items[itemIdx])[0]
        selector = `[name="items.${itemIdx}.${itemErrorKey}"]`
      }
    } else {
      selector = `[name="${firstErrorKey}"]`
    }

    if (selector) {
      const element = document.querySelector(selector)
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'center' })
        element.classList.add('border-red-500')
        element.focus()
        // Remove highlight after user interaction
        const cleanup = () => {
          element.classList.remove('border-red-500')
          element.removeEventListener('focus', cleanup)
        }
        element.addEventListener('blur', cleanup)
      }
    }
  }

  // Cancel with unsaved changes confirmation
  const handleCancel = useCallback(() => {
    if (isDirty) {
      setShowCancelDialog(true)
    } else {
      navigate('/requisitions/my')
    }
  }, [isDirty, navigate])

  const confirmCancel = () => {
    clearDraft()
    setShowCancelDialog(false)
    navigate('/requisitions/my')
  }

  return (
    <div className="max-w-7xl mx-auto pb-24 lg:pb-6">
      {/* Back button */}
      <button
        onClick={handleCancel}
        className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4 transition-colors"
      >
        <ArrowLeft size={18} /> Back
      </button>

      {/* Page Title */}
      <h1 className="text-xl font-bold text-gray-900 mb-4">Create New Requisition</h1>

      {/* Auto-save warning banner */}
      {autoSaveWarning && (
        <div className="mb-4 p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2 text-sm text-amber-700">
          <span className="shrink-0">⚠️</span>
          <span>{autoSaveWarning}</span>
          <button
            type="button"
            onClick={() => setAutoSaveWarning(null)}
            className="ml-auto text-amber-500 hover:text-amber-700 font-medium"
            aria-label="Dismiss warning"
          >
            ✕
          </button>
        </div>
      )}

      {/* Draft saved indicator */}
      {draftSavedAt && !autoSaveWarning && (
        <div className="mb-4 flex items-center gap-1.5 text-xs text-gray-500">
          <Save size={12} />
          <span>Draft saved {draftSavedAt.toLocaleTimeString()}</span>
        </div>
      )}

      {/* Stepper */}
      <RequisitionStepper currentStep={currentStep} completedSteps={completedSteps} />

      {/* Main layout — responsive */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* Form area */}
        <div className="flex-1 min-w-0">
          <form
            ref={formRef}
            onSubmit={handleSubmit(onSubmit, onError)}
            className="space-y-6"
            noValidate
          >
            {/* Section 1: Header */}
            <RequisitionHeader
              register={register}
              errors={errors}
              watch={watch}
              setValue={setValue}
            />

            {/* Section 2: Line Items */}
            <LineItemsSection
              fields={fields}
              append={append}
              remove={remove}
              register={register}
              errors={errors}
              watch={watch}
              setValue={setValue}
            />

            {/* Section 3: Review / Actions */}
            <div className="card">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Review & Submit</h2>

              {/* Quick summary */}
              <div className="bg-gray-50 rounded-lg p-4 mb-4">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-center">
                  <div>
                    <p className="text-xs text-gray-500">Department</p>
                    <p className="text-sm font-medium text-gray-800">
                      {watchedValues.departmentId ? 'Selected' : '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Cost Center</p>
                    <p className="text-sm font-medium text-gray-800">
                      {watchedValues.costCenterId ? 'Selected' : '—'}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Items</p>
                    <p className="text-sm font-medium text-gray-800">{itemCount}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-500">Total</p>
                    <p className="text-sm font-bold text-gray-900">
                      ₹{totalAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </p>
                  </div>
                </div>
              </div>

              {/* Action buttons */}
              <div className="flex flex-col sm:flex-row justify-end gap-3">
                <button
                  type="button"
                  onClick={handleCancel}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="btn-primary"
                >
                  {loading ? 'Creating...' : 'Create Requisition'}
                </button>
              </div>
            </div>
          </form>
        </div>

        {/* Summary panel — side on desktop, bottom fixed on mobile */}
        <div className="hidden lg:block lg:w-72 shrink-0">
          <RequisitionSummary
            totalAmount={totalAmount}
            itemCount={itemCount}
            isDirty={isDirty}
          />
        </div>

        {/* Mobile/tablet sticky bottom summary */}
        <div className="lg:hidden">
          <RequisitionSummary
            totalAmount={totalAmount}
            itemCount={itemCount}
            isDirty={isDirty}
          />
        </div>
      </div>

      {/* Cancel Confirmation Dialog */}
      {showCancelDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          role="dialog"
          aria-modal="true"
          aria-labelledby="cancel-dialog-title"
        >
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
            <h3 id="cancel-dialog-title" className="text-lg font-semibold text-gray-900 mb-2">
              Discard Changes?
            </h3>
            <p className="text-sm text-gray-600 mb-6">
              You have unsaved changes. Are you sure you want to leave? Your changes will be lost.
            </p>
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowCancelDialog(false)}
                className="btn-secondary"
              >
                Stay on Form
              </button>
              <button
                type="button"
                onClick={confirmCancel}
                className="btn-danger"
              >
                Discard Changes
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
