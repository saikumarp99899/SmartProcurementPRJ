import { Check } from 'lucide-react'
import clsx from 'clsx'

const steps = [
  { id: 1, label: 'Header' },
  { id: 2, label: 'Items' },
  { id: 3, label: 'Review' },
]

export default function RequisitionStepper({ currentStep, completedSteps }) {
  return (
    <nav aria-label="Requisition form progress" className="mb-6">
      <ol className="flex items-center justify-center gap-2 sm:gap-4">
        {steps.map((step, idx) => {
          const isCompleted = completedSteps.includes(step.id)
          const isCurrent = currentStep === step.id

          return (
            <li key={step.id} className="flex items-center gap-2 sm:gap-3">
              {/* Step indicator */}
              <div className="flex items-center gap-2">
                <div
                  className={clsx(
                    'flex items-center justify-center w-8 h-8 rounded-full text-sm font-semibold border-2 transition-colors duration-200',
                    isCompleted && 'bg-green-500 border-green-500 text-white',
                    isCurrent && !isCompleted && 'bg-primary-600 border-primary-600 text-white',
                    !isCurrent && !isCompleted && 'bg-white border-gray-300 text-gray-500'
                  )}
                  aria-current={isCurrent ? 'step' : undefined}
                >
                  {isCompleted ? <Check size={16} /> : step.id}
                </div>
                <span
                  className={clsx(
                    'text-sm font-medium hidden sm:inline',
                    isCurrent ? 'text-primary-700' : isCompleted ? 'text-green-700' : 'text-gray-500'
                  )}
                >
                  {step.label}
                </span>
              </div>

              {/* Connector line */}
              {idx < steps.length - 1 && (
                <div
                  className={clsx(
                    'w-8 sm:w-12 h-0.5',
                    isCompleted ? 'bg-green-500' : 'bg-gray-300'
                  )}
                />
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
