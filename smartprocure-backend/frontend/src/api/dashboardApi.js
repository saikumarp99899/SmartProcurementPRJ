import api from './axios'

export const dashboardApi = {
  getAdmin: () => api.get('/dashboard/admin'),
  getBuyer: () => api.get('/dashboard/buyer'),
  getApprover: () => api.get('/dashboard/approver'),
}
