import clsx from 'clsx'

export default function RequisitionSummary({ totalAmount, itemCount, isDirty }) {
  const status = isDirty ? 'Unsaved Changes' : 'Draft'

  return (
    <div
      className={clsx(
        'card',
        // Mobile: fixed bottom bar
        'fixed bottom-0 left-0 right-0 z-40',
        'lg:static lg:z-auto',
        // Desktop/tablet: sticky at top
        'lg:sticky lg:top-6',
        'shadow-lg lg:shadow-sm'
      )}
    >
      <div className="flex items-center justify-between lg:flex-col lg:items-start lg:gap-4">
        {/* Summary details */}
        <div className="flex items-center gap-4 lg:flex-col lg:items-start lg:gap-3 lg:w-full">
          {/* Total Amount */}
          <div>
            <p className="text-xs text-gray-500 uppercase tracking-wide">Total Amount</p>
            <p className="text-xl lg:text-2xl font-bold text-gray-900">
              ₹{totalAmount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </p>
          </div>

          {/* Item Count */}
          <div className="lg:w-full lg:pt-3 lg:border-t lg:border-gray-200">
            <p className="text-xs text-gray-500 uppercase tracking-wide">Items</p>
            <p className="text-lg font-semibold text-gray-900">{itemCount}</p>
          </div>

          {/* Status */}
          <div className="lg:w-full lg:pt-3 lg:border-t lg:border-gray-200">
            <p className="text-xs text-gray-500 uppercase tracking-wide">Status</p>
            <span
              className={clsx(
                'inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full',
                isDirty
                  ? 'bg-amber-100 text-amber-700'
                  : 'bg-gray-100 text-gray-600'
              )}
            >
              {status}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
