import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
  LayoutDashboard,
  Users,
  Store,
  FileText,
  CheckCircle,
  ShoppingCart,
  ClipboardList,
  X,
  BarChart3,
  GitBranch,
  AlertTriangle,
  TrendingUp,
} from 'lucide-react'
import clsx from 'clsx'

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard, roles: ['ROLE_ADMIN', 'ROLE_BUYER', 'ROLE_APPROVER'] },
  { name: 'Users', href: '/users', icon: Users, roles: ['ROLE_ADMIN'] },
  { name: 'Vendors', href: '/vendors', icon: Store, roles: ['ROLE_ADMIN', 'ROLE_BUYER'] },
  { name: 'My Requisitions', href: '/requisitions/my', icon: FileText, roles: ['ROLE_BUYER', 'ROLE_ADMIN'] },
  { name: 'All Requisitions', href: '/requisitions', icon: FileText, roles: ['ROLE_ADMIN', 'ROLE_APPROVER'] },
  { name: 'Approvals', href: '/approvals', icon: CheckCircle, roles: ['ROLE_APPROVER'] },
  { name: 'Workflows', href: '/approvals/workflows', icon: GitBranch, roles: ['ROLE_ADMIN'] },
  { name: 'My POs', href: '/purchase-orders/my', icon: ShoppingCart, roles: ['ROLE_BUYER', 'ROLE_ADMIN'] },
  { name: 'All POs', href: '/purchase-orders', icon: ShoppingCart, roles: ['ROLE_ADMIN', 'ROLE_APPROVER'] },
  { name: 'Audit Logs', href: '/audit-logs', icon: ClipboardList, roles: ['ROLE_ADMIN'] },
  { name: 'AI Spend Analysis', href: '/ai/spend-analysis', icon: BarChart3, roles: ['ROLE_ADMIN', 'ROLE_BUYER'] },
  { name: 'Anomaly Detection', href: '/ai/anomaly-detection', icon: AlertTriangle, roles: ['ROLE_ADMIN'] },
  { name: 'Predictive Analytics', href: '/ai/predictive-analytics', icon: TrendingUp, roles: ['ROLE_ADMIN', 'ROLE_BUYER'] },
]

export default function Sidebar({ open, onClose }) {
  const { user } = useAuth()

  const filteredNav = navigation.filter(item =>
    item.roles.some(role => user?.roles?.includes(role))
  )

  const sidebarContent = (
    <div className="flex h-full flex-col bg-primary-900 text-white">
      {/* Logo */}
      <div className="flex h-16 items-center justify-between px-6 border-b border-primary-800">
        <h1 className="text-xl font-bold">SmartProcure</h1>
        <button onClick={onClose} className="lg:hidden text-white">
          <X size={20} />
        </button>
      </div>

      {/* Nav Links */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {filteredNav.map((item) => (
          <NavLink
            key={item.name}
            to={item.href}
            onClick={onClose}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-primary-700 text-white'
                  : 'text-primary-100 hover:bg-primary-800 hover:text-white'
              )
            }
          >
            <item.icon size={20} />
            {item.name}
          </NavLink>
        ))}
      </nav>

      {/* User Info */}
      <div className="border-t border-primary-800 p-4">
        <div className="text-sm">
          <p className="font-medium">{user?.firstName} {user?.lastName}</p>
          <p className="text-primary-300 text-xs">{user?.roles?.[0]?.replace('ROLE_', '')}</p>
        </div>
      </div>
    </div>
  )

  return (
    <>
      {/* Mobile overlay */}
      {open && (
        <div className="fixed inset-0 z-40 bg-black/50 lg:hidden" onClick={onClose} />
      )}

      {/* Mobile sidebar */}
      <div className={clsx(
        'fixed inset-y-0 left-0 z-50 w-64 transform transition-transform lg:hidden',
        open ? 'translate-x-0' : '-translate-x-full'
      )}>
        {sidebarContent}
      </div>

      {/* Desktop sidebar */}
      <div className="hidden lg:flex lg:w-64 lg:flex-shrink-0">
        {sidebarContent}
      </div>
    </>
  )
}
