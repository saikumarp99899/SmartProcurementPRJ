import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { approvalWorkflowApi } from '../../api/approvalWorkflowApi'
import { lookupApi } from '../../api/lookupApi'
import WorkflowModal from './WorkflowModal'
import toast from 'react-hot-toast'
import {
  PlusCircle, Pencil, Trash2, GitBranch, ToggleLeft, ToggleRight,
  Search, AlertTriangle, User as UserIcon, Users as UsersIcon, CornerUpRight, Building2,
} from 'lucide-react'

const formatAmount = (value) =>
  value === null || value === undefined ? '∞' : `₹${Number(value).toLocaleString()}`

/** Small icon cue for how a step resolves its approver. */
function StepTypeIcon({ type, size = 12 }) {
  if (type === 'ROLE') return <UsersIcon size={size} className="text-indigo-500" />
  if (type === 'REQUESTER_MANAGER') return <CornerUpRight size={size} className="text-teal-500" />
  return <UserIcon size={size} className="text-primary-500" />
}

/** Renders a workflow's steps as an ordered route. */
function StepChain({ steps }) {
  if (!steps?.length) {
    return <span className="text-xs text-gray-400 italic">No steps</span>
  }
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {steps.map((step, index) => (
        <div key={step.id ?? index} className="flex items-center gap-1.5">
          <span
            className="inline-flex items-center gap-1.5 px-2 py-1 bg-white border border-gray-200 rounded-md text-xs"
            title={step.name}
          >
            <span className="font-semibold text-gray-400">{step.stepOrder}</span>
            <StepTypeIcon type={step.approverType} />
            <span className="text-gray-700">{step.approverLabel}</span>
          </span>
          {index < steps.length - 1 && <span className="text-gray-300 text-xs">→</span>}
        </div>
      ))}
    </div>
  )
}

/** Lets an admin confirm an amount routes as intended before buyers hit it. */
function WorkflowPreview({ departments }) {
  const [amount, setAmount] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [result, setResult] = useState(undefined) // undefined = not run, null = no match
  const [loading, setLoading] = useState(false)

  const handlePreview = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const res = await approvalWorkflowApi.previewWorkflow(
        Number(amount),
        departmentId === '' ? undefined : Number(departmentId)
      )
      // 204 No Content means nothing matched.
      setResult(res.status === 204 ? null : res.data)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Preview failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="card mb-6">
      <div className="flex items-center gap-2 mb-3">
        <Search size={18} className="text-primary-600" />
        <h2 className="font-semibold text-gray-900">Test Routing</h2>
      </div>
      <p className="text-sm text-gray-500 mb-3">
        Check which workflow a requisition would follow.
      </p>

      <form onSubmit={handlePreview} className="flex flex-wrap gap-3 mb-4">
        <input
          type="number"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className="input-field flex-1 min-w-[140px]"
          placeholder="Amount, e.g. 120000"
          min={0}
          step="0.01"
          required
        />
        <select
          value={departmentId}
          onChange={(e) => setDepartmentId(e.target.value)}
          className="input-field flex-1 min-w-[160px]"
        >
          <option value="">Any department</option>
          {departments?.map(d => (
            <option key={d.id} value={d.id}>{d.name}</option>
          ))}
        </select>
        <button type="submit" disabled={loading} className="btn-secondary whitespace-nowrap">
          {loading ? 'Checking...' : 'Preview'}
        </button>
      </form>

      {result === null && (
        <div className="flex items-start gap-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
          <AlertTriangle size={16} className="text-yellow-600 flex-shrink-0 mt-0.5" />
          <p className="text-sm text-yellow-800">
            No workflow matches. A requisition like this would fall back to a single approval open
            to any approver. Add a workflow to route it explicitly.
          </p>
        </div>
      )}

      {result && (
        <div className="p-3 bg-primary-50 border border-primary-200 rounded-lg">
          <p className="text-sm font-semibold text-primary-800 mb-2">{result.name}</p>
          <StepChain steps={result.steps} />
        </div>
      )}
    </div>
  )
}

export default function ApprovalWorkflows() {
  const [modal, setModal] = useState(null) // { workflow } | { workflow: null }
  const queryClient = useQueryClient()

  const { data: workflows, isLoading } = useQuery({
    queryKey: ['approval-workflows'],
    queryFn: () => approvalWorkflowApi.getAll().then(res => res.data),
  })

  // Admins are valid approvers in a workflow, so include them.
  const { data: approvers } = useQuery({
    queryKey: ['approvers', 'withAdmins'],
    queryFn: () => lookupApi.getApprovers(true).then(res => res.data),
  })

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: () => lookupApi.getDepartments().then(res => res.data),
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }) => approvalWorkflowApi.setActive(id, active),
    onSuccess: () => {
      toast.success('Workflow updated')
      queryClient.invalidateQueries(['approval-workflows'])
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Update failed'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id) => approvalWorkflowApi.remove(id),
    onSuccess: () => {
      toast.success('Workflow deleted')
      queryClient.invalidateQueries(['approval-workflows'])
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Delete failed'),
  })

  const handleDelete = (workflow) => {
    if (window.confirm(
      `Delete "${workflow.name}"?\n\nRequisitions already in progress keep their existing approval `
      + `chain. To stop using this workflow for new requisitions only, deactivate it instead.`
    )) {
      deleteMutation.mutate(workflow.id)
    }
  }

  if (isLoading) return <div className="text-center py-10">Loading...</div>

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-2">
        <h1 className="text-2xl font-bold text-gray-900">Approval Workflows</h1>
        <button onClick={() => setModal({ workflow: null })} className="btn-primary flex items-center gap-2">
          <PlusCircle size={18} /> New Workflow
        </button>
      </div>
      <p className="text-sm text-gray-500 mb-6">
        Define the route a requisition takes based on its amount and department. Steps are actioned
        in order — each one becomes available only once the previous has approved.
      </p>

      <WorkflowPreview departments={departments} />

      {workflows?.length === 0 ? (
        <div className="card text-center py-10">
          <GitBranch size={28} className="text-gray-300 mx-auto mb-3" />
          <p className="text-gray-600 font-medium mb-1">No workflows configured</p>
          <p className="text-sm text-gray-500">
            Until a workflow exists, every submitted requisition creates a single approval that any
            approver can action.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {workflows?.map((wf) => (
            <div key={wf.id} className={`card ${wf.active ? '' : 'opacity-60'}`}>
              <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-semibold text-gray-900">{wf.name}</h3>
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      wf.active ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'
                    }`}>
                      {wf.active ? 'Active' : 'Inactive'}
                    </span>
                    {wf.priority > 0 && (
                      <span className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded-full text-xs font-medium">
                        Priority {wf.priority}
                      </span>
                    )}
                  </div>
                  {wf.description && (
                    <p className="text-sm text-gray-500 mt-1">{wf.description}</p>
                  )}
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={() => toggleMutation.mutate({ id: wf.id, active: !wf.active })}
                    className="text-gray-500 hover:text-primary-600"
                    title={wf.active ? 'Deactivate' : 'Activate'}
                    aria-label={wf.active ? 'Deactivate workflow' : 'Activate workflow'}
                  >
                    {wf.active ? <ToggleRight size={20} /> : <ToggleLeft size={20} />}
                  </button>
                  <button
                    onClick={() => setModal({ workflow: wf })}
                    className="text-gray-500 hover:text-primary-600"
                    title="Edit"
                    aria-label="Edit workflow"
                  >
                    <Pencil size={16} />
                  </button>
                  <button
                    onClick={() => handleDelete(wf)}
                    className="text-gray-500 hover:text-red-600"
                    title="Delete"
                    aria-label="Delete workflow"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>

              {/* Criteria */}
              <div className="flex flex-wrap items-center gap-3 text-xs text-gray-600 mb-3">
                <span className="px-2 py-1 bg-gray-50 border rounded-md">
                  {formatAmount(wf.minAmount)} — {formatAmount(wf.maxAmount)}
                </span>
                <span className="inline-flex items-center gap-1 px-2 py-1 bg-gray-50 border rounded-md">
                  <Building2 size={11} className="text-gray-400" />
                  {wf.departmentName || 'All departments'}
                </span>
              </div>

              {/* Route */}
              <div className="pt-3 border-t">
                <p className="text-xs font-medium text-gray-500 mb-2">Approval route</p>
                <StepChain steps={wf.steps} />
              </div>
            </div>
          ))}
        </div>
      )}

      {modal && (
        <WorkflowModal
          workflow={modal.workflow}
          approvers={approvers}
          departments={departments}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null)
            queryClient.invalidateQueries(['approval-workflows'])
          }}
        />
      )}
    </div>
  )
}
