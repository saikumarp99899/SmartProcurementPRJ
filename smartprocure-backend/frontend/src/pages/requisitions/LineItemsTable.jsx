import { useState, useMemo } from 'react'
import { ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react'

/**
 * Sortable line items table with alternating row backgrounds.
 * Columns: Item Name, Quantity, Unit Price, Line Total
 * Footer displays sum of all line totals.
 */
export default function LineItemsTable({ items = [] }) {
  const [sortColumn, setSortColumn] = useState(null)
  const [sortDirection, setSortDirection] = useState('asc')

  const handleSort = (column) => {
    if (sortColumn === column) {
      setSortDirection(prev => (prev === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortColumn(column)
      setSortDirection('asc')
    }
  }

  const sortedItems = useMemo(() => {
    if (!sortColumn) return items

    return [...items].sort((a, b) => {
      let aVal, bVal

      switch (sortColumn) {
        case 'itemName':
          aVal = (a.itemName || '').toLowerCase()
          bVal = (b.itemName || '').toLowerCase()
          break
        case 'quantity':
          aVal = Number(a.quantity) || 0
          bVal = Number(b.quantity) || 0
          break
        case 'unitPrice':
          aVal = Number(a.unitPrice) || 0
          bVal = Number(b.unitPrice) || 0
          break
        case 'totalPrice':
          aVal = Number(a.totalPrice) || 0
          bVal = Number(b.totalPrice) || 0
          break
        default:
          return 0
      }

      if (aVal < bVal) return sortDirection === 'asc' ? -1 : 1
      if (aVal > bVal) return sortDirection === 'asc' ? 1 : -1
      return 0
    })
  }, [items, sortColumn, sortDirection])

  const totalSum = useMemo(
    () => items.reduce((sum, item) => sum + (Number(item.totalPrice) || 0), 0),
    [items]
  )

  const SortIcon = ({ column }) => {
    if (sortColumn !== column) return <ArrowUpDown size={14} className="text-gray-400" />
    return sortDirection === 'asc'
      ? <ArrowUp size={14} className="text-indigo-600" />
      : <ArrowDown size={14} className="text-indigo-600" />
  }

  const sortableHeader = (label, column, align = 'left') => (
    <th
      className={`px-4 py-3 text-${align} text-xs font-semibold text-gray-600 uppercase tracking-wider cursor-pointer hover:bg-gray-100 select-none`}
      onClick={() => handleSort(column)}
      aria-sort={sortColumn === column ? (sortDirection === 'asc' ? 'ascending' : 'descending') : 'none'}
    >
      <div className={`flex items-center gap-1 ${align === 'right' ? 'justify-end' : ''}`}>
        {label}
        <SortIcon column={column} />
      </div>
    </th>
  )

  if (!items.length) {
    return (
      <div className="p-6 text-center border border-dashed border-gray-300 rounded-lg">
        <p className="text-sm text-gray-500">No line items.</p>
      </div>
    )
  }

  return (
    <div className="overflow-hidden border border-gray-200 rounded-lg">
      <table className="w-full text-sm">
        <thead className="bg-gray-50 border-b border-gray-200">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider w-10">
              #
            </th>
            {sortableHeader('Item Name', 'itemName')}
            {sortableHeader('Quantity', 'quantity', 'right')}
            {sortableHeader('Unit Price', 'unitPrice', 'right')}
            {sortableHeader('Line Total', 'totalPrice', 'right')}
          </tr>
        </thead>
        <tbody>
          {sortedItems.map((item, idx) => (
            <tr
              key={item.id || idx}
              className={idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'}
            >
              <td className="px-4 py-3 text-gray-500">{idx + 1}</td>
              <td className="px-4 py-3 font-medium text-gray-900">{item.itemName}</td>
              <td className="px-4 py-3 text-right text-gray-700">{item.quantity}</td>
              <td className="px-4 py-3 text-right text-gray-700">
                ₹{Number(item.unitPrice).toLocaleString()}
              </td>
              <td className="px-4 py-3 text-right font-medium text-gray-900">
                ₹{Number(item.totalPrice).toLocaleString()}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot className="border-t-2 border-gray-300 bg-gray-50">
          <tr>
            <td colSpan={4} className="px-4 py-3 text-right font-semibold text-gray-700">
              Total
            </td>
            <td className="px-4 py-3 text-right font-bold text-gray-900 text-base">
              ₹{totalSum.toLocaleString()}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
