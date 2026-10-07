import api from './axios'

export const vendorApi = {
  search: (params) => api.get('/vendors', { params }),
  getById: (id) => api.get(`/vendors/${id}`),
  create: (data) => api.post('/vendors', data),
  update: (id, data) => api.put(`/vendors/${id}`, data),
  deactivate: (id) => api.patch(`/vendors/${id}/deactivate`),
}
