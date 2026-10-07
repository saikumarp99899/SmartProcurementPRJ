import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { approvalApi } from '../../api/approvalApi'
import { requisitionApi } from '../../api/requisitionApi'
import toast from 'react-hot-toast'
import { CheckCircle, XCircle, ChevronDown, ChevronUp, Package, Building2, Wallet, User, Calendar } from 'lucide-react'

export default function PendingApprovals() {
  const [page, setPage] = useState(0)
  const [commentModal, setCommentModal] = useState(null)
  const [comments, setComments] = useState('')
  const [expandedId, setExpandedId] = useState(null)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['approvals', 'pending', page],
    queryFn: () => approvalApi.getPending({ page, size: 10 }).then(res => res.data),
  })

  const approveMutation = useMutation({
    mutationFn: ({ id, comments }) => approvalApi.approve(id, { comments }),
    onSuccess: () => {
      toast.success('Requisition approved!')
      queryClient.invalidateQueries(['approvals'])
      setCommentModal(null)
      setComments('')
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Approval failed'),
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, comments }) => approvalApi.reject(id, { comments }),
    onSuccess: () => {
      toast.success('Requisition rejected')
      queryClient.invalidateQueries(['approvals'])
      setCommentModal(null)
      setComments('')
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Rejection failed'),
  })

  const handleAction = () => {
    if (!commentModal) return
    if (commentModal.action === 'reject' && !comments.trim()) {
      toast.error('Comments are required for rejection')
      return
    }
    if (commentModal.action === 'approve') {
      approveMutation.mutate({ id: commentModal.id, comments })
    } else {
      rejectMutation.mutate({ id: commentModal.id, comments })
    }
  }

  const toggleExpand = (approvalId) => {
    setExpandedId(prev => prev === approvalId ? null : approvalId)
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600" />
      </div>
    )
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Pending Approvals</h1>

      {data?.data?.length === 0 ? (
        <div className="card text-center py-16">
          <CheckCircle size={48} className="mx-auto text-green-300 mb-4" />
          <p className="text-gray-600 font-medium">All caught up!</p>
          <p className="text-sm text-gray-400 mt-1">No pending approvals at this time.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {data?.data?.map((approval) => (
            <ApprovalCard
              key={approval.id}
              approval={approval}
              isExpanded={expandedId === approval.id}
              onToggle={() => toggleExpand(approval.id)}
              onApprove={() => setCommentModal({ id: approval.id, action: 'approve' })}
              onReject={() => setCommentModal({ id: approval.id, action: 'reject' })}
            />
          ))}
        </div>
      )}

      {/* Pagination */}
      {data?.totalPages > 1 && (
        <div className="flex justify-between items-center mt-6">
          <p className="text-sm text-gray-500">Page {page + 1} of {data?.totalPages}</p>
          <div className="flex gap-2">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="btn-secondary text-sm">Previous</button>
            <button onClick={() => setPage(p => p + 1)} disabled={data?.last} className="btn-secondary text-sm">Next</button>
          </div>
        </div>
      )}

      {/* Comment Modal */}
      {commentModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4 shadow-2xl">
            <h3 className="text-lg font-semibold mb-4">
              {commentModal.action === 'approve' ? 'Approve Requisition' : 'Reject Requisition'}
            </h3>
            <textarea
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              className="input-field"
              rows={4}
              placeholder={commentModal.action === 'reject' ? 'Reason for rejection (required)...' : 'Comments (optional)...'}
            />
            <div className="flex justify-end gap-3 mt-4">
              <button onClick={() => { setCommentModal(null); setComments('') }} className="btn-secondary">Cancel</button>
              <button
                onClick={handleAction}
                className={commentModal.action === 'approve' ? 'btn-success' : 'btn-danger'}
                disabled={approveMutation.isPending || rejectMutation.isPending}
              >
                {commentModal.action === 'approve' ? 'Confirm Approve' : 'Confirm Reject'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * Expandable approval card showing requisition detail when expanded.
 */
function ApprovalCard({ approval, isExpanded, onToggle, onApprove, onReject }) {
  // Fetch full requisition detail when expanded
  const { data: requisition, isLoading: reqLoading } = useQuery({
    queryKey: ['requisition', approval.requisitionId],
    queryFn: () => requisitionApi.getById(approval.requisitionId).then(res => res.data),
    enabled: isExpanded && !!approval.requisitionId,
  })

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
      {/* Header row — always visible */}
      <div className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-3">
              <h3 className="font-bold text-gray-900 text-base">{approval.requisitionNumber}</h3>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-700 border border-yellow-200">
                Pending
              </span>
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-2 text-sm text-gray-500">
              <span className="flex items-center gap-1">
                <User size={14} /> {approval.requesterName}
              </span>
              {approval.departmentName && (
                <span className="flex items-center gap-1">
                  <Building2 size={14} /> {approval.departmentName}
                </span>
              )}
              {approval.workflowName && (
                <span className="text-xs text-gray-400">via {approval.workflowName}</span>
              )}
            </div>
          </div>
          <div className="text-right shrink-0">
            <p className="text-xl font-bold text-gray-900">₹{Number(approval.totalAmount).toLocaleString()}</p>
            <p className="text-xs text-gray-500 mt-1">
              Level {approval.approvalLevel}
              {approval.ruleName ? ` · ${approval.ruleName}` : ''}
            </p>
          </div>
        </div>

        {/* Actions + expand toggle */}
        <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-100">
          <div className="flex gap-3">
            <button onClick={onApprove} className="btn-success flex items-center gap-2 text-sm">
              <CheckCircle size={16} /> Approve
            </button>
            <button onClick={onReject} className="btn-danger flex items-center gap-2 text-sm">
              <XCircle size={16} /> Reject
            </button>
          </div>
          <button
            onClick={onToggle}
            className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700 font-medium"
          >
            {isExpanded ? (
              <><ChevronUp size={16} /> Hide Details</>
            ) : (
              <><ChevronDown size={16} /> View Details</>
            )}
          </button>
        </div>
      </div>

      {/* Expanded detail section */}
      {isExpanded && (
        <div className="border-t border-gray-200 bg-gray-50 p-5">
          {reqLoading ? (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-600" />
            </div>
          ) : requisition ? (
            <div className="space-y-4">
              {/* Metadata grid */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <MetadataItem icon={Building2} label="Department" value={requisition.departmentName || '—'} />
                <MetadataItem icon={Wallet} label="Cost Center" value={requisition.costCenterName || '—'} />
                <MetadataItem icon={Package} label="Items" value={requisition.items?.length || 0} />
                <MetadataItem icon={Calendar} label="Created" value={new Date(requisition.createdAt).toLocaleDateString()} />
              </div>

              {/* Description */}
              {requisition.description && (
                <div className="bg-white rounded-lg p-3 border border-gray-200">
                  <p className="text-xs font-medium text-gray-500 uppercase mb-1">Description</p>
                  <p className="text-sm text-gray-700">{requisition.description}</p>
                </div>
              )}

              {/* Line items table */}
              {requisition.items && requisition.items.length > 0 && (
                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-100 border-b border-gray-200">
                      <tr>
                        <th className="text-left px-4 py-2 text-xs font-semibold text-gray-600">#</th>
                        <th className="text-left px-4 py-2 text-xs font-semibold text-gray-600">Item Name</th>
                        <th className="text-right px-4 py-2 text-xs font-semibold text-gray-600">Qty</th>
                        <th className="text-right px-4 py-2 text-xs font-semibold text-gray-600">Unit Price</th>
                        <th className="text-right px-4 py-2 text-xs font-semibold text-gray-600">Total</th>
                      </tr>
                    </thead>
                    <tbody>
                      {requisition.items.map((item, idx) => (
                        <tr key={item.id || idx} className={idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                          <td className="px-4 py-2 text-gray-500">{idx + 1}</td>
                          <td className="px-4 py-2 font-medium text-gray-900">{item.itemName}</td>
                          <td className="px-4 py-2 text-right text-gray-700">{item.quantity}</td>
                          <td className="px-4 py-2 text-right text-gray-700">₹{Number(item.unitPrice).toLocaleString()}</td>
                          <td className="px-4 py-2 text-right font-medium text-gray-900">₹{Number(item.totalPrice).toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot className="border-t-2 border-gray-300 bg-gray-100">
                      <tr>
                        <td colSpan={4} className="px-4 py-2 text-right font-semibold text-gray-700">Total</td>
                        <td className="px-4 py-2 text-right font-bold text-gray-900">
                          ₹{Number(requisition.totalAmount).toLocaleString()}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              )}
            </div>
          ) : (
            <p className="text-sm text-gray-500 text-center py-4">Unable to load requisition details.</p>
          )}
        </div>
      )}
    </div>
  )
}

function MetadataItem({ icon: Icon, label, value }) {
  return (
    <div className="bg-white rounded-lg p-3 border border-gray-200">
      <div className="flex items-center gap-1.5 mb-1">
        <Icon size={12} className="text-gray-400" />
        <span className="text-xs text-gray-500">{label}</span>
      </div>
      <p className="text-sm font-medium text-gray-900">{value}</p>
    </div>
  )
}
