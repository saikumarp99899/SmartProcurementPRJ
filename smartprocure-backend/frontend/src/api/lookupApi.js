import api from './axios'

export const lookupApi = {
  getDepartments: () => api.get('/lookup/departments'),
  getCostCenters: (departmentId) => api.get('/lookup/cost-centers', { params: { departmentId } }),
  getApprovers: (includeAdmins = false) =>
    api.get('/lookup/approvers', { params: { includeAdmins } }),
}
