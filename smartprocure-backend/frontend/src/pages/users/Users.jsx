import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { userApi } from '../../api/userApi'
import toast from 'react-hot-toast'
import { UserCheck, UserX, Shield, UserPlus, X } from 'lucide-react'

function CreateUserModal({ onClose, onSuccess }) {
  const [form, setForm] = useState({
    firstName: '', lastName: '', email: '', password: '', role: 'BUYER'
  })
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      await userApi.create(form)
      toast.success('User created successfully')
      onSuccess()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create user')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-gray-900">Create New User</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">First Name</label>
              <input
                type="text"
                value={form.firstName}
                onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                className="input-field"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
              <input
                type="text"
                value={form.lastName}
                onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                className="input-field"
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              className="input-field"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
            <input
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              className="input-field"
              placeholder="Min 8 characters"
              minLength={8}
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <select
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value })}
              className="input-field"
            >
              <option value="BUYER">Buyer</option>
              <option value="APPROVER">Approver</option>
              <option value="ADMIN">Admin</option>
            </select>
          </div>

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary flex-1">Cancel</button>
            <button type="submit" disabled={loading} className="btn-primary flex-1">
              {loading ? 'Creating...' : 'Create User'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function Users() {
  const [page, setPage] = useState(0)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['users', page],
    queryFn: () => userApi.getAll({ page, size: 10 }).then(res => res.data),
  })

  const activateMutation = useMutation({
    mutationFn: (id) => userApi.activate(id),
    onSuccess: () => { toast.success('User activated'); queryClient.invalidateQueries(['users']) },
  })

  const deactivateMutation = useMutation({
    mutationFn: (id) => userApi.deactivate(id),
    onSuccess: () => { toast.success('User deactivated'); queryClient.invalidateQueries(['users']) },
  })

  const setManagerMutation = useMutation({
    mutationFn: ({ id, managerId }) => userApi.update(id, { managerId }),
    onSuccess: () => {
      toast.success('Reporting manager updated')
      queryClient.invalidateQueries(['users'])
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Could not update manager'),
  })

  if (isLoading) return <div className="text-center py-10">Loading...</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">User Management</h1>
        <button onClick={() => setShowCreateModal(true)} className="btn-primary flex items-center gap-2">
          <UserPlus size={18} /> Create User
        </button>
      </div>

      <div className="card overflow-hidden p-0">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Name</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Email</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Roles</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Reports To</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {data?.content?.map((user) => (
              <tr key={user.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 font-medium">{user.firstName} {user.lastName}</td>
                <td className="px-6 py-4 text-gray-600">{user.email}</td>
                <td className="px-6 py-4">
                  <div className="flex gap-1">
                    {user.roles?.map(role => (
                      <span key={role} className="inline-flex items-center gap-1 px-2 py-0.5 bg-primary-50 text-primary-700 text-xs font-medium rounded-full">
                        <Shield size={10} />{role}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="px-6 py-4">
                  {/* Drives REQUESTER_MANAGER approval steps. */}
                  <select
                    value={user.managerId ?? ''}
                    onChange={(e) =>
                      setManagerMutation.mutate({
                        id: user.id,
                        // 0 clears the manager; null would mean "unchanged".
                        managerId: e.target.value === '' ? 0 : Number(e.target.value),
                      })
                    }
                    className="text-xs border border-gray-200 rounded-md px-2 py-1 bg-white max-w-[160px]"
                    aria-label={`Reporting manager for ${user.firstName} ${user.lastName}`}
                  >
                    <option value="">— None —</option>
                    {data?.content
                      ?.filter(candidate => candidate.id !== user.id)
                      .map(candidate => (
                        <option key={candidate.id} value={candidate.id}>
                          {candidate.firstName} {candidate.lastName}
                        </option>
                      ))}
                  </select>
                </td>
                <td className="px-6 py-4">
                  <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                    user.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                  }`}>
                    {user.status}
                  </span>
                </td>
                <td className="px-6 py-4">
                  {user.status === 'ACTIVE' ? (
                    <button
                      onClick={() => deactivateMutation.mutate(user.id)}
                      className="text-red-600 hover:text-red-700 text-xs font-medium flex items-center gap-1"
                    >
                      <UserX size={14} /> Deactivate
                    </button>
                  ) : (
                    <button
                      onClick={() => activateMutation.mutate(user.id)}
                      className="text-green-600 hover:text-green-700 text-xs font-medium flex items-center gap-1"
                    >
                      <UserCheck size={14} /> Activate
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="flex justify-between items-center mt-4">
        <p className="text-sm text-gray-500">
          Page {page + 1} of {data?.totalPages || 1}
        </p>
        <div className="flex gap-2">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="btn-secondary text-sm">Previous</button>
          <button onClick={() => setPage(p => p + 1)} disabled={data?.last} className="btn-secondary text-sm">Next</button>
        </div>
      </div>

      {/* Create User Modal */}
      {showCreateModal && (
        <CreateUserModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false)
            queryClient.invalidateQueries(['users'])
          }}
        />
      )}
    </div>
  )
}
