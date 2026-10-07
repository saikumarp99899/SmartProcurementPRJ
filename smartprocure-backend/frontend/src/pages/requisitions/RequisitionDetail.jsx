import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { requisitionApi } from '../../api/requisitionApi'
import { useAuth } from '../../context/AuthContext'
import toast from 'react-hot-toast'
import {
  ArrowLeft, Send, ShoppingCart, Printer, XCircle,
} from 'lucide-react'

import WorkflowTracker from './WorkflowTracker'
import ApprovalTimeline from './ApprovalTimeline'
import LineItemsTable from './LineItemsTable'
import RequisitionMetadata from './RequisitionMetadata'
import PrintLayout from './PrintLayout'

const statusColors = {
  DRAFT: 'bg-gray-100 text-gray-700 border-gray-300',
  SUBMITTED: 'bg-yellow-100 text-yellow-700 border-yellow-300',
  APPROVED: 'bg-green-100 text-green-700 border-green-300',
  REJECTED: 'bg-red-100 text-red-700 border-red-300',
  PO_CREATED: 'bg-blue-100 text-blue-700 border-blue-300',
  COMPLETED: 'bg-purple-100 text-purple-700 border-purple-300',
}

export default function RequisitionDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user, hasRole } = useAuth()
  const queryClient = useQueryClient()

  const { data: req, isLoading } = useQuery({
    queryKey: ['requisition', id],
    queryFn: () => requisitionApi.getById(id).then(res => res.data),
  })

  const { data: approvals } = useQuery({
    queryKey: ['requisition', id, 'approvals'],
    queryFn: () => requisitionApi.getApprovals(id).then(res => res.data),
  })

  const submitMutation = useMutation({
    mutationFn: () => requisitionApi.submit(id),
    onSuccess: () => {
      toast.success('Requisition submitted for approval!')
      queryClient.invalidateQueries(['requisition', id])
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Submit failed'),
  })

  const handlePrint = () => {
    window.print()
  }

  // Loading state
  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto flex items-center justify-center py-20">
        <div className="flex flex-col items-center gap-3">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600" />
          <p className="text-sm text-gray-500">Loading requisition...</p>
        </div>
      </div>
    )
  }

  if (!req) {
    return (
      <div className="max-w-5xl mx-auto text-center py-20">
        <p className="text-gray-500">Requisition not found.</p>
      </div>
    )
  }

  const canSubmit = req.status === 'DRAFT' && req.requesterId === user?.id
  const canCreatePO =
    req.status === 'APPROVED' &&
    (hasRole('ROLE_ADMIN') || hasRole('ROLE_BUYER'))

  // Find rejection info from approvals
  const rejectionApproval = approvals?.find(a => a.status === 'REJECTED')

  return (
    <>
      {/* Print Layout — hidden on screen, shown only during print */}
      <div className="print-only" aria-hidden="true">
        <PrintLayout requisition={req} approvals={approvals} />
      </div>

      {/* Screen Layout — hidden during print */}
      <div className="max-w-5xl mx-auto screen-only">
        {/* Back navigation */}
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4 text-sm"
        >
          <ArrowLeft size={16} /> Back
        </button>

        {/* Header Card — Requirement 2.1 */}
        <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-6 mb-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h1 className="text-xl font-bold text-gray-900">
                {req.requisitionNumber}
              </h1>
              <p className="text-sm text-gray-500 mt-1">
                Requested by <span className="font-medium text-gray-700">{req.requesterName}</span>
                {' · '}
                {new Date(req.createdAt).toLocaleDateString()}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <span className="text-xl font-bold text-gray-900">
                ₹{Number(req.totalAmount).toLocaleString()}
              </span>
              <span
                className={`px-3 py-1 rounded-full text-sm font-medium border ${
                  statusColors[req.status] || 'bg-gray-100 text-gray-700 border-gray-300'
                }`}
              >
                {req.status}
              </span>
            </div>
          </div>
        </div>

        {/* Workflow Progress Tracker — Requirement 2.2 */}
        <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-4 mb-6">
          <WorkflowTracker status={req.status} />
        </div>

        {/* Rejection Banner — Requirement 2.3 */}
        {req.status === 'REJECTED' && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-4 mb-6 flex items-start gap-3">
            <XCircle size={20} className="text-red-500 mt-0.5 flex-shrink-0" />
            <div>
              <p className="font-semibold text-red-800">Requisition Rejected</p>
              {rejectionApproval && (
                <>
                  <p className="text-sm text-red-700 mt-1">
                    Rejected by: <span className="font-medium">{rejectionApproval.approverName || 'Unknown'}</span>
                  </p>
                  {rejectionApproval.comments && (
                    <p className="text-sm text-red-600 mt-1">
                      Reason: {rejectionApproval.comments}
                    </p>
                  )}
                </>
              )}
            </div>
          </div>
        )}

        {/* Line Items Table — Requirement 2.4 */}
        <div className="mb-6">
          <h2 className="text-base font-semibold text-gray-900 mb-3">Line Items</h2>
          <LineItemsTable items={req.items} />
        </div>

        {/* Metadata Grid — Requirement 2.8 */}
        <div className="mb-6">
          <h2 className="text-base font-semibold text-gray-900 mb-3">Details</h2>
          <RequisitionMetadata requisition={req} />
        </div>

        {/* Approval Timeline — Requirements 2.5, 2.6 */}
        <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-6 mb-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">Approval History</h2>
          <ApprovalTimeline approvals={approvals} />
        </div>

        {/* Actions — Requirements 2.7, 2.9 */}
        <div className="flex flex-wrap gap-3 pt-2 mb-8 print:hidden">
          {canSubmit && (
            <button
              onClick={() => submitMutation.mutate()}
              disabled={submitMutation.isPending}
              className="btn-primary flex items-center gap-2"
            >
              <Send size={16} /> Submit for Approval
            </button>
          )}
          {canCreatePO && (
            <button
              onClick={() => navigate(`/purchase-orders/create?requisitionId=${req.id}`)}
              className="flex items-center gap-2 px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg font-medium transition-colors"
              style={{ minHeight: '36px' }}
            >
              <ShoppingCart size={16} /> Create Purchase Order
            </button>
          )}
          <button
            onClick={handlePrint}
            className="flex items-center gap-2 px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg font-medium transition-colors"
          >
            <Printer size={16} /> Print
          </button>
        </div>
      </div>
    </>
  )
}
