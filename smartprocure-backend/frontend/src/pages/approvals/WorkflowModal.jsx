import { useState } from 'react'
import { approvalWorkflowApi } from '../../api/approvalWorkflowApi'
import toast from 'react-hot-toast'
import { X, Plus, Trash2, ArrowUp, ArrowDown, GripVertical } from 'lucide-react'

const APPROVER_TYPES = [
  { value: 'SPECIFIC_USER', label: 'Specific person' },
  { value: 'ROLE', label: 'Anyone with a role' },
  { value: 'REQUESTER_MANAGER', label: "Requester's manager" },
]

const APPROVER_ROLES = ['APPROVER', 'ADMIN']

const emptyStep = () => ({
  name: '',
  approverType: 'SPECIFIC_USER',
  approverId: '',
  approverRole: 'APPROVER',
})

/**
 * Create/edit form for a workflow and its ordered steps.
 * `workflow` null means create.
 */
export default function WorkflowModal({ workflow, approvers, departments, onClose, onSaved }) {
  const isEdit = Boolean(workflow)

  const [form, setForm] = useState({
    name: workflow?.name ?? '',
    description: workflow?.description ?? '',
    minAmount: workflow?.minAmount ?? '',
    maxAmount: workflow?.maxAmount ?? '',
    departmentId: workflow?.departmentId ?? '',
    priority: workflow?.priority ?? 0,
    active: workflow?.active ?? true,
  })

  const [steps, setSteps] = useState(
    workflow?.steps?.length
      ? workflow.steps.map(s => ({
          name: s.name,
          approverType: s.approverType,
          approverId: s.approverId ?? '',
          approverRole: s.approverRole ?? 'APPROVER',
        }))
      : [emptyStep()]
  )

  const [saving, setSaving] = useState(false)

  const updateStep = (index, patch) => {
    setSteps(prev => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  }

  const addStep = () => setSteps(prev => [...prev, emptyStep()])

  const removeStep = (index) => {
    // A workflow with no steps would approve everything automatically.
    if (steps.length === 1) {
      toast.error('A workflow needs at least one step')
      return
    }
    setSteps(prev => prev.filter((_, i) => i !== index))
  }

  const moveStep = (index, direction) => {
    const target = index + direction
    if (target < 0 || target >= steps.length) return
    setSteps(prev => {
      const next = [...prev]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()

    // Validate locally so the user sees the problem next to the field
    // rather than as a single server-side message.
    for (const [i, step] of steps.entries()) {
      if (!step.name.trim()) {
        toast.error(`Step ${i + 1} needs a name`)
        return
      }
      if (step.approverType === 'SPECIFIC_USER' && !step.approverId) {
        toast.error(`Step ${i + 1} needs an approver selected`)
        return
      }
    }

    setSaving(true)
    try {
      const payload = {
        name: form.name,
        description: form.description || null,
        minAmount: Number(form.minAmount),
        // Blank upper bound means "no limit", modelled as null server-side.
        maxAmount: form.maxAmount === '' ? null : Number(form.maxAmount),
        departmentId: form.departmentId === '' ? null : Number(form.departmentId),
        priority: Number(form.priority),
        active: form.active,
        steps: steps.map(s => ({
          name: s.name,
          approverType: s.approverType,
          approverId: s.approverType === 'SPECIFIC_USER' ? Number(s.approverId) : null,
          approverRole: s.approverType === 'ROLE' ? s.approverRole : null,
        })),
      }

      if (isEdit) {
        await approvalWorkflowApi.update(workflow.id, payload)
        toast.success('Workflow updated')
      } else {
        await approvalWorkflowApi.create(payload)
        toast.success('Workflow created')
      }
      onSaved()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not save workflow')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-full overflow-y-auto p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-gray-900">
            {isEdit ? 'Edit Workflow' : 'New Workflow'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600" aria-label="Close">
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Identity */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Workflow Name</label>
            <input
              type="text"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className="input-field"
              placeholder="e.g. High Value IT Purchase"
              maxLength={150}
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Description <span className="text-gray-400 font-normal">(optional)</span>
            </label>
            <input
              type="text"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="input-field"
              maxLength={500}
            />
          </div>

          {/* Criteria */}
          <fieldset className="border rounded-lg p-4">
            <legend className="text-sm font-semibold text-gray-700 px-2">When this applies</legend>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Min Amount (₹)</label>
                <input
                  type="number"
                  value={form.minAmount}
                  onChange={(e) => setForm({ ...form, minAmount: e.target.value })}
                  className="input-field"
                  min={0}
                  step="0.01"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Max Amount (₹)</label>
                <input
                  type="number"
                  value={form.maxAmount}
                  onChange={(e) => setForm({ ...form, maxAmount: e.target.value })}
                  className="input-field"
                  min={0}
                  step="0.01"
                  placeholder="Blank = no limit"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Department</label>
                <select
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                  className="input-field"
                >
                  <option value="">All departments</option>
                  {departments?.map(d => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
                <input
                  type="number"
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: e.target.value })}
                  className="input-field"
                  min={0}
                />
                <p className="text-xs text-gray-500 mt-1">
                  Higher wins when several workflows match.
                </p>
              </div>
            </div>
          </fieldset>

          {/* Steps */}
          <fieldset className="border rounded-lg p-4">
            <legend className="text-sm font-semibold text-gray-700 px-2">
              Approval steps (in order)
            </legend>

            <div className="space-y-3">
              {steps.map((step, index) => (
                <div key={index} className="border rounded-lg p-3 bg-gray-50">
                  <div className="flex items-center gap-2 mb-3">
                    <GripVertical size={14} className="text-gray-400" />
                    <span className="inline-flex items-center justify-center w-6 h-6 bg-primary-100 text-primary-700 rounded-full text-xs font-bold">
                      {index + 1}
                    </span>
                    <input
                      type="text"
                      value={step.name}
                      onChange={(e) => updateStep(index, { name: e.target.value })}
                      className="input-field flex-1 py-1.5"
                      placeholder="Step name, e.g. Finance sign-off"
                      maxLength={150}
                    />
                    <button
                      type="button"
                      onClick={() => moveStep(index, -1)}
                      disabled={index === 0}
                      className="p-1 text-gray-400 hover:text-gray-700 disabled:opacity-30"
                      aria-label={`Move step ${index + 1} up`}
                    >
                      <ArrowUp size={14} />
                    </button>
                    <button
                      type="button"
                      onClick={() => moveStep(index, 1)}
                      disabled={index === steps.length - 1}
                      className="p-1 text-gray-400 hover:text-gray-700 disabled:opacity-30"
                      aria-label={`Move step ${index + 1} down`}
                    >
                      <ArrowDown size={14} />
                    </button>
                    <button
                      type="button"
                      onClick={() => removeStep(index)}
                      className="p-1 text-gray-400 hover:text-red-600"
                      aria-label={`Remove step ${index + 1}`}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Approver is</label>
                      <select
                        value={step.approverType}
                        onChange={(e) => updateStep(index, { approverType: e.target.value })}
                        className="input-field py-1.5"
                      >
                        {APPROVER_TYPES.map(t => (
                          <option key={t.value} value={t.value}>{t.label}</option>
                        ))}
                      </select>
                    </div>

                    {step.approverType === 'SPECIFIC_USER' && (
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Person</label>
                        <select
                          value={step.approverId}
                          onChange={(e) => updateStep(index, { approverId: e.target.value })}
                          className="input-field py-1.5"
                        >
                          <option value="">Select…</option>
                          {approvers?.map(a => (
                            <option key={a.id} value={a.id}>{a.name}</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {step.approverType === 'ROLE' && (
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Role</label>
                        <select
                          value={step.approverRole}
                          onChange={(e) => updateStep(index, { approverRole: e.target.value })}
                          className="input-field py-1.5"
                        >
                          {APPROVER_ROLES.map(r => (
                            <option key={r} value={r}>{r}</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {step.approverType === 'REQUESTER_MANAGER' && (
                      <p className="text-xs text-gray-500 self-end pb-2">
                        Resolved per requisition. Requesters need a manager set on their account.
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>

            <button
              type="button"
              onClick={addStep}
              className="btn-secondary flex items-center gap-2 text-sm mt-3"
            >
              <Plus size={14} /> Add Step
            </button>
          </fieldset>

          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })}
              className="rounded border-gray-300"
            />
            Active (inactive workflows are ignored when routing requisitions)
          </label>

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary flex-1">Cancel</button>
            <button type="submit" disabled={saving} className="btn-primary flex-1">
              {saving ? 'Saving...' : isEdit ? 'Save Changes' : 'Create Workflow'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
