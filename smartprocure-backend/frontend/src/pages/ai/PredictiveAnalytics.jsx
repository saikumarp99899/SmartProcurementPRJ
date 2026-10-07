import { useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/aiApi'
import { TrendingUp, AlertTriangle, Database } from 'lucide-react'
import {
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Area,
  ComposedChart,
  ResponsiveContainer,
  Legend,
} from 'recharts'

export default function PredictiveAnalytics() {
  const {
    data: spendData,
    isLoading: spendLoading,
    error: spendError,
  } = useQuery({
    queryKey: ['spend-forecast'],
    queryFn: () => aiApi.getSpendForecast().then(res => res.data),
  })

  const {
    data: demandData,
    isLoading: demandLoading,
    error: demandError,
  } = useQuery({
    queryKey: ['demand-forecast'],
    queryFn: () => aiApi.getDemandForecast().then(res => res.data),
  })

  const isLoading = spendLoading || demandLoading

  if (isLoading) {
    return (
      <div className="text-center py-10">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
        <p className="text-gray-500">Loading predictive analytics...</p>
      </div>
    )
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-primary-100 rounded-lg">
          <TrendingUp className="text-primary-600" size={24} />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Predictive Analytics</h1>
          <p className="text-gray-500 text-sm">Spend forecasts and demand predictions based on historical trends</p>
        </div>
      </div>

      {/* Spend Forecast Section */}
      <SpendForecastSection data={spendData} error={spendError} />

      {/* Demand Forecast Section */}
      <DemandForecastSection data={demandData} error={demandError} />
    </div>
  )
}

function SpendForecastSection({ data, error }) {
  if (error) {
    return (
      <div className="card mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Spend Forecast</h2>
        <div className="flex items-center gap-2 text-red-600 bg-red-50 p-4 rounded-lg">
          <AlertTriangle size={18} />
          <p className="text-sm">Failed to load spend forecast data. Please try again later.</p>
        </div>
      </div>
    )
  }

  const chartData = buildSpendChartData(data)

  return (
    <div className="card mb-6">
      <div className="flex items-center gap-2 mb-4">
        <TrendingUp className="text-primary-600" size={20} />
        <h2 className="text-lg font-semibold text-gray-900">Spend Forecast</h2>
        {data?.forecasts?.some(f => f.lowConfidence) && (
          <LowConfidenceIndicator />
        )}
      </div>

      {chartData.length > 0 ? (
        <ResponsiveContainer width="100%" height={350}>
          <ComposedChart data={chartData} margin={{ top: 10, right: 30, left: 20, bottom: 10 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="month" tick={{ fontSize: 12 }} />
            <YAxis tick={{ fontSize: 12 }} tickFormatter={val => `₹${(val / 1000).toFixed(0)}k`} />
            <Tooltip content={<SpendTooltip />} />
            <Legend />
            {/* Confidence interval band */}
            <Area
              type="monotone"
              dataKey="upperBound"
              stroke="none"
              fill="#dbeafe"
              fillOpacity={0.6}
              name="Upper Bound"
              legendType="none"
            />
            <Area
              type="monotone"
              dataKey="lowerBound"
              stroke="none"
              fill="#ffffff"
              fillOpacity={1}
              name="Lower Bound"
              legendType="none"
            />
            {/* Historical line */}
            <Line
              type="monotone"
              dataKey="actualSpend"
              stroke="#1e40af"
              strokeWidth={2}
              dot={{ r: 4 }}
              name="Historical Spend"
              connectNulls={false}
            />
            {/* Forecast line */}
            <Line
              type="monotone"
              dataKey="predictedSpend"
              stroke="#7c3aed"
              strokeWidth={2}
              strokeDasharray="5 5"
              dot={{ r: 4 }}
              name="Forecast"
              connectNulls={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      ) : (
        <p className="text-sm text-gray-500 text-center py-8">No spend forecast data available.</p>
      )}
    </div>
  )
}

function DemandForecastSection({ data, error }) {
  if (error) {
    return (
      <div className="card mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Demand Forecast</h2>
        <div className="flex items-center gap-2 text-red-600 bg-red-50 p-4 rounded-lg">
          <AlertTriangle size={18} />
          <p className="text-sm">Failed to load demand forecast data. Please try again later.</p>
        </div>
      </div>
    )
  }

  const categories = data?.categories || []

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <Database className="text-primary-600" size={20} />
        <h2 className="text-lg font-semibold text-gray-900">Demand Forecast by Category</h2>
      </div>

      {categories.length === 0 ? (
        <p className="text-sm text-gray-500 text-center py-8">No demand forecast data available.</p>
      ) : (
        <div className="space-y-6">
          {categories.map((category, idx) => (
            <CategoryDemandChart key={idx} category={category} />
          ))}
        </div>
      )}
    </div>
  )
}

function CategoryDemandChart({ category }) {
  if (category.insufficientData) {
    return (
      <div className="border border-gray-200 rounded-lg p-4">
        <h3 className="text-md font-medium text-gray-900 mb-2">{category.categoryName}</h3>
        <div className="flex items-center gap-2 text-amber-600 bg-amber-50 p-3 rounded-lg">
          <AlertTriangle size={16} />
          <p className="text-sm">Not enough data to generate a forecast for this category.</p>
        </div>
      </div>
    )
  }

  const chartData = buildDemandChartData(category)
  const hasLowConfidence = category.forecasts?.some(f => f.lowConfidence)

  return (
    <div className="border border-gray-200 rounded-lg p-4">
      <div className="flex items-center gap-2 mb-3">
        <h3 className="text-md font-medium text-gray-900">{category.categoryName}</h3>
        {hasLowConfidence && <LowConfidenceIndicator />}
      </div>

      {chartData.length > 0 ? (
        <ResponsiveContainer width="100%" height={250}>
          <ComposedChart data={chartData} margin={{ top: 10, right: 30, left: 20, bottom: 10 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="month" tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} tickFormatter={val => `₹${(val / 1000).toFixed(0)}k`} />
            <Tooltip content={<DemandTooltip />} />
            <Legend />
            {/* Confidence interval band */}
            <Area
              type="monotone"
              dataKey="upperBound"
              stroke="none"
              fill="#ede9fe"
              fillOpacity={0.6}
              name="Upper Bound"
              legendType="none"
            />
            <Area
              type="monotone"
              dataKey="lowerBound"
              stroke="none"
              fill="#ffffff"
              fillOpacity={1}
              name="Lower Bound"
              legendType="none"
            />
            {/* Historical line */}
            <Line
              type="monotone"
              dataKey="actualSpend"
              stroke="#1e40af"
              strokeWidth={2}
              dot={{ r: 3 }}
              name="Historical"
              connectNulls={false}
            />
            {/* Forecast line */}
            <Line
              type="monotone"
              dataKey="predictedSpend"
              stroke="#7c3aed"
              strokeWidth={2}
              strokeDasharray="5 5"
              dot={{ r: 3 }}
              name="Forecast"
              connectNulls={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      ) : (
        <p className="text-sm text-gray-500">No chart data available.</p>
      )}
    </div>
  )
}

function LowConfidenceIndicator() {
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium bg-amber-100 text-amber-700 rounded-full">
      <AlertTriangle size={12} />
      Low confidence
    </span>
  )
}

function SpendTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null

  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-sm">
      <p className="font-medium text-gray-900 mb-1">{label}</p>
      {payload.map((entry, idx) => {
        if (entry.name === 'Upper Bound' || entry.name === 'Lower Bound') return null
        return (
          <p key={idx} className="text-gray-600">
            <span className="inline-block w-3 h-3 rounded-full mr-2" style={{ backgroundColor: entry.color }}></span>
            {entry.name}: ₹{Number(entry.value).toLocaleString()}
          </p>
        )
      })}
      {payload.find(p => p.name === 'Upper Bound') && (
        <p className="text-gray-400 text-xs mt-1">
          Confidence: ₹{Number(payload.find(p => p.name === 'Lower Bound')?.value || 0).toLocaleString()} – ₹{Number(payload.find(p => p.name === 'Upper Bound')?.value || 0).toLocaleString()}
        </p>
      )}
    </div>
  )
}

function DemandTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null

  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-sm">
      <p className="font-medium text-gray-900 mb-1">{label}</p>
      {payload.map((entry, idx) => {
        if (entry.name === 'Upper Bound' || entry.name === 'Lower Bound') return null
        return (
          <p key={idx} className="text-gray-600">
            <span className="inline-block w-3 h-3 rounded-full mr-2" style={{ backgroundColor: entry.color }}></span>
            {entry.name}: ₹{Number(entry.value).toLocaleString()}
          </p>
        )
      })}
      {payload.find(p => p.name === 'Upper Bound') && (
        <p className="text-gray-400 text-xs mt-1">
          Confidence: ₹{Number(payload.find(p => p.name === 'Lower Bound')?.value || 0).toLocaleString()} – ₹{Number(payload.find(p => p.name === 'Upper Bound')?.value || 0).toLocaleString()}
        </p>
      )}
    </div>
  )
}

/**
 * Build chart data by merging historical + forecast for spend chart.
 * Historical entries use actualSpend, forecast entries use predictedSpend + bounds.
 */
function buildSpendChartData(data) {
  if (!data) return []

  const historical = (data.historicalData || []).map(h => ({
    month: formatMonth(h.month),
    actualSpend: Number(h.actualSpend),
    predictedSpend: null,
    lowerBound: null,
    upperBound: null,
  }))

  const forecasts = (data.forecasts || []).map(f => ({
    month: formatMonth(f.month),
    actualSpend: null,
    predictedSpend: Number(f.predictedSpend),
    lowerBound: Number(f.lowerBound),
    upperBound: Number(f.upperBound),
    lowConfidence: f.lowConfidence,
  }))

  // Connect historical to forecast by duplicating last historical point into forecast
  if (historical.length > 0 && forecasts.length > 0) {
    const lastHistorical = historical[historical.length - 1]
    forecasts[0] = {
      ...forecasts[0],
      actualSpend: lastHistorical.actualSpend,
    }
  }

  return [...historical, ...forecasts]
}

/**
 * Build chart data for a single demand category.
 */
function buildDemandChartData(category) {
  const historical = (category.historical || []).map(h => ({
    month: formatMonth(h.month),
    actualSpend: Number(h.predictedSpend || h.actualSpend || 0),
    predictedSpend: null,
    lowerBound: null,
    upperBound: null,
  }))

  const forecasts = (category.forecasts || []).map(f => ({
    month: formatMonth(f.month),
    actualSpend: null,
    predictedSpend: Number(f.predictedSpend),
    lowerBound: Number(f.lowerBound),
    upperBound: Number(f.upperBound),
  }))

  // Connect historical to forecast
  if (historical.length > 0 && forecasts.length > 0) {
    const lastHistorical = historical[historical.length - 1]
    forecasts[0] = {
      ...forecasts[0],
      actualSpend: lastHistorical.actualSpend,
    }
  }

  return [...historical, ...forecasts]
}

/**
 * Format "2025-07" to "Jul 2025" for better readability.
 */
function formatMonth(monthStr) {
  if (!monthStr) return ''
  const [year, month] = monthStr.split('-')
  const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const monthIdx = parseInt(month, 10) - 1
  if (monthIdx < 0 || monthIdx > 11) return monthStr
  return `${monthNames[monthIdx]} ${year}`
}
