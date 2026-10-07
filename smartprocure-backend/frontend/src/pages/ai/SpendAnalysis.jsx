import { useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/aiApi'
import { TrendingUp, AlertTriangle, Lightbulb, BarChart3 } from 'lucide-react'

export default function SpendAnalysis() {
  const { data, isLoading } = useQuery({
    queryKey: ['spend-analysis'],
    queryFn: () => aiApi.getSpendAnalysis().then(res => res.data),
  })

  if (isLoading) {
    return (
      <div className="text-center py-10">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
        <p className="text-gray-500">Analyzing procurement data...</p>
      </div>
    )
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-primary-100 rounded-lg">
          <BarChart3 className="text-primary-600" size={24} />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">AI Spend Analysis</h1>
          <p className="text-gray-500 text-sm">AI-powered procurement insights and recommendations</p>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
        <div className="card">
          <p className="text-sm text-gray-500">Total Spend</p>
          <p className="text-2xl font-bold text-gray-900">₹{Number(data?.totalSpend || 0).toLocaleString()}</p>
        </div>
        <div className="card">
          <p className="text-sm text-gray-500">Avg Order Value</p>
          <p className="text-2xl font-bold text-gray-900">₹{Number(data?.averageOrderValue || 0).toLocaleString()}</p>
        </div>
        <div className="card">
          <p className="text-sm text-gray-500">Total Orders</p>
          <p className="text-2xl font-bold text-gray-900">{data?.totalOrders || 0}</p>
        </div>
        <div className="card">
          <p className="text-sm text-gray-500">Active Vendors</p>
          <p className="text-2xl font-bold text-gray-900">{data?.activeVendors || 0}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* AI Insights */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Lightbulb className="text-yellow-500" size={20} />
            <h2 className="text-lg font-semibold text-gray-900">AI Insights</h2>
          </div>
          <div className="space-y-3">
            {data?.insights?.map((insight, index) => (
              <div key={index} className="flex gap-3 p-3 bg-blue-50 rounded-lg">
                <TrendingUp className="text-blue-500 flex-shrink-0 mt-0.5" size={16} />
                <p className="text-sm text-gray-700">{insight}</p>
              </div>
            ))}
            {(!data?.insights || data.insights.length === 0) && (
              <p className="text-sm text-gray-500">No insights available. Add more procurement data.</p>
            )}
          </div>
        </div>

        {/* Recommendations */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <AlertTriangle className="text-orange-500" size={20} />
            <h2 className="text-lg font-semibold text-gray-900">Recommendations</h2>
          </div>
          <div className="space-y-3">
            {data?.recommendations?.map((rec, index) => (
              <div key={index} className="flex gap-3 p-3 bg-orange-50 rounded-lg">
                <span className="flex-shrink-0 w-5 h-5 bg-orange-200 text-orange-700 rounded-full flex items-center justify-center text-xs font-bold">
                  {index + 1}
                </span>
                <p className="text-sm text-gray-700">{rec}</p>
              </div>
            ))}
            {(!data?.recommendations || data.recommendations.length === 0) && (
              <p className="text-sm text-gray-500">No recommendations at this time.</p>
            )}
          </div>
        </div>

        {/* Monthly Trend */}
        <div className="card">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Monthly Spend Trend</h2>
          {data?.monthlyTrend?.length > 0 ? (
            <div className="space-y-2">
              {data.monthlyTrend.map((month, index) => {
                const maxAmount = Math.max(...data.monthlyTrend.map(m => Number(m.amount)))
                const widthPercent = maxAmount > 0 ? (Number(month.amount) / maxAmount) * 100 : 0
                return (
                  <div key={index} className="flex items-center gap-3">
                    <span className="text-xs text-gray-500 w-20 flex-shrink-0">{month.month}</span>
                    <div className="flex-1 bg-gray-100 rounded-full h-6 relative">
                      <div
                        className="bg-primary-500 h-6 rounded-full flex items-center justify-end pr-2"
                        style={{ width: `${Math.max(widthPercent, 5)}%` }}
                      >
                        <span className="text-xs text-white font-medium">
                          ₹{Number(month.amount).toLocaleString()}
                        </span>
                      </div>
                    </div>
                    <span className="text-xs text-gray-400 w-16 text-right">{month.orderCount} orders</span>
                  </div>
                )
              })}
            </div>
          ) : (
            <p className="text-sm text-gray-500">No monthly data available yet.</p>
          )}
        </div>

        {/* Top Vendors */}
        <div className="card">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Top Vendors by Spend</h2>
          {data?.topVendors?.length > 0 ? (
            <div className="space-y-3">
              {data.topVendors.map((vendor, index) => (
                <div key={index} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <div>
                    <p className="font-medium text-gray-900">{vendor.vendorName}</p>
                    <p className="text-xs text-gray-500">{vendor.orderCount} orders</p>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-gray-900">₹{Number(vendor.totalSpend).toLocaleString()}</p>
                    <p className="text-xs text-gray-500">{vendor.percentageOfTotal.toFixed(1)}% of total</p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-500">No vendor data available yet.</p>
          )}
        </div>
      </div>
    </div>
  )
}
