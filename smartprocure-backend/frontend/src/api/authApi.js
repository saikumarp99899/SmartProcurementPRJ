import api from './axios'

export const authApi = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  getCurrentUser: () => api.get('/auth/me'),
  proxyLogin: (targetUserId) => api.post('/auth/proxy-login', { targetUserId }),
  proxyLogout: () => api.post('/auth/proxy-logout'),
}
