import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/aiApi'
import { ShieldAlert, DollarSign, Split, Building2, AlertTriangle, Info, CheckCircle2 } from 'lucide-react'

const ANOMALY_TYPES = [
  { value: '', label: 'All Types' },
  { value: 'PRICE', label: 'Price' },
  { value: 'SPLIT_ORDER', label: 'Split Order' },
  { value: 'VENDOR_CONCENTRATION', label: 'Vendor Concentration' },
]

const SEVERITY_LEVELS = [
  { value: '', label: 'All Severities' },
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
]

const typeConfig = {
  PRICE: { icon: DollarSign, label: 'Price Anomaly', bgColor: 'bg-purple-100', textColor: 'text-purple-700' },
  SPLIT_ORDER: { icon: Split, label: 'Split Order', bgColor: 'bg-indigo-100', textColor: 'text-indigo-700' },
  VENDOR_CONCENTRATION: { icon: Building2, label: 'Vendor Concentration', bgColor: 'bg-teal-100', textColor: 'text-teal-700' },
}

const severityConfig = {
  HIGH: { color: 'bg-red-100 text-red-700 border-red-200', dotColor: 'bg-red-500' },
  MEDIUM: { color: 'bg-yellow-100 text-yellow-700 border-yellow-200', dotColor: 'bg-yellow-500' },
  LOW: { color: 'bg-blue-100 text-blue-700 border-blue-200', dotColor: 'bg-blue-500' },
}

export default function AnomalyDetection() {
  const [typeFilter, setTypeFilter] = useState('')
  const [severityFilter, setSeverityFilter] = useState('')

  const { data: anomalies, isLoading, isError } = useQuery({
    queryKey: ['anomalies'],
    queryFn: () => aiApi.getAnomalies().then(res => res.data),
  })

  const filteredAnomalies = (anomalies || []).filter(anomaly => {
    if (typeFilter && anomaly.type !== typeFilter) return false
    if (severityFilter && anomaly.severity !== severityFilter) return false
    return true
  })

  if (isLoading) {
    return (
      <div className="text-center py-10">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
        <p className="text-gray-500">Scanning for anomalies...</p>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="text-center py-10">
        <AlertTriangle className="mx-auto mb-4 text-red-400" size={48} />
        <p className="text-gray-700 font-medium">Failed to load anomalies</p>
        <p className="text-gray-500 text-sm mt-1">The anomaly detection service is temporarily unavailable.</p>
      </div>
    )
  }

  return (
    <div>
      {/* Page Header */}
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-red-100 rounded-lg">
          <ShieldAlert className="text-red-600" size={24} />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">AI Anomaly Detection</h1>
          <p className="text-gray-500 text-sm">Detect price anomalies, split ordering patterns, and vendor concentration risks</p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 mb-6">
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          {ANOMALY_TYPES.map(t => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>

        <select
          value={severityFilter}
          onChange={(e) => setSeverityFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          {SEVERITY_LEVELS.map(s => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>

        {(typeFilter || severityFilter) && (
          <button
            onClick={() => { setTypeFilter(''); setSeverityFilter('') }}
            className="px-3 py-2 text-sm text-gray-600 hover:text-gray-800 underline"
          >
            Clear filters
          </button>
        )}
      </div>

      {/* Results count */}
      <p className="text-sm text-gray-500 mb-4">
        Showing {filteredAnomalies.length} anomal{filteredAnomalies.length === 1 ? 'y' : 'ies'}
        {anomalies && anomalies.length !== filteredAnomalies.length && ` of ${anomalies.length} total`}
      </p>

      {/* Empty State */}
      {filteredAnomalies.length === 0 && (
        <div className="text-center py-10 card">
          <CheckCircle2 className="mx-auto mb-4 text-green-400" size={48} />
          <p className="text-gray-700 font-medium">No anomalies detected</p>
          <p className="text-gray-500 text-sm mt-1">
            {typeFilter || severityFilter
              ? 'No anomalies match the current filters. Try adjusting your filters.'
              : 'All procurement patterns appear normal. Keep monitoring for changes.'}
          </p>
        </div>
      )}

      {/* Anomaly Cards */}
      <div className="space-y-4">
        {filteredAnomalies.map(anomaly => {
          const type = typeConfig[anomaly.type] || typeConfig.PRICE
          const severity = severityConfig[anomaly.severity] || severityConfig.LOW
          const TypeIcon = type.icon

          return (
            <div key={anomaly.id} className="card border-l-4" style={{ borderLeftColor: anomaly.severity === 'HIGH' ? '#ef4444' : anomaly.severity === 'MEDIUM' ? '#eab308' : '#3b82f6' }}>
              <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
                {/* Type Badge */}
                <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${type.bgColor} ${type.textColor}`}>
                  <TypeIcon size={14} />
                  {type.label}
                </div>

                {/* Severity Badge */}
                <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${severity.color}`}>
                  <span className={`w-2 h-2 rounded-full ${severity.dotColor}`}></span>
                  {anomaly.severity}
                </div>
              </div>

              {/* Description */}
              <p className="text-gray-800 text-sm mb-3">{anomaly.description}</p>

              {/* Affected Entities */}
              {anomaly.affectedEntities && anomaly.affectedEntities.length > 0 && (
                <div className="mb-3">
                  <p className="text-xs font-medium text-gray-500 uppercase mb-1">Affected Entities</p>
                  <div className="flex flex-wrap gap-2">
                    {anomaly.affectedEntities.map((entity, idx) => (
                      <span key={idx} className="inline-flex items-center px-2 py-0.5 rounded bg-gray-100 text-xs text-gray-700">
                        {entity.entityName || `${entity.entityType} #${entity.entityId}`}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Recommended Action */}
              {anomaly.recommendedAction && (
                <div className="flex gap-2 p-3 bg-blue-50 rounded-lg">
                  <Info className="text-blue-500 flex-shrink-0 mt-0.5" size={16} />
                  <div>
                    <p className="text-xs font-medium text-blue-700 mb-0.5">Recommended Action</p>
                    <p className="text-sm text-gray-700">{anomaly.recommendedAction}</p>
                  </div>
                </div>
              )}

              {/* Detected At */}
              {anomaly.detectedAt && (
                <p className="text-xs text-gray-400 mt-3">
                  Detected: {new Date(anomaly.detectedAt).toLocaleDateString()} at {new Date(anomaly.detectedAt).toLocaleTimeString()}
                </p>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
