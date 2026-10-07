import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { requisitionApi } from '../../api/requisitionApi'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PlusCircle, Eye, Clock } from 'lucide-react'

const statusColors = {
  DRAFT: 'bg-gray-100 text-gray-700',
  SUBMITTED: 'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
  PO_CREATED: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-purple-100 text-purple-700',
}

const STATUS_OPTIONS = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PO_CREATED', 'COMPLETED']

export default function MyRequisitions() {
  const [page, setPage] = useState(0)
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  const status = searchParams.get('status') || ''

  // Reset to first page whenever the status filter changes
  useEffect(() => { setPage(0) }, [status])

  const { data, isLoading } = useQuery({
    queryKey: ['requisitions', 'my', status, page],
    queryFn: () =>
      requisitionApi.getMy({ page, size: 10, ...(status ? { status } : {}) }).then(res => res.data),
  })

  const handleStatusChange = (value) => {
    if (value) {
      setSearchParams({ status: value })
    } else {
      setSearchParams({})
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h1 className="text-2xl font-bold text-gray-900">My Requisitions</h1>
        <div className="flex items-center gap-3">
          <select
            value={status}
            onChange={(e) => handleStatusChange(e.target.value)}
            className="input-field py-2"
            aria-label="Filter by status"
          >
            <option value="">All Statuses</option>
            {STATUS_OPTIONS.map(s => (
              <option key={s} value={s}>{s.replace('_', ' ')}</option>
            ))}
          </select>
          <button onClick={() => navigate('/requisitions/create')} className="btn-primary flex items-center gap-2">
            <PlusCircle size={18} /> New Requisition
          </button>
        </div>
      </div>

      {isLoading ? <p className="text-center py-10">Loading...</p> : (
        <>
          <div className="card overflow-hidden p-0">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Req #</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Description</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Department</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Amount</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Status</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {data?.data?.map((req) => (
                  <tr key={req.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 font-mono text-xs">{req.requisitionNumber}</td>
                    <td className="px-6 py-4 text-gray-600 max-w-xs truncate">{req.description || '-'}</td>
                    <td className="px-6 py-4 text-gray-600">{req.departmentName || '-'}</td>
                    <td className="px-6 py-4 font-medium">₹{Number(req.totalAmount).toLocaleString()}</td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[req.status]}`}>
                          {req.status}
                        </span>
                        {req.status === 'SUBMITTED' && (
                          <span className="inline-flex items-center gap-1 text-xs text-gray-500" title="Waiting for an approver to review">
                            <Clock size={12} /> with approver
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <button onClick={() => navigate(`/requisitions/${req.id}`)} className="text-primary-600 hover:text-primary-700 flex items-center gap-1 text-xs font-medium">
                        <Eye size={14} /> View
                      </button>
                    </td>
                  </tr>
                ))}
                {data?.data?.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-6 py-10 text-center text-gray-500">
                      No requisitions found{status ? ` with status ${status.replace('_', ' ')}` : ''}.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex justify-between items-center mt-4">
            <p className="text-sm text-gray-500">Page {page + 1} of {data?.totalPages || 1}</p>
            <div className="flex gap-2">
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="btn-secondary text-sm">Previous</button>
              <button onClick={() => setPage(p => p + 1)} disabled={data?.last} className="btn-secondary text-sm">Next</button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
