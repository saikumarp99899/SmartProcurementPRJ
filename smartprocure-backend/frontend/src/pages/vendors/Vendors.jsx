import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { vendorApi } from '../../api/vendorApi'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import toast from 'react-hot-toast'
import { PlusCircle, Search, Ban, Pencil } from 'lucide-react'

export default function Vendors() {
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [editVendor, setEditVendor] = useState(null)
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['vendors', page, keyword],
    queryFn: () => vendorApi.search({ page, size: 10, keyword: keyword || undefined }).then(res => res.data),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id) => vendorApi.deactivate(id),
    onSuccess: () => { toast.success('Vendor deactivated'); queryClient.invalidateQueries(['vendors']) },
  })

  const activateMutation = useMutation({
    mutationFn: (id) => vendorApi.update(id, { status: 'ACTIVE' }),
    onSuccess: () => { toast.success('Vendor activated'); queryClient.invalidateQueries(['vendors']) },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }) => vendorApi.update(id, data),
    onSuccess: () => {
      toast.success('Vendor updated')
      queryClient.invalidateQueries(['vendors'])
      setEditVendor(null)
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Update failed'),
  })

  const handleEditSubmit = (e) => {
    e.preventDefault()
    const formData = new FormData(e.target)
    const data = {
      vendorName: formData.get('vendorName'),
      email: formData.get('email'),
      phone: formData.get('phone'),
      address: formData.get('address'),
      city: formData.get('city'),
      state: formData.get('state'),
      country: formData.get('country'),
    }
    updateMutation.mutate({ id: editVendor.id, data })
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Vendors</h1>
        {hasRole('ROLE_ADMIN') && (
          <button onClick={() => navigate('/vendors/create')} className="btn-primary flex items-center gap-2">
            <PlusCircle size={18} /> Add Vendor
          </button>
        )}
      </div>

      {/* Search */}
      <div className="mb-4 relative max-w-sm">
        <Search className="absolute left-3 top-2.5 text-gray-400" size={18} />
        <input
          type="text"
          placeholder="Search vendors..."
          value={keyword}
          onChange={(e) => { setKeyword(e.target.value); setPage(0) }}
          className="input-field pl-10"
        />
      </div>

      {isLoading ? <p className="text-center py-10">Loading...</p> : (
        <>
          <div className="card overflow-hidden p-0">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Code</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Name</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Email</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">City</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Status</th>
                  {hasRole('ROLE_ADMIN') && <th className="text-left px-6 py-3 font-medium text-gray-500">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y">
                {data?.data?.map((vendor) => (
                  <tr key={vendor.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 font-mono text-xs">{vendor.vendorCode}</td>
                    <td className="px-6 py-4 font-medium">{vendor.vendorName}</td>
                    <td className="px-6 py-4 text-gray-600">{vendor.email}</td>
                    <td className="px-6 py-4 text-gray-600">{vendor.city || '-'}</td>
                    <td className="px-6 py-4">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                        vendor.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                      }`}>
                        {vendor.status}
                      </span>
                    </td>
                    {hasRole('ROLE_ADMIN') && (
                      <td className="px-6 py-4">
                        <button
                          onClick={() => setEditVendor(vendor)}
                          className="p-1.5 rounded-lg text-gray-400 hover:text-primary-600 hover:bg-primary-50 transition-colors"
                          title="Edit vendor"
                        >
                          <Pencil size={16} />
                        </button>
                      </td>
                    )}
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
        </>
      )}

      {/* Edit Vendor Modal */}
      {editVendor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-white rounded-xl p-6 w-full max-w-lg mx-4 shadow-2xl max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">
              Edit Vendor — {editVendor.vendorCode}
            </h3>
            <form onSubmit={handleEditSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Vendor Name *</label>
                <input name="vendorName" defaultValue={editVendor.vendorName} className="input-field" required />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Email *</label>
                  <input name="email" type="email" defaultValue={editVendor.email} className="input-field" required />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Phone</label>
                  <input name="phone" defaultValue={editVendor.phone || ''} className="input-field" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Address</label>
                <input name="address" defaultValue={editVendor.address || ''} className="input-field" />
              </div>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">City</label>
                  <input name="city" defaultValue={editVendor.city || ''} className="input-field" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">State</label>
                  <input name="state" defaultValue={editVendor.state || ''} className="input-field" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Country</label>
                  <input name="country" defaultValue={editVendor.country || ''} className="input-field" />
                </div>
              </div>

              {/* Status toggle */}
              <div className="border-t pt-4 mt-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-700">Vendor Status</p>
                    <p className="text-xs text-gray-500">
                      {editVendor.status === 'ACTIVE'
                        ? 'This vendor is currently active and can receive POs.'
                        : 'This vendor is inactive and cannot receive new POs.'}
                    </p>
                  </div>
                  {editVendor.status === 'ACTIVE' ? (
                    <button
                      type="button"
                      onClick={() => {
                        deactivateMutation.mutate(editVendor.id)
                        setEditVendor(null)
                      }}
                      className="px-3 py-1.5 text-xs font-medium text-red-700 bg-red-50 border border-red-200 rounded-lg hover:bg-red-100 transition-colors"
                    >
                      Deactivate
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        activateMutation.mutate(editVendor.id)
                        setEditVendor(null)
                      }}
                      className="px-3 py-1.5 text-xs font-medium text-green-700 bg-green-50 border border-green-200 rounded-lg hover:bg-green-100 transition-colors"
                    >
                      Activate
                    </button>
                  )}
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button type="button" onClick={() => setEditVendor(null)} className="btn-secondary">Cancel</button>
                <button type="submit" disabled={updateMutation.isPending} className="btn-primary">
                  {updateMutation.isPending ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
