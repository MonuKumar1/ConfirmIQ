import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItemButton, Paper, Skeleton,
  TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useBlotter, useTrade, useTradeAction } from '../api/hooks'
import { fmtAmount, fmtRate, fromNow, PRODUCT_LABEL } from '../app/format'
import { useNotify } from '../app/notify'
import { useAuth } from '../auth/AuthContext'
import { PageHeader } from '../components/PageHeader'
import { PdfFrame } from '../components/PdfViewer'
import { EmptyState } from '../components/Section'
import { TermsTable } from '../components/trade'

export default function ApprovalsPage() {
  const { data, isLoading } = useBlotter({ queue: 'PENDING_APPROVAL', page: 0, size: 100, sort: 'statusChangedAt,asc' })
  const rows = data?.content ?? []
  const [picked, setSelected] = useState<number>()
  const { user } = useAuth()
  const canApprove = user?.role === 'CHECKER' || user?.role === 'SUPERVISOR'
  // Keep the picked item while it is still queued; otherwise fall back to the oldest.
  const selected = rows.some((r) => r.tradeId === picked) ? picked : rows[0]?.tradeId

  return (
    <>
      <PageHeader
        title="Approvals"
        subtitle="Four-eyes review of draft confirmations before they are released to clients. The preparer of a draft cannot approve it."
      />
      {!canApprove && <Alert severity="info" sx={{ mb: 2 }}>You are signed in as a maker – you can review the queue but approvals require a checker or supervisor.</Alert>}
      {isLoading ? (
        <Skeleton variant="rounded" height={500} />
      ) : rows.length === 0 ? (
        <Paper><EmptyState icon={<FactCheckOutlinedIcon />} title="Approval queue is empty" hint="Drafts submitted by makers will appear here." /></Paper>
      ) : (
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '320px 1fr' }, alignItems: 'start' }}>
          <Paper sx={{ overflow: 'hidden' }}>
            <Box sx={{ px: 2, py: 1.25, borderBottom: 1, borderColor: 'divider' }}>
              <Typography variant="subtitle2">{rows.length} awaiting approval</Typography>
            </Box>
            <List disablePadding sx={{ maxHeight: { lg: 'calc(100vh - 260px)' }, overflowY: 'auto' }}>
              {rows.map((r) => (
                <ListItemButton key={r.tradeId} selected={r.tradeId === selected} onClick={() => setSelected(r.tradeId)} sx={{ display: 'block', borderBottom: 1, borderColor: 'divider', py: 1.25 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                    <Typography variant="body2" className="mono" sx={{ fontWeight: 600 }}>{r.tradeRef}</Typography>
                    <Typography variant="caption" color="text.secondary">{fromNow(r.statusChangedAt)}</Typography>
                  </Box>
                  <Typography variant="body2" noWrap>{r.counterparty}</Typography>
                  <Box sx={{ display: 'flex', gap: 0.75, mt: 0.5, alignItems: 'center' }}>
                    <Chip size="small" label={PRODUCT_LABEL[r.productType]} variant="outlined" />
                    {r.economicVersion > 1 && <Chip size="small" label={`Amendment v${r.economicVersion}`} color="warning" variant="outlined" />}
                    <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>by {r.preparedBy}</Typography>
                  </Box>
                </ListItemButton>
              ))}
            </List>
          </Paper>
          {selected && <ApprovalReview tradeId={selected} canApprove={canApprove} />}
        </Box>
      )}
    </>
  )
}

function ApprovalReview({ tradeId, canApprove }: { tradeId: number; canApprove: boolean }) {
  const { data: trade } = useTrade(tradeId)
  const action = useTradeAction(tradeId)
  const notify = useNotify()
  const [rejecting, setRejecting] = useState(false)
  const [comment, setComment] = useState('')

  if (!trade) return <Skeleton variant="rounded" height={600} />
  const draft = trade.documents.find((d) => d.direction === 'OUTBOUND' && d.stage === 'DRAFT')
  const approve = trade.allowedActions.find((a) => a.action === 'APPROVE')

  const act = async (kind: 'APPROVE' | 'REJECT') => {
    try {
      await action.mutateAsync({ action: kind, comment: comment || undefined })
      notify(kind === 'APPROVE' ? `${trade.tradeRef} approved – final confirmation issued` : `${trade.tradeRef} returned to maker`)
      setRejecting(false)
      setComment('')
    } catch (e) {
      notify(errorMessage(e), 'error')
    }
  }

  return (
    <Paper sx={{ overflow: 'hidden' }}>
      <Box sx={{ px: 2, py: 1.5, display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', borderBottom: 1, borderColor: 'divider' }}>
        <Box sx={{ flex: 1, minWidth: 200 }}>
          <Typography variant="subtitle1" className="mono">{trade.tradeRef}</Typography>
          <Typography variant="caption" color="text.secondary">
            {trade.productName} · {trade.counterparty.legalName} · {trade.buyCurrency} {fmtAmount(trade.buyAmount, trade.buyCurrency)} / {trade.sellCurrency} {fmtAmount(trade.sellAmount, trade.sellCurrency)} @ {fmtRate(trade.rate)}
          </Typography>
        </Box>
        <Button component={RouterLink} to={`/trades/${trade.id}`} endIcon={<OpenInNewIcon />}>Workspace</Button>
        {canApprove && (
          <>
            <Button variant="outlined" color="error" onClick={() => setRejecting(true)} disabled={action.isPending}>Reject</Button>
            <Button variant="contained" color="success" disabled={!approve?.enabled || action.isPending} onClick={() => act('APPROVE')} title={approve?.disabledReason}>
              Approve & issue final
            </Button>
          </>
        )}
      </Box>
      {approve && !approve.enabled && <Alert severity="warning" sx={{ borderRadius: 0 }}>{approve.disabledReason}</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', xl: '360px 1fr' } }}>
        <Box sx={{ p: 2, borderRight: { xl: 1 }, borderColor: { xl: 'divider' } }}>
          <Typography variant="overline" color="text.secondary">Booked terms – verify against the draft</Typography>
          <Paper variant="outlined" sx={{ mt: 1, overflow: 'hidden' }}><TermsTable terms={trade.terms} /></Paper>
          <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mt: 2 }}>Checklist</Typography>
          {['Economic terms agree with front-office booking', 'Settlement instructions are current for both parties', 'Correct template and legal entity', 'Amendment reason documented (if applicable)'].map((c) => (
            <Typography key={c} variant="body2" sx={{ display: 'flex', gap: 1, py: 0.25 }}>☐ {c}</Typography>
          ))}
        </Box>
        <Box sx={{ p: 1.5, height: { xs: 640, xl: 'calc(100vh - 300px)' }, minHeight: 520, order: { xs: -1, xl: 0 } }}>
          {draft ? <PdfFrame src={`/documents/${draft.id}/content`} /> : <Alert severity="warning">No draft document found.</Alert>}
        </Box>
      </Box>

      <Dialog open={rejecting} onClose={() => setRejecting(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Reject draft</DialogTitle>
        <DialogContent>
          <TextField autoFocus fullWidth multiline minRows={3} label="Reason (required)" value={comment} onChange={(e) => setComment(e.target.value)} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejecting(false)}>Cancel</Button>
          <Button variant="contained" color="error" disabled={!comment.trim() || action.isPending} onClick={() => act('REJECT')}>Reject</Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}
