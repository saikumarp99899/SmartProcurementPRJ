import api from './axios'

export const purchaseOrderApi = {
  getAll: (params) => api.get('/purchase-orders', { params }),
  getMy: (params) => api.get('/purchase-orders/my', { params }),
  getById: (id) => api.get(`/purchase-orders/${id}`),
  create: (data) => api.post('/purchase-orders', data),
  updateStatus: (id, newStatus) => api.patch(`/purchase-orders/${id}/status`, null, { params: { newStatus } }),
  changeOrder: (id, data) => api.put(`/purchase-orders/${id}/change-order`, data),
}
