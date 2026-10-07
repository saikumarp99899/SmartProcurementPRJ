import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { purchaseOrderApi } from '../../api/purchaseOrderApi'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { PlusCircle, Package, Truck, Calendar, Building2, ChevronRight } from 'lucide-react'

const statusConfig = {
  DRAFT: { color: 'bg-gray-100 text-gray-700 border-gray-200', dot: 'bg-gray-400', label: 'Draft' },
  SENT: { color: 'bg-blue-100 text-blue-700 border-blue-200', dot: 'bg-blue-500', label: 'Sent' },
  ACCEPTED: { color: 'bg-indigo-100 text-indigo-700 border-indigo-200', dot: 'bg-indigo-500', label: 'Accepted' },
  DELIVERED: { color: 'bg-green-100 text-green-700 border-green-200', dot: 'bg-green-500', label: 'Delivered' },
  CLOSED: { color: 'bg-purple-100 text-purple-700 border-purple-200', dot: 'bg-purple-500', label: 'Closed' },
  CANCELLED: { color: 'bg-red-100 text-red-700 border-red-200', dot: 'bg-red-500', label: 'Cancelled' },
}

const nextStatus = {
  DRAFT: ['SENT', 'CANCELLED'],
  SENT: ['ACCEPTED', 'CANCELLED'],
  ACCEPTED: ['DELIVERED', 'CANCELLED'],
  DELIVERED: ['CLOSED'],
}

export default function MyPurchaseOrders() {
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState('')
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['purchaseOrders', 'my', page, statusFilter],
    queryFn: () => purchaseOrderApi.getMy({ page, size: 10, status: statusFilter || undefined }).then(res => res.data),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }) => purchaseOrderApi.updateStatus(id, status),
    onSuccess: () => { toast.success('Status updated!'); queryClient.invalidateQueries(['purchaseOrders']) },
    onError: (err) => toast.error(err.response?.data?.message || 'Update failed'),
  })

  return (
    <div>
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Purchase Orders</h1>
          <p className="text-sm text-gray-500 mt-1">Manage your purchase orders and track delivery status</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            <option value="">All Status</option>
            {Object.entries(statusConfig).map(([key, cfg]) => (
              <option key={key} value={key}>{cfg.label}</option>
            ))}
          </select>
          <button onClick={() => navigate('/purchase-orders/create')} className="btn-primary flex items-center gap-2">
            <PlusCircle size={18} /> Create PO
          </button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600" />
        </div>
      ) : data?.data?.length === 0 ? (
        <div className="card text-center py-16">
          <Package size={48} className="mx-auto text-gray-300 mb-4" />
          <p className="text-gray-600 font-medium">No purchase orders found</p>
          <p className="text-sm text-gray-400 mt-1">Create your first PO to get started.</p>
        </div>
      ) : (
        <>
          <div className="space-y-3">
            {data?.data?.map((po) => {
              const status = statusConfig[po.status] || statusConfig.DRAFT
              const actions = nextStatus[po.status]

              return (
                <div key={po.id} className="bg-white border border-gray-200 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                  <div className="p-5">
                    <div className="flex items-start justify-between gap-4">
                      {/* Left side */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-3">
                          <h3 className="font-bold text-gray-900">{po.poNumber}</h3>
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${status.color}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${status.dot}`} />
                            {status.label}
                          </span>
                        </div>
                        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-2 text-sm text-gray-500">
                          <span className="flex items-center gap-1.5">
                            <Building2 size={14} className="text-gray-400" /> {po.vendorName}
                          </span>
                          <span className="flex items-center gap-1.5">
                            <Calendar size={14} className="text-gray-400" /> {po.orderDate}
                          </span>
                          {po.expectedDeliveryDate && (
                            <span className="flex items-center gap-1.5">
                              <Truck size={14} className="text-gray-400" /> Due: {po.expectedDeliveryDate}
                            </span>
                          )}
                        </div>
                        {po.items && (
                          <p className="text-xs text-gray-400 mt-1.5">
                            {po.items.length} item{po.items.length !== 1 ? 's' : ''}
                          </p>
                        )}
                      </div>

                      {/* Right side */}
                      <div className="text-right shrink-0">
                        <p className="text-xl font-bold text-gray-900">₹{Number(po.totalAmount).toLocaleString()}</p>
                      </div>
                    </div>

                    {/* Actions */}
                    {actions && actions.length > 0 && (
                      <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-100">
                        <div className="flex gap-2">
                          {actions.map(ns => (
                            <button
                              key={ns}
                              onClick={() => statusMutation.mutate({ id: po.id, status: ns })}
                              disabled={statusMutation.isPending}
                              className={ns === 'CANCELLED'
                                ? 'px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors'
                                : 'px-3 py-1.5 text-xs font-medium text-primary-700 bg-primary-50 border border-primary-200 rounded-lg hover:bg-primary-100 transition-colors'
                              }
                            >
                              {ns === 'CANCELLED' ? 'Cancel' : `Mark ${ns.charAt(0) + ns.slice(1).toLowerCase()}`}
                            </button>
                          ))}
                        </div>
                        <button
                          onClick={() => navigate(`/purchase-orders/${po.id}`)}
                          className="flex items-center gap-1 text-sm text-gray-500 hover:text-primary-600 transition-colors"
                        >
                          View <ChevronRight size={14} />
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              )
            })}
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
