import { useAuth } from '../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import { Menu, LogOut, User, ClipboardList } from 'lucide-react'
import GlobalSearchBar from './GlobalSearchBar'

export default function Header({ onMenuClick }) {
  const { user, logout, hasRole } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="bg-white border-b border-gray-200 h-16 flex items-center justify-between px-6 flex-shrink-0">
      <div className="flex items-center gap-4">
        <button onClick={onMenuClick} className="lg:hidden text-gray-600 hover:text-gray-900" aria-label="Open menu">
          <Menu size={24} />
        </button>
        <h2 className="text-lg font-semibold text-gray-800 hidden sm:block">
          Welcome, {user?.firstName}
        </h2>
      </div>

      <GlobalSearchBar />

      <div className="flex items-center gap-4">
        {hasRole('ROLE_ADMIN') && (
          <button
            onClick={() => navigate('/audit-logs')}
            className="relative p-2 text-gray-500 hover:text-primary-600 hover:bg-gray-100 rounded-lg transition-colors"
            title="Audit Logs"
            aria-label="View audit logs"
          >
            <ClipboardList size={20} />
          </button>
        )}

        <div className="flex items-center gap-2 text-sm text-gray-600">
          <User size={16} />
          <span className="hidden sm:inline">{user?.email}</span>
        </div>

        <button
          onClick={handleLogout}
          className="flex items-center gap-2 text-sm text-red-600 hover:text-red-700 font-medium"
        >
          <LogOut size={16} />
          <span className="hidden sm:inline">Logout</span>
        </button>
      </div>
    </header>
  )
}
