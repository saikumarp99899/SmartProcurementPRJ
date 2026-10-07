import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../../api/dashboardApi'
import { useNavigate } from 'react-router-dom'
import { Users, Store, FileText, CheckCircle, ShoppingCart, DollarSign, ArrowRight } from 'lucide-react'

function StatCard({ title, value, icon: Icon, color, to }) {
  const navigate = useNavigate()
  const colors = {
    blue: 'bg-blue-50 text-blue-600',
    green: 'bg-green-50 text-green-600',
    purple: 'bg-purple-50 text-purple-600',
    orange: 'bg-orange-50 text-orange-600',
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

export default function AdminDashboard() {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', 'admin'],
    queryFn: () => dashboardApi.getAdmin().then(res => res.data),
  })

  if (isLoading) return <div className="text-center py-10">Loading...</div>

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Admin Dashboard</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <StatCard title="Total Users" value={data?.totalUsers} icon={Users} color="blue" to="/users" />
        <StatCard title="Active Vendors" value={data?.activeVendors} icon={Store} color="green" to="/vendors" />
        <StatCard title="Total Requisitions" value={data?.totalRequisitions} icon={FileText} color="purple" to="/requisitions" />
        <StatCard title="Pending Approvals" value={data?.pendingApprovals} icon={CheckCircle} color="orange" to="/approvals" />
        <StatCard title="Purchase Orders" value={data?.totalPurchaseOrders} icon={ShoppingCart} color="indigo" to="/purchase-orders" />
        <StatCard title="Total Spend" value={data?.totalProcurementSpend ? `₹${Number(data.totalProcurementSpend).toLocaleString()}` : '₹0'} icon={DollarSign} color="pink" to="/ai/spend-analysis" />
      </div>
    </div>
  )
}
