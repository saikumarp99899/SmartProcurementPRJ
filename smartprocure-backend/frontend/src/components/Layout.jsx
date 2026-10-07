import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'
import ProxyBanner from './ProxyBanner'
import { useState } from 'react'
import { useAuth } from '../context/AuthContext'

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const { isProxy } = useAuth()

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      {/* Proxy Banner - fixed at top when proxy session is active */}
      <ProxyBanner />

      {/* Sidebar */}
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main Content */}
      <div className={`flex flex-1 flex-col overflow-hidden ${isProxy ? 'pt-10' : ''}`}>
        <Header onMenuClick={() => setSidebarOpen(true)} />
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
