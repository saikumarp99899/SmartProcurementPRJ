import api from './axios'

export const addressApi = {
  getAll: () => api.get('/company-addresses'),
  getByType: (type) => api.get('/company-addresses', { params: { type } }),
}
