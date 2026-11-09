import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import ScienceOutlinedIcon from '@mui/icons-material/ScienceOutlined'
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined'
import {
  Alert, AlertTitle, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Divider,
  ListItemText, Menu, MenuItem, Paper, Skeleton, Tab, Tabs, TextField, Tooltip, Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { api, downloadFile, errorMessage } from '../api/client'
import { useTrade, useTradeAction } from '../api/hooks'
import type { ActionType, AllowedAction, AmendRequest, InboundResult, TradeDetail } from '../api/types'
import { fmtAmount, fmtDateTime, fmtRate, fromNow } from '../app/format'
import { useNotify } from '../app/notify'
import { AuditTimeline } from '../components/AuditTimeline'
import { PageHeader } from '../components/PageHeader'
import { EmptyState, Field, Section } from '../components/Section'
import { StatusChip, ToneChip } from '../components/StatusChip'
import { DocumentList, LifecycleStepper, MatchResultView, SettlementTable, TermsTable } from '../components/trade'

const PRIMARY: ActionType[] = ['ENRICH', 'GENERATE', 'SUBMIT', 'APPROVE', 'SEND', 'CONFIRM']
const DANGER: ActionType[] = ['REJECT', 'DISPUTE', 'CANCEL']
const MATCHABLE = ['AWAITING_RESPONSE', 'MISMATCHED', 'DISPUTED']

const CONFIRM_TEXT: Partial<Record<ActionType, string>> = {
  ENRICH: 'Validate counterparty static data and settlement instructions for this trade.',
  GENERATE: 'Generate a DRAFT confirmation from the active template and current trade economics.',
  SUBMIT: 'Submit the current draft to a checker for four-eyes approval.',
  APPROVE: 'Approve the draft. A FINAL e-stamped confirmation will be issued and released for sending.',
  REJECT: 'Return the draft to the maker. Explain what must be corrected.',
  SEND: 'Deliver the FINAL confirmation to the client contacts and start the response SLA.',
  CHASE: 'Send a reminder to the client confirmation contacts.',
  CONFIRM: 'Mark this trade as fully confirmed. The confirmation will be closed.',
  FORCE_MATCH: 'Accept the break as within tolerance and treat the confirmation as matched. A justification is required.',
  DISPUTE: 'Record a dispute on this confirmation. Describe the disagreement.',
  CANCEL: 'Cancel this confirmation. If already sent, a cancellation notice will be issued to the client.',
}

export default function TradePage() {
  const id = Number(useParams().id)
  const { data: trade, isLoading, error } = useTrade(id)
  const [tab, setTab] = useState('overview')

  if (error) return <Alert severity="error">{errorMessage(error)}</Alert>
  if (isLoading || !trade) {
    return (
      <Box sx={{ display: 'grid', gap: 2 }}>
        <Skeleton variant="rounded" height={80} />
        <Skeleton variant="rounded" height={120} />
        <Skeleton variant="rounded" height={400} />
      </Box>
    )
  }
  const c = trade.confirmation
  const latestMatch = trade.matches[0]

  return (
    <>
      <PageHeader
        crumbs={[{ label: 'Confirmations', to: '/confirmations' }, { label: trade.tradeRef }]}
        title={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
            <span className="mono" style={{ fontSize: '0.92em' }}>{trade.tradeRef}</span>
            <StatusChip status={c.status} size="medium" />
            {trade.economicVersion > 1 && <Chip label={`Amended · v${trade.economicVersion}`} color="warning" variant="outlined" size="small" />}
            {c.escalated && !['CONFIRMED', 'CANCELLED'].includes(c.status) && <Chip label="Escalated" color="error" size="small" />}
          </Box>
        }
        subtitle={`${trade.productName} · ${trade.currencyPair} · ${trade.counterparty.legalName} · FO ref ${trade.externalRef}`}
      />

      <ActionBar trade={trade} />

      <Paper sx={{ p: 2, mb: 2 }}>
        <LifecycleStepper status={c.status} />
      </Paper>

      {trade.enrichmentIssues.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <AlertTitle>Enrichment exception</AlertTitle>
          {trade.enrichmentIssues.join(' · ')}. Update the counterparty's static data, then enrich again.{' '}
          <RouterLink to={`/counterparties/${trade.counterparty.id}`}>Open counterparty</RouterLink>
        </Alert>
      )}
      {c.disputeReason && ['DISPUTED', 'MISMATCHED'].includes(c.status) && (
        <Alert severity="error" sx={{ mb: 2 }}><AlertTitle>Dispute</AlertTitle>{c.disputeReason}</Alert>
      )}
      {c.status === 'MISMATCHED' && latestMatch && (
        <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" onClick={() => setTab('matching')}>View breaks</Button>}>
          Client document disagrees on {latestMatch.fields.filter((f) => f.status !== 'MATCH').map((f) => f.label).join(', ')}.
          Amend the trade if our booking is wrong, or chase the client for corrected paper.
        </Alert>
      )}
      {c.slaBreached && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Client response SLA breached {fromNow(c.slaDueAt)} ({c.chaserCount} chaser{c.chaserCount === 1 ? '' : 's'} sent).
        </Alert>
      )}

      <Paper sx={{ overflow: 'hidden' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="overview" label="Overview" />
          <Tab value="settlement" label="Settlement" />
          <Tab value="documents" label={`Documents (${trade.documents.length})`} />
          <Tab value="matching" label={<Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center' }}>Matching {latestMatch && <ToneChip value={latestMatch.outcome} />}</Box>} />
          <Tab value="activity" label={`Audit trail (${trade.timeline.length})`} />
          <Tab value="messages" label={`Communications (${trade.messages.length})`} />
        </Tabs>
        <Box sx={{ p: { xs: 1.5, md: 2.5 } }}>
          {tab === 'overview' && <Overview trade={trade} />}
          {tab === 'settlement' && <SettlementTable trade={trade} />}
          {tab === 'documents' && <DocumentList documents={trade.documents} contentUrl={(d) => `/documents/${d.id}/content`} />}
          {tab === 'matching' && (
            trade.matches.length ? (
              <Box sx={{ display: 'grid', gap: 3 }}>
                {trade.matches.map((m, i) => (
                  <Box key={m.id}>
                    <Typography variant="overline" color="text.secondary">{i === 0 ? 'Latest result' : `Earlier result #${trade.matches.length - i}`}</Typography>
                    <MatchResultView match={m} />
                    {i < trade.matches.length - 1 && <Divider sx={{ mt: 3 }} />}
                  </Box>
                ))}
              </Box>
            ) : <EmptyState title="No client document received yet" hint="When the client returns their confirmation it is matched field-by-field against our booking." />
          )}
          {tab === 'activity' && <AuditTimeline events={trade.timeline} />}
          {tab === 'messages' && <Messages trade={trade} />}
        </Box>
      </Paper>
    </>
  )
}

function Overview({ trade }: { trade: TradeDetail }) {
  const c = trade.confirmation
  return (
    <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', lg: '1.3fr 1fr' } }}>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Economic terms (as confirmed)</Typography>
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}><TermsTable terms={trade.terms} /></Paper>
      </Box>
      <Box sx={{ display: 'grid', gap: 2, alignContent: 'start' }}>
        <Section title="Counterparty">
          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: '1fr 1fr' }}>
            <Box sx={{ gridColumn: '1 / -1' }}>
              <Field label="Legal name">
                <RouterLink to={`/counterparties/${trade.counterparty.id}`}>{trade.counterparty.legalName}</RouterLink>
              </Field>
            </Box>
            <Field label="LEI" mono>{trade.counterparty.lei}</Field>
            <Field label="Delivery channel">{trade.counterparty.deliveryChannel === 'PORTAL' ? 'Client portal' : 'Email'}</Field>
            <Field label="Response SLA">{trade.counterparty.confirmationSlaHours} hours</Field>
            <Field label="Master agreement">{trade.counterparty.masterAgreement?.replace('the ', '')}</Field>
          </Box>
        </Section>
        <Section title="Confirmation">
          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: '1fr 1fr' }}>
            <Field label="Status">{c.statusDescription}</Field>
            <Field label="Since">{fmtDateTime(c.statusChangedAt)}</Field>
            <Field label="Prepared by">{c.preparedBy}</Field>
            <Field label="Approved by">{c.approvedBy}</Field>
            <Field label="Sent">{fmtDateTime(c.sentAt)}</Field>
            <Field label="Response due">{fmtDateTime(c.slaDueAt)}</Field>
            <Field label="Chasers">{c.chaserCount}{c.lastChasedAt ? ` · last ${fromNow(c.lastChasedAt)}` : ''}</Field>
            <Field label="Confirmed">{fmtDateTime(c.confirmedAt)}</Field>
          </Box>
        </Section>
        <Section title="Booking">
          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: '1fr 1fr' }}>
            <Field label="Source system">{trade.sourceSystem}</Field>
            <Field label="Channel">{trade.ingestionChannel.replace('_', ' ')}</Field>
            <Field label="Book">{trade.book}</Field>
            <Field label="Trader">{trade.trader}</Field>
            <Field label="Executed">{fmtDateTime(trade.executionTime)}</Field>
            <Field label="Booked">{fmtDateTime(trade.createdAt)}</Field>
          </Box>
        </Section>
      </Box>
    </Box>
  )
}

function Messages({ trade }: { trade: TradeDetail }) {
  const [open, setOpen] = useState<number | null>(trade.messages[0]?.id ?? null)
  if (!trade.messages.length) return <EmptyState title="No client communications yet" />
  return (
    <Box sx={{ display: 'grid', gap: 1 }}>
      {trade.messages.map((m) => (
        <Paper key={m.id} variant="outlined" sx={{ overflow: 'hidden' }}>
          <Box onClick={() => setOpen(open === m.id ? null : m.id)} sx={{ px: 2, py: 1.25, cursor: 'pointer', display: 'flex', gap: 1, alignItems: 'center', '&:hover': { bgcolor: 'action.hover' } }}>
            <ToneChip value={m.messageType} />
            <Typography variant="body2" sx={{ fontWeight: 600, flex: 1, minWidth: 0 }} noWrap>{m.subject}</Typography>
            <Typography variant="caption" color="text.secondary">{fmtDateTime(m.createdAt)}</Typography>
          </Box>
          {open === m.id && (
            <Box sx={{ px: 2, pb: 2 }}>
              <Typography variant="caption" color="text.secondary" component="div">To: {m.recipients}{m.cc ? ` · Cc: ${m.cc}` : ''} · by {m.createdBy}</Typography>
              <Box component="pre" sx={{ m: 0, mt: 1, p: 1.5, bgcolor: 'action.hover', borderRadius: 1, whiteSpace: 'pre-wrap', fontFamily: 'inherit', fontSize: 13 }}>{m.body}</Box>
            </Box>
          )}
        </Paper>
      ))}
    </Box>
  )
}

function ActionBar({ trade }: { trade: TradeDetail }) {
  const action = useTradeAction(trade.id)
  const notify = useNotify()
  const qc = useQueryClient()
  const [pending, setPending] = useState<AllowedAction | null>(null)
  const [comment, setComment] = useState('')
  const [amend, setAmend] = useState<AmendRequest>({})
  const [simEl, setSimEl] = useState<HTMLElement | null>(null)
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const open = (a: AllowedAction) => {
    setComment('')
    setAmend({
      valueDate: trade.valueDate, buyAmount: trade.buyAmount, sellAmount: trade.sellAmount, rate: trade.rate,
      farValueDate: trade.farValueDate, farBuyAmount: trade.farBuyAmount, farSellAmount: trade.farSellAmount, farRate: trade.farRate,
      fixingDate: trade.fixingDate, fixingSource: trade.fixingSource,
    })
    setPending(a)
  }

  const run = async () => {
    if (!pending) return
    try {
      await action.mutateAsync({ action: pending.action, comment: comment || undefined, amendment: pending.action === 'AMEND' ? amend : undefined })
      notify(`${pending.label} completed`)
      setPending(null)
    } catch (e) {
      notify(errorMessage(e), 'error')
    }
  }

  const refresh = () => qc.invalidateQueries()

  const showResult = (r: InboundResult) => {
    notify(`${r.fileName}: ${r.status.replace('_', ' ').toLowerCase()} – ${r.note}`, r.status === 'MATCHED' ? 'success' : r.status === 'MISMATCHED' ? 'warning' : 'info')
  }

  const simulate = async (breakField: string, deliver: boolean) => {
    setSimEl(null)
    setBusy(true)
    try {
      if (deliver) {
        const { data } = await api.post<InboundResult>('/inbound/simulate', { tradeId: trade.id, breakField, deliver })
        showResult(data)
        await refresh()
      } else {
        const res = await api.post('/inbound/simulate', { tradeId: trade.id, breakField, deliver }, { responseType: 'blob' })
        const url = URL.createObjectURL(res.data as Blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `${trade.counterparty.shortCode}_confirmation_${trade.tradeRef}.pdf`
        a.click()
        setTimeout(() => URL.revokeObjectURL(url), 5000)
      }
    } catch (e) {
      notify(errorMessage(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  const upload = async (file: File) => {
    setBusy(true)
    try {
      const form = new FormData()
      form.append('files', file)
      form.append('tradeId', String(trade.id))
      const { data } = await api.post<InboundResult[]>('/inbound/upload', form)
      data.forEach(showResult)
      await refresh()
    } catch (e) {
      notify(errorMessage(e), 'error')
    } finally {
      setBusy(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const latestFinal = trade.documents.find((d) => d.direction === 'OUTBOUND' && d.stage === 'FINAL')
  const latestDraft = trade.documents.find((d) => d.direction === 'OUTBOUND')
  const matchable = MATCHABLE.includes(trade.confirmation.status)
  const sorted = [...trade.allowedActions].sort((a, b) => Number(PRIMARY.includes(b.action)) - Number(PRIMARY.includes(a.action)))

  return (
    <Paper sx={{ p: 1.5, mb: 2, display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
      {sorted.length === 0 && !matchable && (
        <Typography variant="body2" color="text.secondary" sx={{ px: 0.5 }}>
          {trade.confirmation.status === 'CONFIRMED' ? 'Confirmation complete – no further action required.'
            : trade.confirmation.status === 'CANCELLED' ? 'Cancelled – no further action possible.'
            : 'No actions available for your role at this stage.'}
        </Typography>
      )}
      {sorted.map((a) => (
        <Tooltip key={a.action} title={a.disabledReason ?? ''}>
          <span>
            <Button
              variant={PRIMARY.includes(a.action) ? 'contained' : 'outlined'}
              color={DANGER.includes(a.action) ? 'error' : 'primary'}
              disabled={!a.enabled || action.isPending}
              onClick={() => open(a)}
            >
              {a.label}
            </Button>
          </span>
        </Tooltip>
      ))}
      {matchable && (
        <>
          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
          <input ref={fileRef} type="file" accept="application/pdf" hidden onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
          <Button variant="outlined" startIcon={<UploadFileOutlinedIcon />} disabled={busy} onClick={() => fileRef.current?.click()}>
            Upload client response
          </Button>
          <Button variant="text" startIcon={<ScienceOutlinedIcon />} endIcon={<ArrowDropDownIcon />} disabled={busy} onClick={(e) => setSimEl(e.currentTarget)}>
            Simulate client
          </Button>
          <Menu anchorEl={simEl} open={!!simEl} onClose={() => setSimEl(null)}>
            <MenuItem disabled><Typography variant="overline">Receive counterparty paper now</Typography></MenuItem>
            <MenuItem onClick={() => simulate('NONE', true)}><ListItemText primary="Matching confirmation" secondary="All terms agree" /></MenuItem>
            <MenuItem onClick={() => simulate('RATE', true)}><ListItemText primary="Break: exchange rate" secondary="Client rate differs by 15 pips" /></MenuItem>
            <MenuItem onClick={() => simulate('VALUE_DATE', true)}><ListItemText primary="Break: value date" secondary="Client settles one business day later" /></MenuItem>
            <MenuItem onClick={() => simulate('AMOUNT', true)}><ListItemText primary="Break: amount" secondary="Client notional differs" /></MenuItem>
            <Divider />
            <MenuItem onClick={() => simulate('NONE', false)}><ListItemText primary="Download counterparty paper" secondary="To test upload or the inbox folder" /></MenuItem>
          </Menu>
        </>
      )}
      <Box sx={{ flex: 1 }} />
      {(latestFinal ?? latestDraft) && (
        <Button variant="text" onClick={() => {
          const d = latestFinal ?? latestDraft!
          void downloadFile(`/documents/${d.id}/content?download=true`, d.fileName)
        }}>
          Download {latestFinal ? 'final' : 'draft'} PDF
        </Button>
      )}

      <Dialog open={!!pending} onClose={() => setPending(null)} maxWidth={pending?.action === 'AMEND' ? 'sm' : 'xs'} fullWidth>
        <DialogTitle>{pending?.label} · <span className="mono">{trade.tradeRef}</span></DialogTitle>
        <DialogContent>
          {pending?.action === 'AMEND' ? (
            <AmendForm trade={trade} value={amend} onChange={setAmend} />
          ) : (
            <DialogContentText sx={{ mb: 2 }}>{CONFIRM_TEXT[pending?.action ?? 'GENERATE']}</DialogContentText>
          )}
          <TextField
            label={pending?.commentRequired ? 'Comment (required)' : 'Comment (optional)'}
            fullWidth
            multiline
            minRows={2}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            sx={{ mt: pending?.action === 'AMEND' ? 2 : 0 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPending(null)}>Cancel</Button>
          <Button
            variant="contained"
            color={pending && DANGER.includes(pending.action) ? 'error' : 'primary'}
            disabled={action.isPending || (pending?.commentRequired && !comment.trim())}
            onClick={run}
          >
            {action.isPending ? 'Working…' : pending?.label}
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}

function AmendForm({ trade, value, onChange }: { trade: TradeDetail; value: AmendRequest; onChange: (v: AmendRequest) => void }) {
  const num = (k: keyof AmendRequest) => (e: React.ChangeEvent<HTMLInputElement>) =>
    onChange({ ...value, [k]: e.target.value === '' ? undefined : Number(e.target.value) })
  const str = (k: keyof AmendRequest) => (e: React.ChangeEvent<HTMLInputElement>) => onChange({ ...value, [k]: e.target.value || undefined })
  return (
    <Box>
      <Alert severity="info" sx={{ mb: 2 }}>
        Amending economics creates version v{trade.economicVersion + 1}, regenerates the confirmation as a draft and requires
        fresh four-eyes approval before re-sending. Current: {trade.buyCurrency} {fmtAmount(trade.buyAmount, trade.buyCurrency)} /
        {' '}{trade.sellCurrency} {fmtAmount(trade.sellAmount, trade.sellCurrency)} @ {fmtRate(trade.rate)}.
      </Alert>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
        <TextField label={trade.productType === 'FX_SWAP' ? 'Near value date' : 'Value date'} type="date" value={value.valueDate ?? ''} onChange={str('valueDate')} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label={trade.productType === 'FX_SWAP' ? 'Near rate' : 'Exchange rate'} type="number" value={value.rate ?? ''} onChange={num('rate')} />
        <TextField label={`We buy (${trade.buyCurrency})`} type="number" value={value.buyAmount ?? ''} onChange={num('buyAmount')} />
        <TextField label={`We sell (${trade.sellCurrency})`} type="number" value={value.sellAmount ?? ''} onChange={num('sellAmount')} />
        {trade.productType === 'FX_SWAP' && (
          <>
            <TextField label="Far value date" type="date" value={value.farValueDate ?? ''} onChange={str('farValueDate')} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField label="Far rate" type="number" value={value.farRate ?? ''} onChange={num('farRate')} />
            <TextField label={`Far leg: we buy (${trade.sellCurrency})`} type="number" value={value.farBuyAmount ?? ''} onChange={num('farBuyAmount')} />
            <TextField label={`Far leg: we sell (${trade.buyCurrency})`} type="number" value={value.farSellAmount ?? ''} onChange={num('farSellAmount')} />
          </>
        )}
        {trade.productType === 'FX_NDF' && (
          <>
            <TextField label="Fixing date" type="date" value={value.fixingDate ?? ''} onChange={str('fixingDate')} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField label="Settlement rate option" value={value.fixingSource ?? ''} onChange={str('fixingSource')} />
          </>
        )}
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        Amounts must remain consistent with the rate (market quotation convention, 0.1% tolerance).
      </Typography>
    </Box>
  )
}
