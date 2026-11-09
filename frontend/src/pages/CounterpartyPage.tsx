import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import StarIcon from '@mui/icons-material/Star'
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton,
  MenuItem, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { useCounterparty, useHouseSsis } from '../api/hooks'
import type { CounterpartyDetail } from '../api/types'
import { fmtDate, humanize } from '../app/format'
import { useNotify } from '../app/notify'
import { useAuth } from '../auth/AuthContext'
import { PageHeader } from '../components/PageHeader'
import { EmptyState, Field, Section } from '../components/Section'

const CLIENT_TYPES = ['BANK', 'ASSET_MANAGER', 'HEDGE_FUND', 'PENSION_FUND', 'INSURER', 'CORPORATE', 'SOVEREIGN']
const CONTACT_ROLES = ['CONFIRMATIONS', 'OPERATIONS', 'AUTHORISED_SIGNATORY', 'RELATIONSHIP_MANAGER']

export default function CounterpartyPage() {
  const id = Number(useParams().id)
  const { data: c, error } = useCounterparty(id)
  const { data: house = [] } = useHouseSsis()
  const { hasRole } = useAuth()
  const canEdit = hasRole('SUPERVISOR')
  const qc = useQueryClient()
  const notify = useNotify()
  const [editing, setEditing] = useState(false)
  const [addContact, setAddContact] = useState(false)
  const [addSsi, setAddSsi] = useState(false)

  if (error) return <Alert severity="error">{errorMessage(error)}</Alert>
  if (!c) return <Skeleton variant="rounded" height={500} />

  const mutate = async (fn: () => Promise<unknown>, message: string) => {
    try {
      await fn()
      notify(message)
      await qc.invalidateQueries({ queryKey: ['counterparty', id] })
      await qc.invalidateQueries({ queryKey: ['counterparties'] })
    } catch (e) {
      notify(errorMessage(e), 'error')
    }
  }

  return (
    <>
      <PageHeader
        crumbs={[{ label: 'Counterparties', to: '/counterparties' }, { label: c.shortCode }]}
        title={c.legalName}
        subtitle={<>LEI <span className="mono">{c.lei}</span> · {humanize(c.clientType)} · {c.country}</>}
        actions={canEdit && <Button variant="outlined" startIcon={<EditOutlinedIcon />} onClick={() => setEditing(true)}>Edit</Button>}
      />
      {!canEdit && <Alert severity="info" sx={{ mb: 2 }}>Static data is maintained by supervisors. You have read-only access.</Alert>}

      <Section title="Profile" sx={{ mb: 2 }}>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' } }}>
          <Field label="Short code" mono>{c.shortCode}</Field>
          <Field label="BIC" mono>{c.bic}</Field>
          <Field label="Delivery channel">{c.deliveryChannel === 'PORTAL' ? 'Client portal' : 'Email'}</Field>
          <Field label="Response SLA">{c.confirmationSlaHours} hours</Field>
          <Box sx={{ gridColumn: { md: 'span 2' } }}><Field label="Master agreement">{c.masterAgreement?.replace(/^the /, '')}</Field></Box>
          <Field label="Status"><Chip size="small" label={c.active ? 'Active' : 'Inactive'} color={c.active ? 'success' : 'default'} variant="outlined" /></Field>
        </Box>
      </Section>

      <Section title="Contacts" subtitle="Confirmation contacts receive confirmations and chasers" sx={{ mb: 2 }} noPadding
        actions={canEdit && <Button startIcon={<AddIcon />} onClick={() => setAddContact(true)}>Add contact</Button>}>
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead><TableRow><TableCell>Name</TableCell><TableCell>Role</TableCell><TableCell>Email</TableCell><TableCell>Phone</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {c.contacts.map((ct) => (
                <TableRow key={ct.id}>
                  <TableCell>{ct.name} {ct.primary && <Tooltip title="Primary contact"><StarIcon sx={{ fontSize: 14, color: 'warning.main', verticalAlign: 'middle' }} /></Tooltip>}</TableCell>
                  <TableCell>{humanize(ct.role)}</TableCell>
                  <TableCell className="mono">{ct.email}</TableCell>
                  <TableCell>{ct.phone ?? '—'}</TableCell>
                  <TableCell align="right">
                    {canEdit && <IconButton size="small" aria-label="Remove contact" onClick={() => mutate(() => api.delete(`/counterparties/${id}/contacts/${ct.id}`), 'Contact removed')}><DeleteOutlineIcon fontSize="small" /></IconButton>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Section>

      <Section title="Standard settlement instructions" subtitle="One active SSI per currency – adding a new one supersedes the previous instruction" sx={{ mb: 2 }} noPadding
        actions={canEdit && <Button startIcon={<AddIcon />} onClick={() => setAddSsi(true)}>Add / replace SSI</Button>}>
        {c.settlementInstructions.length === 0 ? <EmptyState title="No settlement instructions" /> : (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead><TableRow><TableCell>Ccy</TableCell><TableCell>Beneficiary</TableCell><TableCell>Account with</TableCell><TableCell>BIC</TableCell><TableCell>Account / IBAN</TableCell><TableCell>Intermediary</TableCell><TableCell>Effective</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>
                {c.settlementInstructions.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell><Chip size="small" label={s.currency} variant="outlined" /></TableCell>
                    <TableCell>{s.beneficiaryName}</TableCell>
                    <TableCell>{s.bankName}</TableCell>
                    <TableCell className="mono">{s.bankBic}</TableCell>
                    <TableCell className="mono">{s.accountNumber}</TableCell>
                    <TableCell className="mono">{s.intermediaryBic ?? '—'}</TableCell>
                    <TableCell>{fmtDate(s.effectiveFrom)}</TableCell>
                    <TableCell align="right">
                      {canEdit && <Tooltip title="Deactivate"><IconButton size="small" onClick={() => mutate(() => api.delete(`/counterparties/${id}/ssis/${s.id}`), `${s.currency} SSI deactivated`)}><DeleteOutlineIcon fontSize="small" /></IconButton></Tooltip>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </Section>

      <Section title="House settlement instructions (Meridian Capital)" subtitle="Printed on every confirmation for the currencies we receive" noPadding>
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead><TableRow><TableCell>Ccy</TableCell><TableCell>Account with</TableCell><TableCell>BIC</TableCell><TableCell>Account</TableCell></TableRow></TableHead>
            <TableBody>
              {house.map((s) => (
                <TableRow key={s.id}><TableCell><Chip size="small" label={s.currency} variant="outlined" /></TableCell><TableCell>{s.bankName}</TableCell><TableCell className="mono">{s.bankBic}</TableCell><TableCell className="mono">{s.accountNumber}</TableCell></TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Section>

      {editing && <CounterpartyDialog existing={c} onClose={() => setEditing(false)} />}
      {addContact && <ContactDialog id={id} onClose={() => setAddContact(false)} onSave={(body) => mutate(() => api.post(`/counterparties/${id}/contacts`, body), 'Contact added')} />}
      {addSsi && <SsiDialog legalName={c.legalName} onClose={() => setAddSsi(false)} onSave={(body) => mutate(() => api.post(`/counterparties/${id}/ssis`, body), `${body.currency} SSI saved`)} />}
    </>
  )
}

export function CounterpartyDialog({ existing, onClose, onSaved }: { existing?: CounterpartyDetail; onClose: () => void; onSaved?: (id: number) => void }) {
  const [form, setForm] = useState({
    shortCode: existing?.shortCode ?? '', legalName: existing?.legalName ?? '', lei: existing?.lei ?? '', bic: existing?.bic ?? '',
    country: existing?.country ?? '', clientType: existing?.clientType ?? 'ASSET_MANAGER', deliveryChannel: existing?.deliveryChannel ?? 'EMAIL',
    confirmationSlaHours: existing?.confirmationSlaHours ?? 24, masterAgreement: existing?.masterAgreement ?? '', active: existing?.active ?? true,
  })
  const [error, setError] = useState<string>()
  const qc = useQueryClient()
  const notify = useNotify()
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: k === 'confirmationSlaHours' ? Number(e.target.value) : ['shortCode', 'lei', 'bic', 'country'].includes(k) ? e.target.value.toUpperCase() : e.target.value })
  const save = async () => {
    try {
      const { data } = existing
        ? await api.put<CounterpartyDetail>(`/counterparties/${existing.id}`, form)
        : await api.post<CounterpartyDetail>('/counterparties', form)
      notify(existing ? 'Counterparty updated' : 'Counterparty created')
      await qc.invalidateQueries({ queryKey: ['counterparties'] })
      await qc.invalidateQueries({ queryKey: ['counterparty', data.id] })
      onSaved?.(data.id)
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    }
  }
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{existing ? `Edit ${existing.shortCode}` : 'New counterparty'}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: '1fr 1fr', mt: 1 }}>
          <TextField label="Legal name" value={form.legalName} onChange={set('legalName')} sx={{ gridColumn: '1 / -1' }} />
          <TextField label="Short code" value={form.shortCode} onChange={set('shortCode')} />
          <TextField label="Country (ISO-2)" value={form.country} onChange={set('country')} slotProps={{ htmlInput: { maxLength: 2 } }} />
          <TextField label="LEI (20 chars)" value={form.lei} onChange={set('lei')} slotProps={{ htmlInput: { maxLength: 20 } }} />
          <TextField label="BIC (optional)" value={form.bic} onChange={set('bic')} />
          <TextField select label="Client type" value={form.clientType} onChange={set('clientType')}>
            {CLIENT_TYPES.map((t) => <MenuItem key={t} value={t}>{humanize(t)}</MenuItem>)}
          </TextField>
          <TextField select label="Delivery channel" value={form.deliveryChannel} onChange={set('deliveryChannel')}>
            <MenuItem value="EMAIL">Email</MenuItem><MenuItem value="PORTAL">Client portal</MenuItem>
          </TextField>
          <TextField label="Response SLA (hours)" type="number" value={form.confirmationSlaHours} onChange={set('confirmationSlaHours')} />
          <FormControlLabel control={<Checkbox checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />} label="Active" />
          <TextField label="Master agreement" value={form.masterAgreement} onChange={set('masterAgreement')} sx={{ gridColumn: '1 / -1' }} placeholder="the ISDA 2002 Master Agreement dated …" />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save}>Save</Button>
      </DialogActions>
    </Dialog>
  )
}

function ContactDialog({ onClose, onSave }: { id: number; onClose: () => void; onSave: (b: object) => Promise<void> }) {
  const [form, setForm] = useState({ name: '', email: '', phone: '', role: 'CONFIRMATIONS', primary: false })
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Add contact</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, mt: 1 }}>
          <TextField label="Name" value={form.name} onChange={set('name')} />
          <TextField label="Email" value={form.email} onChange={set('email')} />
          <TextField label="Phone" value={form.phone} onChange={set('phone')} />
          <TextField select label="Role" value={form.role} onChange={set('role')}>
            {CONTACT_ROLES.map((r) => <MenuItem key={r} value={r}>{humanize(r)}</MenuItem>)}
          </TextField>
          <FormControlLabel control={<Checkbox checked={form.primary} onChange={(e) => setForm({ ...form, primary: e.target.checked })} />} label="Primary contact" />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!form.name || !form.email} onClick={async () => { await onSave(form); onClose() }}>Add</Button>
      </DialogActions>
    </Dialog>
  )
}

function SsiDialog({ legalName, onClose, onSave }: { legalName: string; onClose: () => void; onSave: (b: { currency: string }) => Promise<void> }) {
  const [form, setForm] = useState({
    currency: '', beneficiaryName: legalName, bankName: '', bankBic: '', accountNumber: '', intermediaryBic: '',
    effectiveFrom: new Date().toISOString().slice(0, 10),
  })
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: ['currency', 'bankBic', 'intermediaryBic'].includes(k) ? e.target.value.toUpperCase() : e.target.value })
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Add or replace settlement instruction</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 2 }}>SSI changes are a common fraud vector. Verify every change by call-back to a known contact before saving.</Alert>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: '1fr 1fr' }}>
          <TextField label="Currency" value={form.currency} onChange={set('currency')} slotProps={{ htmlInput: { maxLength: 3 } }} />
          <TextField label="Effective from" type="date" value={form.effectiveFrom} onChange={set('effectiveFrom')} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField label="Beneficiary" value={form.beneficiaryName} onChange={set('beneficiaryName')} sx={{ gridColumn: '1 / -1' }} />
          <TextField label="Account with (bank)" value={form.bankName} onChange={set('bankName')} />
          <TextField label="Bank BIC" value={form.bankBic} onChange={set('bankBic')} />
          <TextField label="Account / IBAN" value={form.accountNumber} onChange={set('accountNumber')} />
          <TextField label="Intermediary BIC (optional)" value={form.intermediaryBic} onChange={set('intermediaryBic')} />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!form.currency || !form.bankBic || !form.accountNumber} onClick={async () => { await onSave(form); onClose() }}>Save SSI</Button>
      </DialogActions>
    </Dialog>
  )
}
