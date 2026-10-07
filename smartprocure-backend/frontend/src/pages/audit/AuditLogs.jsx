import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { auditApi } from '../../api/auditApi'
import { ClipboardList } from 'lucide-react'

export default function AuditLogs() {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['auditLogs', page],
    queryFn: () => auditApi.getAll({ page, size: 20 }).then(res => res.data),
  })

  if (isLoading) return <div className="text-center py-10">Loading...</div>

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <ClipboardList className="text-primary-600" size={24} />
        <h1 className="text-2xl font-bold text-gray-900">Audit Logs</h1>
      </div>

      <div className="card overflow-hidden p-0">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Timestamp</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">User</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Action</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Entity</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Description</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {data?.data?.map((log) => (
              <tr key={log.id} className="hover:bg-gray-50">
                <td className="px-6 py-3 text-gray-500 text-xs whitespace-nowrap">
                  {new Date(log.timestamp).toLocaleString()}
                </td>
                <td className="px-6 py-3">{log.userFullName}</td>
                <td className="px-6 py-3">
                  <span className="px-2 py-0.5 bg-primary-50 text-primary-700 rounded text-xs font-medium">
                    {log.action}
                  </span>
                </td>
                <td className="px-6 py-3 text-gray-600 text-xs">
                  {log.entityType} #{log.entityId}
                </td>
                <td className="px-6 py-3 text-gray-600 max-w-xs truncate">{log.description}</td>
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
    </div>
  )
}
