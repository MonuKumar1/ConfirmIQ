import CallMadeIcon from '@mui/icons-material/CallMade'
import CallReceivedIcon from '@mui/icons-material/CallReceived'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import ErrorIcon from '@mui/icons-material/Error'
import HelpOutlineIcon from '@mui/icons-material/HelpOutlineOutlined'
import QrCode2Icon from '@mui/icons-material/QrCode2'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import {
  Box, Chip, IconButton, LinearProgress, Step, StepLabel, Stepper, Table, TableBody, TableCell, TableHead, TableRow, Tooltip,
  Typography, useMediaQuery, type Theme,
} from '@mui/material'
import { useState } from 'react'
import type { ConfirmationStatus, DocumentView, MatchView, TermField, TradeDetail } from '../api/types'
import { fmtBytes, fmtDateTime, humanize } from '../app/format'
import { Hash } from './Mono'
import { PdfDialog } from './PdfViewer'
import { EmptyState } from './Section'
import { ToneChip } from './StatusChip'

/** Economic terms as printed on the confirmation. */
export function TermsTable({ terms, counterpartyView }: { terms: TermField[]; counterpartyView?: boolean }) {
  return (
    <Table size="small">
      <TableBody>
        {terms.map((t) => (
          <TableRow key={t.key}>
            <TableCell sx={{ color: 'text.secondary', width: '42%', bgcolor: 'action.hover' }}>
              {counterpartyView ? t.counterpartyLabel.replace('We ', 'You ').replace('Your Reference', 'Meridian Reference') : t.label}
            </TableCell>
            <TableCell sx={{ fontWeight: 600 }} className={t.type === 'TEXT' && t.key !== 'tradeRef' ? undefined : 'mono'}>
              {t.value}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export function SettlementTable({ trade, houseLabel = 'Meridian Capital', cptyLabel }: {
  trade: TradeDetail
  houseLabel?: string
  cptyLabel?: string
}) {
  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Ccy</TableCell>
            <TableCell>Beneficiary</TableCell>
            <TableCell>Account with</TableCell>
            <TableCell>BIC</TableCell>
            <TableCell>Account</TableCell>
            <TableCell>Intermediary</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {trade.settlement.flatMap((s) =>
            ([['house', s.house, houseLabel], ['cpty', s.counterparty, cptyLabel ?? trade.counterparty.shortCode]] as const).map(([k, ssi, who]) => (
              <TableRow key={s.currency + k}>
                <TableCell><Chip size="small" label={s.currency} variant="outlined" /></TableCell>
                {ssi ? (
                  <>
                    <TableCell>
                      <Typography variant="body2">{ssi.beneficiaryName}</Typography>
                      <Typography variant="caption" color="text.secondary">{who} receives {s.currency}</Typography>
                    </TableCell>
                    <TableCell>{ssi.bankName}</TableCell>
                    <TableCell className="mono">{ssi.bankBic}</TableCell>
                    <TableCell className="mono">{ssi.accountNumber}</TableCell>
                    <TableCell className="mono">{ssi.intermediaryBic ?? '—'}</TableCell>
                  </>
                ) : (
                  <TableCell colSpan={5}>
                    <Typography variant="body2" color="error.main">Missing {who} SSI for {s.currency} – enrichment blocked</Typography>
                  </TableCell>
                )}
              </TableRow>
            )),
          )}
        </TableBody>
      </Table>
    </Box>
  )
}

const DOC_LABEL: Record<string, string> = {
  CONFIRMATION: 'Confirmation',
  AMENDED_CONFIRMATION: 'Amended confirmation',
  CANCELLATION: 'Cancellation notice',
  CLIENT_RESPONSE: 'Client response',
  REPORT: 'Report',
}

export function DocumentList({ documents, contentUrl }: { documents: DocumentView[]; contentUrl: (d: DocumentView) => string }) {
  const [open, setOpen] = useState<DocumentView | null>(null)
  if (!documents.length) return <EmptyState title="No documents yet" hint="Documents appear here once a draft is generated or a client response is received." />
  return (
    <>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell />
              <TableCell>Document</TableCell>
              <TableCell>Stage</TableCell>
              <TableCell>Source / result</TableCell>
              <TableCell>SHA-256</TableCell>
              <TableCell>Created</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {documents.map((d) => (
              <TableRow key={d.id} hover sx={{ cursor: 'pointer' }} onClick={() => setOpen(d)}>
                <TableCell sx={{ width: 32, pr: 0 }}>
                  <Tooltip title={d.direction === 'OUTBOUND' ? 'Outbound (house paper)' : 'Inbound (client paper)'}>
                    {d.direction === 'OUTBOUND' ? <CallMadeIcon fontSize="small" color="primary" /> : <CallReceivedIcon fontSize="small" color="secondary" />}
                  </Tooltip>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {DOC_LABEL[d.docType]} {d.direction === 'OUTBOUND' && <Typography component="span" variant="caption" color="text.secondary">v{d.version}</Typography>}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" className="mono" sx={{ wordBreak: 'break-all' }}>{d.fileName} · {fmtBytes(d.sizeBytes)}</Typography>
                </TableCell>
                <TableCell><ToneChip value={d.stage} /></TableCell>
                <TableCell>
                  {d.processingStatus ? (
                    <Tooltip title={d.processingNote ?? ''}><span><ToneChip value={d.processingStatus} /></span></Tooltip>
                  ) : (
                    <Typography variant="body2" color="text.secondary">{humanize(d.source)}</Typography>
                  )}
                </TableCell>
                <TableCell><Hash value={d.sha256} /></TableCell>
                <TableCell>
                  <Typography variant="body2">{fmtDateTime(d.createdAt)}</Typography>
                  <Typography variant="caption" color="text.secondary">{d.createdBy}</Typography>
                </TableCell>
                <TableCell align="right">
                  <IconButton size="small" aria-label="Preview"><VisibilityOutlinedIcon fontSize="small" /></IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      {open && (
        <PdfDialog
          open
          onClose={() => setOpen(null)}
          src={contentUrl(open)}
          title={`${DOC_LABEL[open.docType]}${open.direction === 'OUTBOUND' ? ` v${open.version} (${open.stage.toLowerCase()})` : ''}`}
          subtitle={`${open.fileName} · sha256 ${open.sha256}`}
          fileName={open.fileName}
        />
      )}
    </>
  )
}

const FIELD_ICON = {
  MATCH: <CheckCircleIcon fontSize="small" color="success" />,
  MISMATCH: <ErrorIcon fontSize="small" color="error" />,
  MISSING: <HelpOutlineIcon fontSize="small" color="warning" />,
  UNREADABLE: <HelpOutlineIcon fontSize="small" color="warning" />,
}

export function MatchResultView({ match }: { match: MatchView }) {
  const breaks = match.fields.filter((f) => f.status !== 'MATCH').length
  return (
    <Box>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mb: 1.5 }}>
        <ToneChip value={match.outcome} />
        <Typography variant="body2" color="text.secondary">
          {match.fields.length - breaks}/{match.fields.length} terms agree · identified by {humanize(match.identifiedBy)} · {fmtDateTime(match.createdAt)}
        </Typography>
        {match.qrVerified && <Chip size="small" icon={<QrCode2Icon />} label="QR fingerprint verified" color="success" variant="outlined" />}
      </Box>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 32 }} />
              <TableCell>Term</TableCell>
              <TableCell>Our booking</TableCell>
              <TableCell>Client document</TableCell>
              <TableCell>Difference</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {match.fields.map((f) => (
              <TableRow key={f.key} sx={f.status !== 'MATCH' ? { bgcolor: (t) => `${t.palette.error.main}12` } : undefined}>
                <TableCell sx={{ pr: 0 }}>{FIELD_ICON[f.status]}</TableCell>
                <TableCell>{f.label}</TableCell>
                <TableCell className="mono">{f.expected}</TableCell>
                <TableCell className="mono" sx={{ fontWeight: f.status !== 'MATCH' ? 700 : 400, color: f.status !== 'MATCH' ? 'error.main' : undefined }}>
                  {f.actual ?? <em>not found</em>}
                </TableCell>
                <TableCell className="mono" sx={{ color: 'text.secondary' }}>{f.difference ?? ''}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
    </Box>
  )
}

const STEPS = ['Booked', 'Enriched', 'Drafted', 'Approved', 'Sent', 'Matched', 'Confirmed']
const STEP_OF: Record<ConfirmationStatus, number> = {
  NEW: 0, ENRICHED: 1, DRAFTED: 2, PENDING_APPROVAL: 2, APPROVED: 3, AWAITING_RESPONSE: 4,
  MATCHED: 5, MISMATCHED: 5, DISPUTED: 5, CONFIRMED: 7, CANCELLED: -1,
}

export function LifecycleStepper({ status }: { status: ConfirmationStatus }) {
  const active = STEP_OF[status]
  const broken = status === 'MISMATCHED' || status === 'DISPUTED'
  const compact = useMediaQuery((t: Theme) => t.breakpoints.down('sm'))
  if (status === 'CANCELLED') {
    return <Typography variant="body2" color="text.secondary">This confirmation has been cancelled.</Typography>
  }
  if (compact) {
    const step = Math.min(active, STEPS.length - 1)
    return (
      <Box>
        <Typography variant="body2" sx={{ fontWeight: 600 }} color={broken ? 'error.main' : 'text.primary'}>
          Step {step + 1} of {STEPS.length} · {STEPS[step]}{broken ? (status === 'DISPUTED' ? ' – disputed' : ' – break') : ''}
        </Typography>
        <LinearProgress variant="determinate" value={(Math.min(active, STEPS.length) / STEPS.length) * 100} color={broken ? 'error' : 'primary'} sx={{ mt: 1, height: 6, borderRadius: 3 }} />
      </Box>
    )
  }
  return (
    <Stepper activeStep={active} alternativeLabel sx={{ '& .MuiStepLabel-label': { fontSize: 12, mt: '6px !important' } }}>
      {STEPS.map((label, i) => (
        <Step key={label} completed={i < active}>
          <StepLabel
            error={broken && i === active}
            optional={
              i === active && status === 'PENDING_APPROVAL' ? <Typography variant="caption" color="warning.main" sx={{ display: 'block', textAlign: 'center' }}>Awaiting checker</Typography>
                : broken && i === active ? <Typography variant="caption" color="error" sx={{ display: 'block', textAlign: 'center' }}>{status === 'DISPUTED' ? 'Disputed' : 'Break'}</Typography>
                : undefined
            }
          >
            {label}
          </StepLabel>
        </Step>
      ))}
    </Stepper>
  )
}
