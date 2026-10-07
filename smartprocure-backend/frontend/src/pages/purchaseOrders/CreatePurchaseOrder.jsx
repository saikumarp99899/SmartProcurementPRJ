import { useState, useEffect, useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useForm, useFieldArray } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { purchaseOrderApi } from '../../api/purchaseOrderApi'
import { requisitionApi } from '../../api/requisitionApi'
import { vendorApi } from '../../api/vendorApi'
import { addressApi } from '../../api/addressApi'
import toast from 'react-hot-toast'
import {
  ArrowLeft,
  Package,
  Building2,
  Truck,
  FileText,
  MapPin,
  Receipt,
  StickyNote,
} from 'lucide-react'

// --- Zod validation schema ---
const lineItemSchema = z.object({
  itemName: z.string().min(1, 'Item name is required'),
  description: z.string().optional().default(''),
  quantity: z.coerce.number().int().min(1, 'Qty must be at least 1'),
  unitPrice: z.coerce.number().min(0.01, 'Unit price must be > 0'),
})

const purchaseOrderSchema = z.object({
  requisitionId: z.coerce.number().min(1, 'Requisition is required'),
  vendorId: z.coerce.number().min(1, 'Vendor is required'),
  expectedDeliveryDate: z.string().min(1, 'Delivery date is required'),
  paymentTerms: z.string().min(1, 'Payment terms required'),
  shippingMethod: z.string().optional().default(''),
  shipToAddressId: z.coerce.number().optional(),
  billToAddressId: z.coerce.number().optional(),
  notes: z.string().optional().default(''),
  taxPercent: z.coerce.number().min(0).max(100).default(0),
  shippingCost: z.coerce.number().min(0).default(0),
  items: z.array(lineItemSchema).min(1, 'At least one line item is required'),
})

const PAYMENT_TERMS_OPTIONS = [
  '30 Days Net',
  '45 Days Net',
  '60 Days Net',
  '15 Days Net',
  'Immediate',
  'Due on Receipt',
  '2/10 Net 30',
]

const SHIPPING_METHOD_OPTIONS = [
  'Standard Ground',
  'Express',
  'Overnight',
  'Freight',
  'Pickup',
  'Courier',
]

export default function CreatePurchaseOrder() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [loading, setLoading] = useState(false)
  const requisitionIdParam = searchParams.get('requisitionId')

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(purchaseOrderSchema),
    defaultValues: {
      requisitionId: requisitionIdParam || '',
      vendorId: '',
      expectedDeliveryDate: '',
      paymentTerms: '30 Days Net',
      shippingMethod: '',
      shipToAddressId: '',
      billToAddressId: '',
      notes: '',
      taxPercent: 0,
      shippingCost: 0,
      items: [],
    },
    mode: 'onBlur',
  })

  const { fields, replace } = useFieldArray({ control, name: 'items' })

  const watchedValues = watch()
  const requisitionId = watch('requisitionId')

  // --- Queries ---
  const { data: requisitionData, isLoading: reqLoading } = useQuery({
    queryKey: ['requisition', requisitionId],
    queryFn: () => requisitionApi.getById(requisitionId).then(res => res.data),
    enabled: !!requisitionId && Number(requisitionId) > 0,
  })

  const { data: vendors } = useQuery({
    queryKey: ['vendors', 'active'],
    queryFn: () => vendorApi.search({ status: 'ACTIVE', size: 100 }).then(res => res.data),
  })

  const { data: shipToAddresses } = useQuery({
    queryKey: ['addresses', 'SHIP_TO'],
    queryFn: () => addressApi.getByType('SHIP_TO').then(res => res.data),
  })

  const { data: billToAddresses } = useQuery({
    queryKey: ['addresses', 'BILL_TO'],
    queryFn: () => addressApi.getByType('BILL_TO').then(res => res.data),
  })

  // Pre-populate items from requisition
  useEffect(() => {
    if (requisitionData?.items?.length) {
      const items = requisitionData.items.map(item => ({
        itemName: item.itemName,
        description: item.description || '',
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      }))
      replace(items)
    }
  }, [requisitionData, replace])

  // Pre-select vendor from requisition if available (use search param or requisition vendor)
  useEffect(() => {
    if (requisitionData?.vendorId && !watchedValues.vendorId) {
      setValue('vendorId', requisitionData.vendorId)
    }
  }, [requisitionData, setValue, watchedValues.vendorId])

  // Pre-select default addresses
  useEffect(() => {
    if (shipToAddresses?.length && !watchedValues.shipToAddressId) {
      const defaultAddr = shipToAddresses.find(a => a.isDefault) || shipToAddresses[0]
      if (defaultAddr) setValue('shipToAddressId', defaultAddr.id)
    }
  }, [shipToAddresses, setValue, watchedValues.shipToAddressId])

  useEffect(() => {
    if (billToAddresses?.length && !watchedValues.billToAddressId) {
      const defaultAddr = billToAddresses.find(a => a.isDefault) || billToAddresses[0]
      if (defaultAddr) setValue('billToAddressId', defaultAddr.id)
    }
  }, [billToAddresses, setValue, watchedValues.billToAddressId])

  // --- Calculations ---
  const subtotal = useMemo(() => {
    return (watchedValues.items || []).reduce((sum, item) => {
      return sum + (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0)
    }, 0)
  }, [watchedValues.items])

  const taxAmount = useMemo(() => {
    return subtotal * ((Number(watchedValues.taxPercent) || 0) / 100)
  }, [subtotal, watchedValues.taxPercent])

  const shippingCost = Number(watchedValues.shippingCost) || 0
  const grandTotal = subtotal + taxAmount + shippingCost

  // Get selected vendor details
  const selectedVendor = useMemo(() => {
    if (!watchedValues.vendorId || !vendors?.data) return null
    return vendors.data.find(v => v.id === Number(watchedValues.vendorId))
  }, [watchedValues.vendorId, vendors])

  // Get selected addresses
  const selectedShipTo = useMemo(() => {
    if (!watchedValues.shipToAddressId || !shipToAddresses) return null
    return shipToAddresses.find(a => a.id === Number(watchedValues.shipToAddressId))
  }, [watchedValues.shipToAddressId, shipToAddresses])

  const selectedBillTo = useMemo(() => {
    if (!watchedValues.billToAddressId || !billToAddresses) return null
    return billToAddresses.find(a => a.id === Number(watchedValues.billToAddressId))
  }, [watchedValues.billToAddressId, billToAddresses])

  // --- Submit ---
  const onSubmit = async (data) => {
    setLoading(true)
    try {
      const payload = {
        requisitionId: Number(data.requisitionId),
        vendorId: Number(data.vendorId),
        expectedDeliveryDate: data.expectedDeliveryDate || null,
        paymentTerms: data.paymentTerms,
        shippingMethod: data.shippingMethod || null,
        shipToAddressId: data.shipToAddressId ? Number(data.shipToAddressId) : null,
        billToAddressId: data.billToAddressId ? Number(data.billToAddressId) : null,
        notes: data.notes || null,
        taxAmount: taxAmount,
        shippingCost: Number(data.shippingCost) || 0,
        items: data.items.map(item => ({
          itemName: item.itemName,
          description: item.description || '',
          quantity: Number(item.quantity),
          unitPrice: Number(item.unitPrice),
        })),
      }
      await purchaseOrderApi.create(payload)
      toast.success('Purchase Order created successfully!')
      navigate('/purchase-orders/my')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create Purchase Order')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-7xl mx-auto pb-12">
      {/* Back Button */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4 transition-colors"
      >
        <ArrowLeft size={18} /> Back
      </button>

      <h1 className="text-xl font-bold text-gray-900 mb-6">Create Purchase Order</h1>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>
        {/* === Section 1: PO Header === */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <FileText size={20} className="text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">PO Header</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Source Requisition *
              </label>
              <input
                type="number"
                {...register('requisitionId')}
                className="input-field"
                placeholder="Enter approved requisition ID"
              />
              {errors.requisitionId && (
                <p className="text-xs text-red-500 mt-1">{errors.requisitionId.message}</p>
              )}
              {requisitionData && (
                <p className="text-xs text-green-600 mt-1">
                  ✓ {requisitionData.requisitionNumber} — {requisitionData.status}
                </p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Payment Terms *
              </label>
              <select {...register('paymentTerms')} className="input-field">
                {PAYMENT_TERMS_OPTIONS.map(term => (
                  <option key={term} value={term}>{term}</option>
                ))}
              </select>
              {errors.paymentTerms && (
                <p className="text-xs text-red-500 mt-1">{errors.paymentTerms.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Shipping Method
              </label>
              <select {...register('shippingMethod')} className="input-field">
                <option value="">Select shipping method</option>
                {SHIPPING_METHOD_OPTIONS.map(method => (
                  <option key={method} value={method}>{method}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Expected Delivery Date *
              </label>
              <input
                type="date"
                {...register('expectedDeliveryDate')}
                className="input-field"
                min={new Date().toISOString().split('T')[0]}
              />
              {errors.expectedDeliveryDate && (
                <p className="text-xs text-red-500 mt-1">{errors.expectedDeliveryDate.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Vendor *
              </label>
              <select {...register('vendorId')} className="input-field">
                <option value="">Select Vendor</option>
                {vendors?.data?.map(v => (
                  <option key={v.id} value={v.id}>
                    {v.vendorName} ({v.vendorCode})
                  </option>
                ))}
              </select>
              {errors.vendorId && (
                <p className="text-xs text-red-500 mt-1">{errors.vendorId.message}</p>
              )}
            </div>
          </div>
        </div>

        {/* === Section 2: Vendor Information (read-only) === */}
        {selectedVendor && (
          <div className="card">
            <div className="flex items-center gap-2 mb-4">
              <Building2 size={20} className="text-purple-600" />
              <h2 className="text-lg font-semibold text-gray-900">Vendor Information</h2>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              <div>
                <p className="text-xs text-gray-500">Vendor Name</p>
                <p className="text-sm font-medium text-gray-900">{selectedVendor.vendorName}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Vendor Code</p>
                <p className="text-sm font-medium text-gray-900">{selectedVendor.vendorCode}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Email</p>
                <p className="text-sm font-medium text-gray-900">{selectedVendor.email}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Phone</p>
                <p className="text-sm font-medium text-gray-900">{selectedVendor.phone || '—'}</p>
              </div>
              <div className="md:col-span-2">
                <p className="text-xs text-gray-500">Address</p>
                <p className="text-sm font-medium text-gray-900">
                  {[selectedVendor.address, selectedVendor.city, selectedVendor.state, selectedVendor.country]
                    .filter(Boolean)
                    .join(', ') || '—'}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* === Section 3 & 4: Addresses (side by side on desktop) === */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Ship-To Address */}
          <div className="card">
            <div className="flex items-center gap-2 mb-4">
              <Truck size={20} className="text-green-600" />
              <h2 className="text-lg font-semibold text-gray-900">Ship-To Address</h2>
            </div>

            <div className="mb-3">
              <select {...register('shipToAddressId')} className="input-field">
                <option value="">Select ship-to address</option>
                {shipToAddresses?.map(addr => (
                  <option key={addr.id} value={addr.id}>
                    {addr.companyName} — {addr.city}
                  </option>
                ))}
              </select>
            </div>

            {selectedShipTo && (
              <div className="bg-gray-50 rounded-lg p-3 text-sm space-y-1">
                <p className="font-medium text-gray-900">{selectedShipTo.companyName}</p>
                <p className="text-gray-600">{selectedShipTo.addressLine1}</p>
                {selectedShipTo.addressLine2 && (
                  <p className="text-gray-600">{selectedShipTo.addressLine2}</p>
                )}
                <p className="text-gray-600">
                  {selectedShipTo.city}, {selectedShipTo.state} {selectedShipTo.postalCode}
                </p>
                <p className="text-gray-600">{selectedShipTo.country}</p>
                {selectedShipTo.phone && (
                  <p className="text-gray-500">📞 {selectedShipTo.phone}</p>
                )}
              </div>
            )}
          </div>

          {/* Bill-To Address */}
          <div className="card">
            <div className="flex items-center gap-2 mb-4">
              <MapPin size={20} className="text-orange-600" />
              <h2 className="text-lg font-semibold text-gray-900">Bill-To Address</h2>
            </div>

            <div className="mb-3">
              <select {...register('billToAddressId')} className="input-field">
                <option value="">Select bill-to address</option>
                {billToAddresses?.map(addr => (
                  <option key={addr.id} value={addr.id}>
                    {addr.companyName} — {addr.city}
                  </option>
                ))}
              </select>
            </div>

            {selectedBillTo && (
              <div className="bg-gray-50 rounded-lg p-3 text-sm space-y-1">
                <p className="font-medium text-gray-900">{selectedBillTo.companyName}</p>
                <p className="text-gray-600">{selectedBillTo.addressLine1}</p>
                {selectedBillTo.addressLine2 && (
                  <p className="text-gray-600">{selectedBillTo.addressLine2}</p>
                )}
                <p className="text-gray-600">
                  {selectedBillTo.city}, {selectedBillTo.state} {selectedBillTo.postalCode}
                </p>
                <p className="text-gray-600">{selectedBillTo.country}</p>
                {selectedBillTo.phone && (
                  <p className="text-gray-500">📞 {selectedBillTo.phone}</p>
                )}
              </div>
            )}
          </div>
        </div>

        {/* === Section 5: Line Items === */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Package size={20} className="text-indigo-600" />
            <h2 className="text-lg font-semibold text-gray-900">Line Items</h2>
            {requisitionData && (
              <span className="text-xs text-gray-500 ml-2">
                (from {requisitionData.requisitionNumber})
              </span>
            )}
          </div>

          {fields.length === 0 && (
            <div className="text-center py-8 text-gray-400">
              <Package size={40} className="mx-auto mb-2 opacity-50" />
              <p className="text-sm">Enter a requisition ID above to load line items</p>
            </div>
          )}

          {fields.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-200">
                    <th className="text-left py-2 px-2 font-medium text-gray-600 w-8">#</th>
                    <th className="text-left py-2 px-2 font-medium text-gray-600">Item Name</th>
                    <th className="text-left py-2 px-2 font-medium text-gray-600 hidden md:table-cell">Description</th>
                    <th className="text-right py-2 px-2 font-medium text-gray-600 w-24">Qty</th>
                    <th className="text-right py-2 px-2 font-medium text-gray-600 w-32">Unit Price</th>
                    <th className="text-right py-2 px-2 font-medium text-gray-600 w-32">Line Total</th>
                  </tr>
                </thead>
                <tbody>
                  {fields.map((field, index) => {
                    const qty = Number(watchedValues.items?.[index]?.quantity) || 0
                    const price = Number(watchedValues.items?.[index]?.unitPrice) || 0
                    const lineTotal = qty * price
                    return (
                      <tr key={field.id} className="border-b border-gray-100 hover:bg-gray-50">
                        <td className="py-2 px-2 text-gray-400">{index + 1}</td>
                        <td className="py-2 px-2">
                          <input
                            {...register(`items.${index}.itemName`)}
                            className="input-field text-sm"
                            placeholder="Item name"
                          />
                          {errors.items?.[index]?.itemName && (
                            <p className="text-xs text-red-500">{errors.items[index].itemName.message}</p>
                          )}
                        </td>
                        <td className="py-2 px-2 hidden md:table-cell">
                          <input
                            {...register(`items.${index}.description`)}
                            className="input-field text-sm"
                            placeholder="Description"
                          />
                        </td>
                        <td className="py-2 px-2">
                          <input
                            type="number"
                            {...register(`items.${index}.quantity`)}
                            className="input-field text-sm text-right"
                            min="1"
                          />
                          {errors.items?.[index]?.quantity && (
                            <p className="text-xs text-red-500">{errors.items[index].quantity.message}</p>
                          )}
                        </td>
                        <td className="py-2 px-2">
                          <input
                            type="number"
                            step="0.01"
                            {...register(`items.${index}.unitPrice`)}
                            className="input-field text-sm text-right"
                            min="0.01"
                          />
                          {errors.items?.[index]?.unitPrice && (
                            <p className="text-xs text-red-500">{errors.items[index].unitPrice.message}</p>
                          )}
                        </td>
                        <td className="py-2 px-2 text-right font-medium text-gray-900">
                          ₹{lineTotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
                <tfoot>
                  <tr className="border-t-2 border-gray-200">
                    <td colSpan={5} className="py-2 px-2 text-right font-medium text-gray-700">
                      Subtotal
                    </td>
                    <td className="py-2 px-2 text-right font-semibold text-gray-900">
                      ₹{subtotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}

          {errors.items && !Array.isArray(errors.items) && (
            <p className="text-xs text-red-500 mt-2">{errors.items.message}</p>
          )}
        </div>

        {/* === Section 6: Totals === */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Receipt size={20} className="text-emerald-600" />
            <h2 className="text-lg font-semibold text-gray-900">Order Totals</h2>
          </div>

          <div className="max-w-md ml-auto space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-600">Subtotal</span>
              <span className="text-sm font-medium text-gray-900">
                ₹{subtotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
            </div>

            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-2">
                <span className="text-sm text-gray-600">Tax</span>
                <input
                  type="number"
                  step="0.1"
                  {...register('taxPercent')}
                  className="input-field text-sm w-20 text-right"
                  min="0"
                  max="100"
                />
                <span className="text-xs text-gray-500">%</span>
              </div>
              <span className="text-sm font-medium text-gray-900">
                ₹{taxAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
            </div>

            <div className="flex items-center justify-between gap-4">
              <label className="text-sm text-gray-600">Shipping Cost</label>
              <div className="flex items-center gap-1">
                <span className="text-sm text-gray-500">₹</span>
                <input
                  type="number"
                  step="0.01"
                  {...register('shippingCost')}
                  className="input-field text-sm w-28 text-right"
                  min="0"
                />
              </div>
            </div>

            <div className="border-t-2 border-gray-200 pt-3 flex items-center justify-between">
              <span className="text-base font-bold text-gray-900">Grand Total</span>
              <span className="text-lg font-bold text-gray-900">
                ₹{grandTotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
            </div>
          </div>
        </div>

        {/* === Section 7: Notes === */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <StickyNote size={20} className="text-yellow-600" />
            <h2 className="text-lg font-semibold text-gray-900">Notes & Special Instructions</h2>
          </div>

          <textarea
            {...register('notes')}
            className="input-field min-h-[100px] resize-y"
            placeholder="Enter any special instructions, delivery notes, or terms for the vendor..."
            rows={4}
          />
        </div>

        {/* === Actions === */}
        <div className="flex flex-col sm:flex-row justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="btn-secondary"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={loading || fields.length === 0}
            className="btn-primary"
          >
            {loading ? 'Creating...' : 'Create Purchase Order'}
          </button>
        </div>
      </form>
    </div>
  )
}
