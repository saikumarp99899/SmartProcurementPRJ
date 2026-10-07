import { FileEdit, Clock, CheckCircle2, ShoppingCart } from 'lucide-react'

const WORKFLOW_STAGES = [
  { key: 'DRAFT', label: 'Draft', icon: FileEdit },
  { key: 'SUBMITTED', label: 'Submitted', icon: Clock },
  { key: 'APPROVED', label: 'Approved', icon: CheckCircle2 },
  { key: 'PO_CREATED', label: 'PO Created', icon: ShoppingCart },
  { key: 'COMPLETED', label: 'Completed', icon: CheckCircle2 },
]

/**
 * Horizontal workflow progress tracker.
 * - Completed stages: green fill
 * - Current stage: distinct bg color + ring indicator
 * - Future stages: gray/muted
 */
export default function WorkflowTracker({ status }) {
  const currentIndex = WORKFLOW_STAGES.findIndex(s => s.key === status)

  return (
    <div className="w-full px-2 py-4">
      <div className="flex items-center justify-between">
        {WORKFLOW_STAGES.map((stage, index) => {
          const isDone = index < currentIndex
          const isCurrent = index === currentIndex
          const Icon = stage.icon

          return (
            <div key={stage.key} className="flex items-center flex-1 last:flex-none">
              <div className="flex flex-col items-center gap-1.5">
                <div
                  className={`w-10 h-10 rounded-full flex items-center justify-center transition-all ${
                    isDone
                      ? 'bg-green-500 text-white'
                      : isCurrent
                      ? 'bg-indigo-600 text-white ring-4 ring-indigo-200'
                      : 'bg-gray-200 text-gray-400'
                  }`}
                  aria-label={`${stage.label}${isDone ? ' (completed)' : isCurrent ? ' (current)' : ''}`}
                >
                  <Icon size={18} />
                </div>
                <span
                  className={`text-xs text-center whitespace-nowrap ${
                    isCurrent
                      ? 'font-semibold text-indigo-700'
                      : isDone
                      ? 'font-medium text-green-700'
                      : 'text-gray-500'
                  }`}
                >
                  {stage.label}
                </span>
              </div>
              {index < WORKFLOW_STAGES.length - 1 && (
                <div
                  className={`flex-1 h-0.5 mx-3 mb-6 rounded ${
                    isDone ? 'bg-green-500' : 'bg-gray-200'
                  }`}
                />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
