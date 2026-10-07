import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../../api/dashboardApi'
import { useNavigate } from 'react-router-dom'
import { FileText, Clock, CheckCircle, XCircle, DollarSign, PlusCircle, ArrowRight } from 'lucide-react'

function StatCard({ title, value, icon: Icon, color, to }) {
  const navigate = useNavigate()
  const colors = {
    blue: 'bg-blue-50 text-blue-600',
    yellow: 'bg-yellow-50 text-yellow-600',
    green: 'bg-green-50 text-green-600',
    red: 'bg-red-50 text-red-600',
    indigo: 'bg-indigo-50 text-indigo-600',
    pink: 'bg-pink-50 text-pink-600',
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

export default function BuyerDashboard() {
  const navigate = useNavigate()
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', 'buyer'],
    queryFn: () => dashboardApi.getBuyer().then(res => res.data),
  })

  if (isLoading) return <div className="text-center py-10">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Buyer Dashboard</h1>
        <button onClick={() => navigate('/requisitions/create')} className="btn-primary flex items-center gap-2">
          <PlusCircle size={18} />
          New Requisition
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <StatCard title="Total Requisitions" value={data?.totalMyRequisitions} icon={FileText} color="blue" to="/requisitions/my" />
        <StatCard title="Draft" value={data?.draftRequisitions} icon={FileText} color="blue" to="/requisitions/my?status=DRAFT" />
        <StatCard title="Pending Approval" value={data?.pendingRequisitions} icon={Clock} color="yellow" to="/requisitions/my?status=SUBMITTED" />
        <StatCard title="Approved" value={data?.approvedRequisitions} icon={CheckCircle} color="green" to="/requisitions/my?status=APPROVED" />
        <StatCard title="Rejected" value={data?.rejectedRequisitions} icon={XCircle} color="red" to="/requisitions/my?status=REJECTED" />
        <StatCard title="My Total Spend" value={data?.myTotalSpend ? `₹${Number(data.myTotalSpend).toLocaleString()}` : '₹0'} icon={DollarSign} color="pink" to="/purchase-orders/my" />
      </div>
    </div>
  )
}
