import api from './axios'

export const searchApi = {
  search: (q) => api.get('/search', { params: { q } }),
}
