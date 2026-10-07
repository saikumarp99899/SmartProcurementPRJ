import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { purchaseOrderApi } from '../../api/purchaseOrderApi'
import { vendorApi } from '../../api/vendorApi'
import { useAuth } from '../../context/AuthContext'
import toast from 'react-hot-toast'
import {
  ArrowLeft, Printer, Truck, Calendar, Building2, FileText,
  CreditCard, Package, Edit3, X,
} from 'lucide-react'

const statusConfig = {
  DRAFT: { color: 'bg-gray-100 text-gray-700 border-gray-300', label: 'Draft' },
  SENT: { color: 'bg-blue-100 text-blue-700 border-blue-300', label: 'Sent' },
  ACCEPTED: { color: 'bg-indigo-100 text-indigo-700 border-indigo-300', label: 'Accepted' },
  DELIVERED: { color: 'bg-green-100 text-green-700 border-green-300', label: 'Delivered' },
  CLOSED: { color: 'bg-purple-100 text-purple-700 border-purple-300', label: 'Closed' },
  CANCELLED: { color: 'bg-red-100 text-red-700 border-red-300', label: 'Cancelled' },
}

const statusSteps = ['DRAFT', 'SENT', 'ACCEPTED', 'DELIVERED', 'CLOSED']

const nextStatus = {
  DRAFT: ['SENT', 'CANCELLED'],
  SENT: ['ACCEPTED', 'CANCELLED'],
  ACCEPTED: ['DELIVERED', 'CANCELLED'],
  DELIVERED: ['CLOSED'],
}

export default function PurchaseOrderDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const queryClient = useQueryClient()
  const [showChangeOrder, setShowChangeOrder] = useState(false)

  const { data: po, isLoading } = useQuery({
    queryKey: ['purchaseOrder', id],
    queryFn: () => purchaseOrderApi.getById(id).then(res => res.data),
  })

  const { data: vendor } = useQuery({
    queryKey: ['vendor', po?.vendorId],
    queryFn: () => vendorApi.getById(po.vendorId).then(res => res.data),
    enabled: !!po?.vendorId,
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }) => purchaseOrderApi.updateStatus(id, status),
    onSuccess: () => {
      toast.success('Status updated!')
      queryClient.invalidateQueries(['purchaseOrder', id])
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Update failed'),
  })

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto flex items-center justify-center py-20">
        <div className="flex flex-col items-center gap-3">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600" />
          <p className="text-sm text-gray-500">Loading purchase order...</p>
        </div>
      </div>
    )
  }

  if (!po) {
    return (
      <div className="max-w-5xl mx-auto text-center py-20">
        <p className="text-gray-500">Purchase order not found.</p>
      </div>
    )
  }

  const canChangeOrder = (po.status === 'SENT' || po.status === 'ACCEPTED') &&
    (hasRole('ROLE_ADMIN') || hasRole('ROLE_BUYER'))
  const actions = nextStatus[po.status]

  return (
    <div className="max-w-5xl mx-auto">
      {/* Back navigation */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4 text-sm"
      >
        <ArrowLeft size={16} /> Back
      </button>

      {/* Header Card */}
      <div className="card p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold text-gray-900">{po.poNumber}</h1>
              <span className={`px-3 py-1 rounded-full text-sm font-medium border ${statusConfig[po.status]?.color || 'bg-gray-100 text-gray-700 border-gray-300'}`}>
                {statusConfig[po.status]?.label || po.status}
              </span>
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-2 text-sm text-gray-500">
              <span className="flex items-center gap-1.5">
                <Calendar size={14} className="text-gray-400" /> Ordered: {po.orderDate}
              </span>
              {po.expectedDeliveryDate && (
                <span className="flex items-center gap-1.5">
                  <Truck size={14} className="text-gray-400" /> Expected: {po.expectedDeliveryDate}
                </span>
              )}
              <span className="flex items-center gap-1.5">
                <Building2 size={14} className="text-gray-400" /> Created by: {po.createdByName}
              </span>
            </div>
          </div>
          <div className="text-right">
            <p className="text-2xl font-bold text-gray-900">₹{Number(po.totalAmount).toLocaleString()}</p>
          </div>
        </div>
      </div>

      {/* Status Tracker */}
      <div className="card p-4 mb-6">
        <StatusTracker currentStatus={po.status} />
      </div>

      {/* Vendor Info */}
      <div className="card p-6 mb-6">
        <h2 className="text-base font-semibold text-gray-900 mb-3 flex items-center gap-2">
          <Building2 size={16} /> Vendor Information
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <InfoField label="Vendor Name" value={vendor?.vendorName || po.vendorName} />
          <InfoField label="Vendor Code" value={vendor?.vendorCode || '—'} />
          <InfoField label="Email" value={vendor?.email || '—'} />
          <InfoField label="Phone" value={vendor?.phone || '—'} />
        </div>
      </div>

      {/* Line Items Table */}
      <div className="card mb-6 overflow-hidden">
        <div className="p-6 pb-3">
          <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
            <Package size={16} /> Line Items
          </h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 text-left text-gray-600">
                <th className="px-6 py-3 font-medium">#</th>
                <th className="px-6 py-3 font-medium">Item Name</th>
                <th className="px-6 py-3 font-medium text-right">Qty</th>
                <th className="px-6 py-3 font-medium text-right">Unit Price</th>
                <th className="px-6 py-3 font-medium text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {po.items?.map((item, idx) => (
                <tr key={item.id} className={idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                  <td className="px-6 py-3 text-gray-500">{idx + 1}</td>
                  <td className="px-6 py-3 font-medium text-gray-900">{item.itemName}</td>
                  <td className="px-6 py-3 text-right text-gray-700">{item.quantity}</td>
                  <td className="px-6 py-3 text-right text-gray-700">₹{Number(item.unitPrice).toLocaleString()}</td>
                  <td className="px-6 py-3 text-right font-medium text-gray-900">₹{Number(item.totalPrice).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-gray-200 bg-gray-50">
                <td colSpan="4" className="px-6 py-3 text-right font-medium text-gray-700">Subtotal</td>
                <td className="px-6 py-3 text-right font-bold text-gray-900">₹{Number(po.subtotal || 0).toLocaleString()}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      {/* Totals */}
      <div className="card p-6 mb-6">
        <h2 className="text-base font-semibold text-gray-900 mb-3 flex items-center gap-2">
          <CreditCard size={16} /> Order Totals
        </h2>
        <div className="space-y-2 max-w-xs ml-auto">
          <div className="flex justify-between text-sm text-gray-600">
            <span>Subtotal</span>
            <span>₹{Number(po.subtotal || 0).toLocaleString()}</span>
          </div>
          <div className="flex justify-between text-sm text-gray-600">
            <span>Tax</span>
            <span>₹{Number(po.taxAmount || 0).toLocaleString()}</span>
          </div>
          <div className="flex justify-between text-sm text-gray-600">
            <span>Shipping</span>
            <span>₹{Number(po.shippingCost || 0).toLocaleString()}</span>
          </div>
          <div className="flex justify-between text-base font-bold text-gray-900 pt-2 border-t border-gray-200">
            <span>Grand Total</span>
            <span>₹{Number(po.totalAmount).toLocaleString()}</span>
          </div>
        </div>
      </div>

      {/* Terms & Notes */}
      <div className="card p-6 mb-6">
        <h2 className="text-base font-semibold text-gray-900 mb-3 flex items-center gap-2">
          <FileText size={16} /> Terms & Notes
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <InfoField label="Payment Terms" value={po.paymentTerms || '—'} />
          <InfoField label="Shipping Method" value={po.shippingMethod || '—'} />
          <div className="sm:col-span-3">
            <InfoField label="Notes" value={po.notes || '—'} />
          </div>
        </div>
      </div>

      {/* Actions */}
      <div className="flex flex-wrap gap-3 pt-2 mb-8">
        {actions && actions.length > 0 && actions.map(ns => (
          <button
            key={ns}
            onClick={() => statusMutation.mutate({ id: po.id, status: ns })}
            disabled={statusMutation.isPending}
            className={ns === 'CANCELLED'
              ? 'px-4 py-2 text-sm font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors'
              : 'px-4 py-2 text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 rounded-lg transition-colors'
            }
          >
            {ns === 'CANCELLED' ? 'Cancel PO' : `Mark ${ns.charAt(0) + ns.slice(1).toLowerCase()}`}
          </button>
        ))}
        {canChangeOrder && (
          <button
            onClick={() => setShowChangeOrder(true)}
            className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-amber-700 bg-amber-50 border border-amber-200 rounded-lg hover:bg-amber-100 transition-colors"
          >
            <Edit3 size={16} /> Change Order
          </button>
        )}
        <button
          onClick={() => window.print()}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
        >
          <Printer size={16} /> Print
        </button>
      </div>

      {/* Change Order Modal */}
      {showChangeOrder && (
        <ChangeOrderModal
          po={po}
          onClose={() => setShowChangeOrder(false)}
          onSuccess={() => {
            setShowChangeOrder(false)
            queryClient.invalidateQueries(['purchaseOrder', id])
          }}
        />
      )}
    </div>
  )
}

/* ─── Status Tracker ─── */
function StatusTracker({ currentStatus }) {
  const currentIdx = statusSteps.indexOf(currentStatus)
  const isCancelled = currentStatus === 'CANCELLED'

  return (
    <div className="flex items-center justify-between">
      {statusSteps.map((step, idx) => {
        const isCompleted = !isCancelled && idx < currentIdx
        const isCurrent = step === currentStatus
        return (
          <div key={step} className="flex items-center flex-1">
            <div className="flex flex-col items-center flex-1">
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all
                  ${isCompleted ? 'bg-green-500 text-white' : ''}
                  ${isCurrent && !isCancelled ? 'bg-primary-600 text-white ring-4 ring-primary-100' : ''}
                  ${!isCompleted && !isCurrent ? 'bg-gray-200 text-gray-500' : ''}
                  ${isCancelled && step === currentStatus ? 'bg-red-500 text-white ring-4 ring-red-100' : ''}
                `}
              >
                {isCompleted ? '✓' : idx + 1}
              </div>
              <span className={`text-xs mt-1 ${isCurrent ? 'font-semibold text-primary-700' : 'text-gray-500'}`}>
                {step}
              </span>
            </div>
            {idx < statusSteps.length - 1 && (
              <div className={`h-0.5 flex-1 mx-1 ${isCompleted ? 'bg-green-400' : 'bg-gray-200'}`} />
            )}
          </div>
        )
      })}
      {isCancelled && (
        <div className="flex flex-col items-center ml-4">
          <div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold bg-red-500 text-white ring-4 ring-red-100">
            ✕
          </div>
          <span className="text-xs mt-1 font-semibold text-red-700">CANCELLED</span>
        </div>
      )}
    </div>
  )
}

/* ─── Info Field ─── */
function InfoField({ label, value }) {
  return (
    <div>
      <p className="text-xs text-gray-500 uppercase tracking-wide">{label}</p>
      <p className="text-sm text-gray-900 mt-0.5">{value}</p>
    </div>
  )
}

/* ─── Change Order Modal ─── */
function ChangeOrderModal({ po, onClose, onSuccess }) {
  const [items, setItems] = useState(
    po.items.map(item => ({
      id: item.id,
      itemName: item.itemName,
      quantity: item.quantity,
      unitPrice: Number(item.unitPrice),
    }))
  )
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState(po.expectedDeliveryDate || '')
  const [paymentTerms, setPaymentTerms] = useState(po.paymentTerms || '')
  const [shippingMethod, setShippingMethod] = useState(po.shippingMethod || '')
  const [changeReason, setChangeReason] = useState('')

  const changeOrderMutation = useMutation({
    mutationFn: (payload) => purchaseOrderApi.changeOrder(po.id, payload),
    onSuccess: () => {
      toast.success('Change order submitted successfully!')
      onSuccess()
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Change order failed'),
  })

  const updateItem = (idx, field, value) => {
    const updated = [...items]
    updated[idx] = { ...updated[idx], [field]: value }
    setItems(updated)
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!changeReason.trim()) {
      toast.error('Change reason is required')
      return
    }
    changeOrderMutation.mutate({
      expectedDeliveryDate: expectedDeliveryDate || null,
      paymentTerms,
      shippingMethod,
      changeReason,
      items: items.map(item => ({
        id: item.id,
        itemName: item.itemName,
        quantity: Number(item.quantity),
        unitPrice: Number(item.unitPrice),
      })),
    })
  }

  const calculatedSubtotal = items.reduce((sum, item) => sum + (Number(item.quantity) * Number(item.unitPrice)), 0)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-3xl max-h-[90vh] overflow-y-auto m-4">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-lg font-bold text-gray-900">Change Order — {po.poNumber}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          {/* Editable Line Items */}
          <div>
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Line Items</h3>
            <div className="overflow-x-auto border border-gray-200 rounded-lg">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 text-left text-gray-600">
                    <th className="px-4 py-2 font-medium">Item</th>
                    <th className="px-4 py-2 font-medium text-right">Qty</th>
                    <th className="px-4 py-2 font-medium text-right">Unit Price</th>
                    <th className="px-4 py-2 font-medium text-right">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item, idx) => (
                    <tr key={idx} className={idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                      <td className="px-4 py-2 text-gray-900">{item.itemName}</td>
                      <td className="px-4 py-2">
                        <input
                          type="number"
                          min="1"
                          value={item.quantity}
                          onChange={(e) => updateItem(idx, 'quantity', e.target.value)}
                          className="w-20 text-right px-2 py-1 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-primary-500"
                        />
                      </td>
                      <td className="px-4 py-2">
                        <input
                          type="number"
                          min="0"
                          step="0.01"
                          value={item.unitPrice}
                          onChange={(e) => updateItem(idx, 'unitPrice', e.target.value)}
                          className="w-28 text-right px-2 py-1 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-primary-500"
                        />
                      </td>
                      <td className="px-4 py-2 text-right font-medium text-gray-900">
                        ₹{(Number(item.quantity) * Number(item.unitPrice)).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-gray-200 bg-gray-50">
                    <td colSpan="3" className="px-4 py-2 text-right font-medium text-gray-700">New Subtotal</td>
                    <td className="px-4 py-2 text-right font-bold text-gray-900">₹{calculatedSubtotal.toLocaleString()}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          {/* Editable Fields */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Expected Delivery Date</label>
              <input
                type="date"
                value={expectedDeliveryDate}
                onChange={(e) => setExpectedDeliveryDate(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Payment Terms</label>
              <input
                type="text"
                value={paymentTerms}
                onChange={(e) => setPaymentTerms(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Shipping Method</label>
              <input
                type="text"
                value={shippingMethod}
                onChange={(e) => setShippingMethod(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>

          {/* Change Reason */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Change Reason <span className="text-red-500">*</span>
            </label>
            <textarea
              rows={3}
              value={changeReason}
              onChange={(e) => setChangeReason(e.target.value)}
              placeholder="Explain why this change order is needed..."
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
              required
            />
          </div>

          {/* Buttons */}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={changeOrderMutation.isPending}
              className="px-4 py-2 text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 rounded-lg transition-colors disabled:opacity-50"
            >
              {changeOrderMutation.isPending ? 'Submitting...' : 'Submit Change Order'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
