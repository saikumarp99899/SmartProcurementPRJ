import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import Layout from './components/Layout'
import Login from './pages/Login'
import Register from './pages/Register'
import AdminDashboard from './pages/dashboards/AdminDashboard'
import BuyerDashboard from './pages/dashboards/BuyerDashboard'
import ApproverDashboard from './pages/dashboards/ApproverDashboard'
import Users from './pages/users/Users'
import Vendors from './pages/vendors/Vendors'
import CreateVendor from './pages/vendors/CreateVendor'
import Requisitions from './pages/requisitions/Requisitions'
import MyRequisitions from './pages/requisitions/MyRequisitions'
import CreateRequisition from './pages/requisitions/CreateRequisition'
import RequisitionDetail from './pages/requisitions/RequisitionDetail'
import PendingApprovals from './pages/approvals/PendingApprovals'
import ApprovalWorkflows from './pages/approvals/ApprovalWorkflows'
import PurchaseOrders from './pages/purchaseOrders/PurchaseOrders'
import MyPurchaseOrders from './pages/purchaseOrders/MyPurchaseOrders'
import CreatePurchaseOrder from './pages/purchaseOrders/CreatePurchaseOrder'
import AuditLogs from './pages/audit/AuditLogs'
import SpendAnalysis from './pages/ai/SpendAnalysis'
import PredictiveAnalytics from './pages/ai/PredictiveAnalytics'
import AnomalyDetection from './pages/ai/AnomalyDetection'
import PurchaseOrderDetail from './pages/purchaseOrders/PurchaseOrderDetail'

function ProtectedRoute({ children, roles }) {
  const { user, isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (roles && !roles.some(role => user.roles.includes(role))) {
    return <Navigate to="/dashboard" replace />
  }

  return children
}

function DashboardRedirect() {
  const { user } = useAuth()

  if (user?.roles?.includes('ROLE_ADMIN')) return <Navigate to="/dashboard/admin" replace />
  if (user?.roles?.includes('ROLE_APPROVER')) return <Navigate to="/dashboard/approver" replace />
  if (user?.roles?.includes('ROLE_BUYER')) return <Navigate to="/dashboard/buyer" replace />
  return <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      {/* Protected */}
      <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route index element={<DashboardRedirect />} />
        <Route path="dashboard" element={<DashboardRedirect />} />
        <Route path="dashboard/admin" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AdminDashboard /></ProtectedRoute>} />
        <Route path="dashboard/buyer" element={<ProtectedRoute roles={['ROLE_BUYER', 'ROLE_ADMIN']}><BuyerDashboard /></ProtectedRoute>} />
        <Route path="dashboard/approver" element={<ProtectedRoute roles={['ROLE_APPROVER', 'ROLE_ADMIN']}><ApproverDashboard /></ProtectedRoute>} />

        {/* Users - Admin only */}
        <Route path="users" element={<ProtectedRoute roles={['ROLE_ADMIN']}><Users /></ProtectedRoute>} />

        {/* Vendors */}
        <Route path="vendors" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_BUYER']}><Vendors /></ProtectedRoute>} />
        <Route path="vendors/create" element={<ProtectedRoute roles={['ROLE_ADMIN']}><CreateVendor /></ProtectedRoute>} />

        {/* Requisitions */}
        <Route path="requisitions" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_APPROVER']}><Requisitions /></ProtectedRoute>} />
        <Route path="requisitions/my" element={<ProtectedRoute roles={['ROLE_BUYER', 'ROLE_ADMIN']}><MyRequisitions /></ProtectedRoute>} />
        <Route path="requisitions/create" element={<ProtectedRoute roles={['ROLE_BUYER', 'ROLE_ADMIN']}><CreateRequisition /></ProtectedRoute>} />
        <Route path="requisitions/:id" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_BUYER', 'ROLE_APPROVER']}><RequisitionDetail /></ProtectedRoute>} />

        {/* Approvals */}
        <Route path="approvals" element={<ProtectedRoute roles={['ROLE_APPROVER', 'ROLE_ADMIN']}><PendingApprovals /></ProtectedRoute>} />
        <Route path="approvals/workflows" element={<ProtectedRoute roles={['ROLE_ADMIN']}><ApprovalWorkflows /></ProtectedRoute>} />

        {/* Purchase Orders */}
        <Route path="purchase-orders" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_APPROVER']}><PurchaseOrders /></ProtectedRoute>} />
        <Route path="purchase-orders/my" element={<ProtectedRoute roles={['ROLE_BUYER', 'ROLE_ADMIN']}><MyPurchaseOrders /></ProtectedRoute>} />
        <Route path="purchase-orders/create" element={<ProtectedRoute roles={['ROLE_BUYER', 'ROLE_ADMIN']}><CreatePurchaseOrder /></ProtectedRoute>} />
        <Route path="purchase-orders/:id" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_BUYER', 'ROLE_APPROVER']}><PurchaseOrderDetail /></ProtectedRoute>} />

        {/* Audit Logs */}
        <Route path="audit-logs" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AuditLogs /></ProtectedRoute>} />

        {/* AI Analytics */}
        <Route path="ai/spend-analysis" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_BUYER']}><SpendAnalysis /></ProtectedRoute>} />
        <Route path="ai/predictive-analytics" element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_BUYER']}><PredictiveAnalytics /></ProtectedRoute>} />
        <Route path="ai/anomaly-detection" element={<ProtectedRoute roles={['ROLE_ADMIN']}><AnomalyDetection /></ProtectedRoute>} />
      </Route>

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}
