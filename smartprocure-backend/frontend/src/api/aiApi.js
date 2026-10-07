import api from './axios'

export const aiApi = {
  getSpendAnalysis: () => api.get('/ai/spend-analysis'),
  getVendorRecommendations: (itemName) => api.get('/ai/vendor-recommendations', { params: { itemName } }),
  getAnomalies: () => api.get('/ai/anomalies'),
  getSpendForecast: () => api.get('/ai/predictions/spend-forecast'),
  getDemandForecast: () => api.get('/ai/predictions/demand'),
  getItemSuggestions: (prefix) => api.get('/ai/item-suggestions', { params: { prefix } }),
  getPriceSuggestion: (itemName) => api.get('/ai/price-suggestion', { params: { itemName } }),
}
