import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { requisitionApi } from '../../api/requisitionApi'
import { useNavigate } from 'react-router-dom'
import { Eye } from 'lucide-react'

const statusColors = {
  DRAFT: 'bg-gray-100 text-gray-700',
  SUBMITTED: 'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
  PO_CREATED: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-purple-100 text-purple-700',
}

export default function Requisitions() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const navigate = useNavigate()

  const { data, isLoading } = useQuery({
    queryKey: ['requisitions', 'all', page, status],
    queryFn: () => requisitionApi.getAll({ page, size: 10, status: status || undefined }).then(res => res.data),
  })

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">All Requisitions</h1>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className="input-field w-48">
          <option value="">All Status</option>
          <option value="DRAFT">Draft</option>
          <option value="SUBMITTED">Submitted</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="PO_CREATED">PO Created</option>
          <option value="COMPLETED">Completed</option>
        </select>
      </div>

      {isLoading ? <p className="text-center py-10">Loading...</p> : (
        <>
          <div className="card overflow-hidden p-0">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Req #</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Requester</th>
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
                    <td className="px-6 py-4">{req.requesterName}</td>
                    <td className="px-6 py-4 text-gray-600">{req.departmentName || '-'}</td>
                    <td className="px-6 py-4 font-medium">₹{Number(req.totalAmount).toLocaleString()}</td>
                    <td className="px-6 py-4">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[req.status]}`}>
                        {req.status}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <button onClick={() => navigate(`/requisitions/${req.id}`)} className="text-primary-600 hover:text-primary-700 flex items-center gap-1 text-xs font-medium">
                        <Eye size={14} /> View
                      </button>
                    </td>
                  </tr>
                ))}
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
