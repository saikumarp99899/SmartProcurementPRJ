import { useQuery } from '@tanstack/react-query'
import { lookupApi } from '../../api/lookupApi'

export default function RequisitionHeader({ register, errors, watch, setValue }) {
  const departmentId = watch('departmentId')

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: () => lookupApi.getDepartments().then(res => res.data),
  })

  const { data: costCenters } = useQuery({
    queryKey: ['costCenters', departmentId],
    queryFn: () => lookupApi.getCostCenters(departmentId || undefined).then(res => res.data),
    enabled: true,
  })

  return (
    <div className="card">
      <h2 className="text-lg font-semibold text-gray-900 mb-4">Header Information</h2>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Department */}
        <div>
          <label htmlFor="departmentId" className="block text-sm font-medium text-gray-700 mb-1">
            Department <span className="text-red-500">*</span>
          </label>
          <select
            id="departmentId"
            {...register('departmentId', {
              onChange: () => setValue('costCenterId', ''),
            })}
            className={`input-field ${errors.departmentId ? 'border-red-500 focus:ring-red-500 focus:border-red-500' : ''}`}
          >
            <option value="">Select Department</option>
            {departments?.map(d => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
          {errors.departmentId && (
            <p className="mt-1 text-sm text-red-600" role="alert">{errors.departmentId.message}</p>
          )}
        </div>

        {/* Cost Center */}
        <div>
          <label htmlFor="costCenterId" className="block text-sm font-medium text-gray-700 mb-1">
            Cost Center <span className="text-red-500">*</span>
          </label>
          <select
            id="costCenterId"
            {...register('costCenterId')}
            className={`input-field ${errors.costCenterId ? 'border-red-500 focus:ring-red-500 focus:border-red-500' : ''}`}
          >
            <option value="">Select Cost Center</option>
            {costCenters?.map(cc => (
              <option key={cc.id} value={cc.id}>{cc.name} ({cc.code})</option>
            ))}
          </select>
          {errors.costCenterId && (
            <p className="mt-1 text-sm text-red-600" role="alert">{errors.costCenterId.message}</p>
          )}
        </div>
      </div>

      {/* Description */}
      <div className="mt-4">
        <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-1">
          Description <span className="text-red-500">*</span>
        </label>
        <textarea
          id="description"
          {...register('description')}
          className={`input-field ${errors.description ? 'border-red-500 focus:ring-red-500 focus:border-red-500' : ''}`}
          rows={3}
          placeholder="Purpose of this requisition..."
        />
        {errors.description && (
          <p className="mt-1 text-sm text-red-600" role="alert">{errors.description.message}</p>
        )}
      </div>
    </div>
  )
}
