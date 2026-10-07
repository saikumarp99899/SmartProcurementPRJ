import api from './axios'

export const approvalApi = {
  getPending: (params) => api.get('/approvals/pending', { params }),
  getById: (id) => api.get(`/approvals/${id}`),
  approve: (id, data) => api.post(`/approvals/${id}/approve`, data),
  reject: (id, data) => api.post(`/approvals/${id}/reject`, data),
}
