import api from './axios'

export const auditApi = {
  getAll: (params) => api.get('/audit-logs', { params }),
  getByEntity: (entityType, entityId, params) => api.get(`/audit-logs/entity/${entityType}/${entityId}`, { params }),
}
