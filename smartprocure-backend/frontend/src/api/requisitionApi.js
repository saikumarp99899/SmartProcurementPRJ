import api from './axios'

export const requisitionApi = {
  getAll: (params) => api.get('/requisitions', { params }),
  getMy: (params) => api.get('/requisitions/my', { params }),
  getById: (id) => api.get(`/requisitions/${id}`),
  create: (data) => api.post('/requisitions', data),
  submit: (id) => api.post(`/requisitions/${id}/submit`),
  getApprovals: (id) => api.get(`/requisitions/${id}/approvals`),
}
