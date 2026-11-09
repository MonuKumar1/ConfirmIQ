import { Box, Chip, Typography } from '@mui/material'
import type { AuditView } from '../api/types'
import { fmtDateTime, humanize } from '../app/format'

const ACTION_TONE: Record<string, string> = {
  TRADE_CAPTURED: 'info.main', APPROVE: 'success.main', CONFIRM: 'success.main', AFFIRM: 'success.main',
  INBOUND_MATCHED: 'success.main', INBOUND_MISMATCHED: 'error.main', DISPUTE: 'error.main', REJECT: 'warning.main',
  ENRICHMENT_EXCEPTION: 'warning.main', ESCALATED: 'error.main', CANCEL: 'text.disabled', TRADE_AMENDED: 'warning.main',
  CHASER_SENT: 'warning.main',
}

const ACTION_LABEL: Record<string, string> = {
  TRADE_CAPTURED: 'Trade captured', ENRICH: 'Enriched', GENERATE: 'Draft generated', SUBMIT: 'Submitted for approval',
  APPROVE: 'Approved (four-eyes)', REJECT: 'Rejected by checker', SEND: 'Sent to client', CHASE: 'Chased',
  CHASER_SENT: 'Chaser sent', CONFIRM: 'Marked confirmed', AFFIRM: 'Affirmed by client', DISPUTE: 'Dispute raised',
  AMEND: 'Amended', TRADE_AMENDED: 'Economics amended', CANCEL: 'Cancelled', FORCE_MATCH: 'Break accepted',
  DOCUMENT_GENERATED: 'Document generated', INBOUND_RECEIVED: 'Client document received',
  INBOUND_MATCHED: 'Matched', INBOUND_MISMATCHED: 'Mismatch detected', INBOUND_LINKED: 'Document linked manually',
  ENRICHMENT_EXCEPTION: 'Enrichment exception', ESCALATED: 'Escalated',
}

export function AuditTimeline({ events }: { events: AuditView[] }) {
  return (
    <Box sx={{ position: 'relative', pl: 3 }}>
      <Box sx={{ position: 'absolute', left: 7, top: 6, bottom: 6, width: 2, bgcolor: 'divider' }} />
      {[...events].reverse().map((e) => (
        <Box key={e.id} sx={{ position: 'relative', pb: 2 }}>
          <Box sx={{ position: 'absolute', left: -21, top: 4, width: 12, height: 12, borderRadius: '50%', border: 2, borderColor: 'background.paper', bgcolor: ACTION_TONE[e.action] ?? 'primary.main', boxShadow: (t) => `0 0 0 1px ${t.palette.divider}` }} />
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{ACTION_LABEL[e.action] ?? humanize(e.action)}</Typography>
            {e.fromStatus && e.toStatus && e.fromStatus !== e.toStatus && (
              <Typography variant="caption" color="text.secondary" className="mono">{e.fromStatus} → {e.toStatus}</Typography>
            )}
            <Box sx={{ flex: 1 }} />
            <Typography variant="caption" color="text.secondary">{fmtDateTime(e.occurredAt)}</Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center', mt: 0.25 }}>
            <Chip size="small" label={e.actor} variant="outlined" sx={{ height: 18, fontSize: 10.5 }} />
            {e.actorRole && <Typography variant="caption" color="text.secondary">{humanize(e.actorRole)}</Typography>}
          </Box>
          {e.details && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{e.details}</Typography>}
          {e.comment && (
            <Typography variant="body2" sx={{ mt: 0.5, pl: 1.25, borderLeft: 3, borderColor: 'divider', fontStyle: 'italic' }}>
              “{e.comment}”
            </Typography>
          )}
        </Box>
      ))}
    </Box>
  )
}
