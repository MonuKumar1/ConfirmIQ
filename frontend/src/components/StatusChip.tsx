import { Chip, type ChipProps } from '@mui/material'
import type { ConfirmationStatus } from '../api/types'

type Tone = ChipProps['color']

export const STATUS_META: Record<ConfirmationStatus, { label: string; tone: Tone }> = {
  NEW: { label: 'New', tone: 'default' },
  ENRICHED: { label: 'Enriched', tone: 'default' },
  DRAFTED: { label: 'Drafted', tone: 'info' },
  PENDING_APPROVAL: { label: 'Pending approval', tone: 'warning' },
  APPROVED: { label: 'Approved', tone: 'info' },
  AWAITING_RESPONSE: { label: 'Awaiting client', tone: 'primary' },
  MATCHED: { label: 'Matched', tone: 'success' },
  MISMATCHED: { label: 'Mismatch', tone: 'error' },
  DISPUTED: { label: 'Disputed', tone: 'error' },
  CONFIRMED: { label: 'Confirmed', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'default' },
}

export function StatusChip({ status, size = 'small' }: { status: ConfirmationStatus; size?: ChipProps['size'] }) {
  const meta = STATUS_META[status]
  const solid = status === 'CONFIRMED'
  return (
    <Chip
      size={size}
      label={meta.label}
      color={meta.tone}
      variant={solid ? 'filled' : 'outlined'}
      sx={{ fontWeight: 600, ...(meta.tone === 'default' && status === 'CANCELLED' ? { textDecoration: 'line-through', opacity: 0.75 } : {}) }}
    />
  )
}

const GENERIC_TONE: Record<string, Tone> = {
  MATCHED: 'success', MISMATCHED: 'error', UNIDENTIFIED: 'warning', NOT_EXPECTED: 'default', FAILED: 'error',
  ACCEPTED: 'success', DUPLICATE: 'default', REJECTED: 'error', MATCH: 'success', MISMATCH: 'error',
  MISSING: 'warning', UNREADABLE: 'warning', DRAFT: 'warning', FINAL: 'success', RECEIVED: 'info',
  ACTION_REQUIRED: 'warning', UNDER_REVIEW: 'error', CONFIRMED: 'success',
}

export function ToneChip({ value, label }: { value: string; label?: string }) {
  return <Chip size="small" variant="outlined" color={GENERIC_TONE[value] ?? 'default'} label={label ?? value.replace(/_/g, ' ')} />
}
