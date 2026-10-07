import { CheckCircle2, XCircle, Clock } from 'lucide-react'

const actionConfig = {
  APPROVED: { icon: CheckCircle2, color: 'text-green-500', bg: 'bg-green-100', label: 'Approved' },
  REJECTED: { icon: XCircle, color: 'text-red-500', bg: 'bg-red-100', label: 'Rejected' },
  PENDING: { icon: Clock, color: 'text-yellow-500', bg: 'bg-yellow-100', label: 'Pending' },
}

/**
 * Vertical timeline of approval actions.
 * Shows approver name, action, timestamp, and comments.
 * If no approval actions exist, displays a "not yet started" message.
 */
export default function ApprovalTimeline({ approvals }) {
  if (!approvals || approvals.length === 0) {
    return (
      <div className="p-6 text-center border border-dashed border-gray-300 rounded-lg">
        <Clock size={24} className="mx-auto text-gray-400 mb-2" />
        <p className="text-sm text-gray-500">
          The approval process has not yet started.
        </p>
      </div>
    )
  }

  return (
    <div className="relative pl-6">
      {/* Vertical line */}
      <div className="absolute left-3 top-2 bottom-2 w-0.5 bg-gray-200" />

      <div className="space-y-4">
        {approvals.map((approval, index) => {
          const config = actionConfig[approval.status] || actionConfig.PENDING
          const Icon = config.icon

          return (
            <div key={approval.id || index} className="relative flex gap-3">
              {/* Icon dot */}
              <div
                className={`absolute -left-3 w-6 h-6 rounded-full flex items-center justify-center ${config.bg}`}
              >
                <Icon size={14} className={config.color} />
              </div>

              {/* Content */}
              <div className="ml-6 flex-1 pb-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium text-gray-900">
                    {approval.approverName || approval.assignedApproverName || 'Pending Approver'}
                  </span>
                  <span
                    className={`px-2 py-0.5 rounded text-xs font-medium ${config.bg} ${config.color}`}
                  >
                    {config.label}
                  </span>
                  {approval.approvalLevel && (
                    <span className="text-xs text-gray-400">Level {approval.approvalLevel}</span>
                  )}
                </div>
                {approval.approvedAt && (
                  <p className="text-xs text-gray-500 mt-0.5">
                    {new Date(approval.approvedAt).toLocaleString()}
                  </p>
                )}
                {approval.comments && (
                  <p className="text-sm text-gray-600 mt-1 italic">"{approval.comments}"</p>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
