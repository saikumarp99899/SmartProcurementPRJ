import api from './axios'

export const approvalWorkflowApi = {
  getAll: () => api.get('/approval-workflows'),
  getById: (id) => api.get(`/approval-workflows/${id}`),
  create: (data) => api.post('/approval-workflows', data),
  update: (id, data) => api.put(`/approval-workflows/${id}`, data),
  setActive: (id, active) =>
    api.patch(`/approval-workflows/${id}/active`, null, { params: { active } }),
  remove: (id) => api.delete(`/approval-workflows/${id}`),
  // Returns 204 with no body when no workflow matches the amount.
  previewWorkflow: (amount, departmentId) =>
    api.get('/approval-workflows/preview', { params: { amount, departmentId } }),
}
