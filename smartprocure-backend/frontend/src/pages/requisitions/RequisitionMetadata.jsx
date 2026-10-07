import { Building2, Wallet, DollarSign, Package } from 'lucide-react'

/**
 * Metadata grid displaying department, cost center, total amount, and item count.
 */
export default function RequisitionMetadata({ requisition }) {
  const items = [
    {
      icon: Building2,
      label: 'Department',
      value: requisition.departmentName || '—',
    },
    {
      icon: Wallet,
      label: 'Cost Center',
      value: requisition.costCenterName || '—',
    },
    {
      icon: DollarSign,
      label: 'Total Amount',
      value: `₹${Number(requisition.totalAmount).toLocaleString()}`,
      highlight: true,
    },
    {
      icon: Package,
      label: 'Item Count',
      value: requisition.items?.length || 0,
    },
  ]

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
      {items.map(({ icon: Icon, label, value, highlight }) => (
        <div
          key={label}
          className="p-4 bg-gray-50 rounded-lg border border-gray-100"
        >
          <div className="flex items-center gap-2 mb-1">
            <Icon size={14} className="text-gray-400" />
            <span className="text-xs text-gray-500 uppercase tracking-wider">{label}</span>
          </div>
          <p className={`font-semibold ${highlight ? 'text-lg text-indigo-700' : 'text-gray-900'}`}>
            {value}
          </p>
        </div>
      ))}
    </div>
  )
}
