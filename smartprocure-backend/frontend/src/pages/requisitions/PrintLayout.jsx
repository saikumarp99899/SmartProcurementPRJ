/**
 * Print-optimized layout for requisition detail.
 * Includes header, line items table, and approval history.
 * No navigation or action elements.
 * Formatted for A4 / Letter page dimensions.
 */
export default function PrintLayout({ requisition, approvals }) {
  const totalSum = (requisition.items || []).reduce(
    (sum, item) => sum + (Number(item.totalPrice) || 0),
    0
  )

  return (
    <div className="print-layout p-8 max-w-[210mm] mx-auto font-sans text-sm text-black bg-white">
      {/* Header */}
      <div className="border-b-2 border-gray-800 pb-4 mb-6">
        <div className="flex justify-between items-start">
          <div>
            <h1 className="text-2xl font-bold">{requisition.requisitionNumber}</h1>
            <p className="text-gray-600 mt-1">Requester: {requisition.requesterName}</p>
          </div>
          <div className="text-right">
            <p className="font-semibold text-lg">
              Status: {requisition.status}
            </p>
            <p className="text-gray-600">
              Date: {new Date(requisition.createdAt).toLocaleDateString()}
            </p>
          </div>
        </div>
        <div className="grid grid-cols-4 gap-4 mt-4 text-xs">
          <div>
            <span className="text-gray-500">Department:</span>
            <span className="ml-1 font-medium">{requisition.departmentName || '—'}</span>
          </div>
          <div>
            <span className="text-gray-500">Cost Center:</span>
            <span className="ml-1 font-medium">{requisition.costCenterName || '—'}</span>
          </div>
          <div>
            <span className="text-gray-500">Total:</span>
            <span className="ml-1 font-bold">₹{Number(requisition.totalAmount).toLocaleString()}</span>
          </div>
          <div>
            <span className="text-gray-500">Items:</span>
            <span className="ml-1 font-medium">{requisition.items?.length || 0}</span>
          </div>
        </div>
      </div>

      {/* Line Items Table */}
      <div className="mb-6">
        <h2 className="text-base font-bold mb-3">Line Items</h2>
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr className="border-b-2 border-gray-400">
              <th className="text-left py-2 pr-2">#</th>
              <th className="text-left py-2 pr-2">Item Name</th>
              <th className="text-right py-2 pr-2">Qty</th>
              <th className="text-right py-2 pr-2">Unit Price</th>
              <th className="text-right py-2">Line Total</th>
            </tr>
          </thead>
          <tbody>
            {(requisition.items || []).map((item, idx) => (
              <tr key={item.id || idx} className="border-b border-gray-200">
                <td className="py-1.5 pr-2">{idx + 1}</td>
                <td className="py-1.5 pr-2">{item.itemName}</td>
                <td className="py-1.5 pr-2 text-right">{item.quantity}</td>
                <td className="py-1.5 pr-2 text-right">₹{Number(item.unitPrice).toLocaleString()}</td>
                <td className="py-1.5 text-right font-medium">₹{Number(item.totalPrice).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-gray-400">
              <td colSpan={4} className="py-2 text-right font-semibold">Total:</td>
              <td className="py-2 text-right font-bold">₹{totalSum.toLocaleString()}</td>
            </tr>
          </tfoot>
        </table>
      </div>

      {/* Approval History */}
      <div>
        <h2 className="text-base font-bold mb-3">Approval History</h2>
        {approvals && approvals.length > 0 ? (
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr className="border-b-2 border-gray-400">
                <th className="text-left py-2 pr-2">Level</th>
                <th className="text-left py-2 pr-2">Approver</th>
                <th className="text-left py-2 pr-2">Action</th>
                <th className="text-left py-2 pr-2">Date</th>
                <th className="text-left py-2">Comments</th>
              </tr>
            </thead>
            <tbody>
              {approvals.map((a, idx) => (
                <tr key={a.id || idx} className="border-b border-gray-200">
                  <td className="py-1.5 pr-2">{a.approvalLevel}</td>
                  <td className="py-1.5 pr-2">
                    {a.approverName || a.assignedApproverName || '—'}
                  </td>
                  <td className="py-1.5 pr-2">{a.status}</td>
                  <td className="py-1.5 pr-2">
                    {a.approvedAt ? new Date(a.approvedAt).toLocaleString() : '—'}
                  </td>
                  <td className="py-1.5">{a.comments || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="text-gray-500">No approval actions recorded.</p>
        )}
      </div>

      {/* Footer */}
      <div className="mt-8 pt-4 border-t border-gray-300 text-xs text-gray-500 text-center">
        Printed on {new Date().toLocaleString()} • SmartProcure
      </div>
    </div>
  )
}
