import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { purchaseOrderApi } from '../../api/purchaseOrderApi'
import { useNavigate } from 'react-router-dom'
import { ShoppingCart, Eye, Calendar, Building2 } from 'lucide-react'

const statusConfig = {
  DRAFT: { color: 'bg-gray-100 text-gray-700 border-gray-200', dot: 'bg-gray-400', label: 'Draft' },
  SENT: { color: 'bg-blue-100 text-blue-700 border-blue-200', dot: 'bg-blue-500', label: 'Sent' },
  ACCEPTED: { color: 'bg-indigo-100 text-indigo-700 border-indigo-200', dot: 'bg-indigo-500', label: 'Accepted' },
  DELIVERED: { color: 'bg-green-100 text-green-700 border-green-200', dot: 'bg-green-500', label: 'Delivered' },
  CLOSED: { color: 'bg-purple-100 text-purple-700 border-purple-200', dot: 'bg-purple-500', label: 'Closed' },
  CANCELLED: { color: 'bg-red-100 text-red-700 border-red-200', dot: 'bg-red-500', label: 'Cancelled' },
}

export default function PurchaseOrders() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const navigate = useNavigate()

  const { data, isLoading } = useQuery({
    queryKey: ['purchaseOrders', 'all', page, status],
    queryFn: () => purchaseOrderApi.getAll({ page, size: 10, status: status || undefined }).then(res => res.data),
  })

  return (
    <div>
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-primary-100 rounded-lg">
            <ShoppingCart className="text-primary-600" size={22} />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">All Purchase Orders</h1>
            <p className="text-sm text-gray-500">Organization-wide purchase order tracking</p>
          </div>
        </div>
        <select
          value={status}
          onChange={(e) => { setStatus(e.target.value); setPage(0) }}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          <option value="">All Status</option>
          {Object.entries(statusConfig).map(([key, cfg]) => (
            <option key={key} value={key}>{cfg.label}</option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600" />
        </div>
      ) : data?.data?.length === 0 ? (
        <div className="card text-center py-16">
          <ShoppingCart size={48} className="mx-auto text-gray-300 mb-4" />
          <p className="text-gray-600 font-medium">No purchase orders found</p>
          <p className="text-sm text-gray-400 mt-1">
            {status ? 'Try a different status filter.' : 'Purchase orders will appear here once created.'}
          </p>
        </div>
      ) : (
        <>
          {/* Table card */}
          <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-600 uppercase tracking-wider">PO Number</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-600 uppercase tracking-wider">Vendor</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-600 uppercase tracking-wider">Created By</th>
                    <th className="text-right px-5 py-3.5 text-xs font-semibold text-gray-600 uppercase tracking-wider">Amount</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-600 uppercase tracking-wider">Order Date</th>
                    <th className="text-left px-5 py-3.5 text-xs font-semibold text-gray-600 uppercase tracking-wider">Status</th>
                    <th className="text-center px-5 py-3.5 text-xs font-semibold text-gray-600 uppercase tracking-wider">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {data?.data?.map((po, idx) => {
                    const statusCfg = statusConfig[po.status] || statusConfig.DRAFT
                    return (
                      <tr key={po.id} className={`hover:bg-gray-50 transition-colors ${idx % 2 === 0 ? 'bg-white' : 'bg-gray-50/50'}`}>
                        <td className="px-5 py-4">
                          <span className="font-mono text-xs font-medium text-gray-900">{po.poNumber}</span>
                        </td>
                        <td className="px-5 py-4">
                          <div className="flex items-center gap-2">
                            <Building2 size={14} className="text-gray-400" />
                            <span className="font-medium text-gray-800">{po.vendorName}</span>
                          </div>
                        </td>
                        <td className="px-5 py-4 text-gray-600">{po.createdByName}</td>
                        <td className="px-5 py-4 text-right">
                          <span className="font-bold text-gray-900">₹{Number(po.totalAmount).toLocaleString()}</span>
                        </td>
                        <td className="px-5 py-4">
                          <div className="flex items-center gap-1.5 text-gray-600">
                            <Calendar size={13} className="text-gray-400" />
                            {po.orderDate}
                          </div>
                        </td>
                        <td className="px-5 py-4">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${statusCfg.color}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${statusCfg.dot}`} />
                            {statusCfg.label}
                          </span>
                        </td>
                        <td className="px-5 py-4 text-center">
                          <button
                            onClick={() => navigate(`/purchase-orders/${po.id}`)}
                            className="p-1.5 rounded-lg text-gray-400 hover:text-primary-600 hover:bg-primary-50 transition-colors"
                            title="View Details"
                          >
                            <Eye size={16} />
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          <div className="flex justify-between items-center mt-6">
            <p className="text-sm text-gray-500">
              Page {page + 1} of {data?.totalPages || 1}
              {data?.totalItems && ` · ${data.totalItems} total`}
            </p>
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
