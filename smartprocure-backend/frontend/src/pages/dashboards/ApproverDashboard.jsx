import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../../api/dashboardApi'
import { useNavigate } from 'react-router-dom'
import { Clock, CheckCircle, XCircle, ArrowRight } from 'lucide-react'

function StatCard({ title, value, icon: Icon, color, to }) {
  const navigate = useNavigate()
  const colors = {
    orange: 'bg-orange-50 text-orange-600',
    green: 'bg-green-50 text-green-600',
    red: 'bg-red-50 text-red-600',
  }

  const clickable = Boolean(to)

  return (
    <div
      onClick={clickable ? () => navigate(to) : undefined}
      onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); navigate(to) } } : undefined}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      aria-label={clickable ? `View ${title}` : undefined}
      className={`card flex items-center gap-4 group ${
        clickable
          ? 'cursor-pointer transition-all hover:shadow-md hover:border-primary-300 focus:outline-none focus:ring-2 focus:ring-primary-500'
          : ''
      }`}
    >
      <div className={`p-3 rounded-lg ${colors[color]}`}>
        <Icon size={24} />
      </div>
      <div className="flex-1">
        <p className="text-sm text-gray-500">{title}</p>
        <p className="text-2xl font-bold text-gray-900">{value ?? '-'}</p>
      </div>
      {clickable && (
        <ArrowRight size={18} className="text-gray-300 group-hover:text-primary-500 transition-colors" />
      )}
    </div>
  )
}

export default function ApproverDashboard() {
  const navigate = useNavigate()
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', 'approver'],
    queryFn: () => dashboardApi.getApprover().then(res => res.data),
  })

  if (isLoading) return <div className="text-center py-10">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Approver Dashboard</h1>
        <button onClick={() => navigate('/approvals')} className="btn-primary">
          View Pending Approvals
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <StatCard title="Pending Approvals" value={data?.pendingApprovals} icon={Clock} color="orange" to="/approvals" />
        <StatCard title="Approved by Me" value={data?.approvedByMe} icon={CheckCircle} color="green" to="/requisitions?status=APPROVED" />
        <StatCard title="Rejected by Me" value={data?.rejectedByMe} icon={XCircle} color="red" to="/requisitions?status=REJECTED" />
      </div>
    </div>
  )
}
