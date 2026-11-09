import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined'
import {
  Alert, AlertTitle, Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Paper, Skeleton,
  TextField, Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { usePortalTrade } from '../api/hooks'
import type { InboundResult } from '../api/types'
import { fmtDateTime } from '../app/format'
import { useNotify } from '../app/notify'
import { PageHeader } from '../components/PageHeader'
import { PdfFrame } from '../components/PdfViewer'
import { Section } from '../components/Section'
import { DocumentList, SettlementTable, TermsTable } from '../components/trade'

export default function PortalTradePage() {
  const id = Number(useParams().id)
  const { data: trade, error } = usePortalTrade(id)
  const qc = useQueryClient()
  const notify = useNotify()
  const [disputing, setDisputing] = useState(false)
  const [affirming, setAffirming] = useState(false)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  if (error) return <Alert severity="error">{errorMessage(error)}</Alert>
  if (!trade) return <Skeleton variant="rounded" height={600} />

  const status = trade.confirmation.status
  const canAffirm = trade.allowedActions.some((a) => a.action === 'AFFIRM')
  const canDispute = trade.allowedActions.some((a) => a.action === 'DISPUTE')
  const finalDoc = trade.documents.find((d) => d.direction === 'OUTBOUND' && d.stage === 'FINAL' && d.docType !== 'CANCELLATION')

  const refresh = async () => {
    await qc.invalidateQueries({ queryKey: ['portal-trade', id] })
    await qc.invalidateQueries({ queryKey: ['portal'] })
  }

  const run = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true)
    try {
      await fn()
      notify(message)
      await refresh()
      setDisputing(false)
      setAffirming(false)
    } catch (e) {
      notify(errorMessage(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  const upload = (file: File) => run(async () => {
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post<InboundResult>(`/portal/confirmations/${id}/upload`, form)
    if (data.status === 'MISMATCHED') notify('Your document differs from our records – our operations team will contact you.', 'warning')
  }, 'Document received')

  return (
    <>
      <PageHeader
        crumbs={[{ label: 'Confirmations', to: '/portal' }, { label: trade.tradeRef }]}
        title={<span className="mono">{trade.tradeRef}</span>}
        subtitle={`${trade.productName} · ${trade.currencyPair} · issued ${fmtDateTime(trade.confirmation.sentAt)}`}
      />

      {status === 'AWAITING_RESPONSE' && (
        <Alert severity={trade.confirmation.slaBreached ? 'warning' : 'info'} sx={{ mb: 2 }}>
          <AlertTitle>Your response is required{trade.confirmation.slaDueAt ? ` by ${fmtDateTime(trade.confirmation.slaDueAt)}` : ''}</AlertTitle>
          Review the terms and settlement instructions below. Affirm electronically if they are correct, raise a dispute if not,
          or upload your signed copy.
        </Alert>
      )}
      {status === 'CONFIRMED' && <Alert severity="success" sx={{ mb: 2 }}>This transaction is confirmed. Thank you.</Alert>}
      {(status === 'DISPUTED' || status === 'MISMATCHED') && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <AlertTitle>Under review by Meridian operations</AlertTitle>
          {trade.confirmation.disputeReason ?? 'The document received differs from our records. Our team will contact you shortly.'}
        </Alert>
      )}
      {status === 'MATCHED' && <Alert severity="success" sx={{ mb: 2 }}>Your document matches our records. You may affirm now to complete confirmation.</Alert>}

      {(canAffirm || canDispute || status === 'AWAITING_RESPONSE') && (
        <Paper sx={{ p: 1.5, mb: 2, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {canAffirm && <Button variant="contained" color="success" startIcon={<CheckCircleOutlineIcon />} disabled={busy} onClick={() => setAffirming(true)}>Affirm terms</Button>}
          {canDispute && <Button variant="outlined" color="error" startIcon={<ReportProblemOutlinedIcon />} disabled={busy} onClick={() => setDisputing(true)}>Raise dispute</Button>}
          {status === 'AWAITING_RESPONSE' && (
            <>
              <input ref={fileRef} type="file" accept="application/pdf" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(f); e.target.value = '' }} />
              <Button variant="outlined" startIcon={<UploadFileOutlinedIcon />} disabled={busy} onClick={() => fileRef.current?.click()}>Upload signed copy</Button>
            </>
          )}
        </Paper>
      )}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '1fr 1.15fr' }, alignItems: 'start', mb: 2 }}>
        <Section title="Economic terms" subtitle="From your perspective" noPadding>
          <TermsTable terms={trade.terms} counterpartyView />
        </Section>
        <Section title="Confirmation document" noPadding>
          <Box sx={{ height: { xs: 560, lg: 640 }, p: 1 }}>
            {finalDoc ? <PdfFrame src={`/portal/documents/${finalDoc.id}/content`} /> : <Typography sx={{ p: 2 }} color="text.secondary">No document available.</Typography>}
          </Box>
        </Section>
      </Box>
      <Box sx={{ display: 'grid', gap: 2 }}>
        <Section title="Settlement instructions" subtitle="Please verify both parties' accounts before value date" noPadding>
          <SettlementTable trade={trade} cptyLabel="You" />
        </Section>
        <Section title="Documents" noPadding>
          <DocumentList documents={trade.documents} contentUrl={(d) => `/portal/documents/${d.id}/content`} />
        </Section>
      </Box>

      <Dialog open={affirming} onClose={() => setAffirming(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Affirm confirmation</DialogTitle>
        <DialogContent>
          <DialogContentText>
            By affirming you confirm, on behalf of your organisation, that the economic terms and settlement instructions of
            <strong className="mono"> {trade.tradeRef}</strong> are correct. This is recorded in the audit trail.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAffirming(false)}>Cancel</Button>
          <Button variant="contained" color="success" disabled={busy} onClick={() => run(() => api.post(`/portal/confirmations/${id}/affirm`), 'Confirmation affirmed')}>Affirm</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={disputing} onClose={() => setDisputing(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Raise a dispute</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>Describe which terms differ from your records and the values you expect.</DialogContentText>
          <TextField autoFocus fullWidth multiline minRows={4} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Our booking shows an exchange rate of 1.0868, not 1.0853." />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDisputing(false)}>Cancel</Button>
          <Button variant="contained" color="error" disabled={busy || !reason.trim()} onClick={() => run(() => api.post(`/portal/confirmations/${id}/dispute`, { reason }), 'Dispute submitted to Meridian operations')}>Submit dispute</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
