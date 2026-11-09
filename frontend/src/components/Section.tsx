import { Box, Paper, Typography, type SxProps, type Theme } from '@mui/material'
import type { ReactNode } from 'react'

export function Section({ title, subtitle, actions, children, sx, noPadding }: {
  title?: ReactNode
  subtitle?: ReactNode
  actions?: ReactNode
  children: ReactNode
  sx?: SxProps<Theme>
  noPadding?: boolean
}) {
  return (
    <Paper sx={{ overflow: 'hidden', ...sx }}>
      {(title || actions) && (
        <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1, borderBottom: 1, borderColor: 'divider' }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            {title && <Typography variant="subtitle1">{title}</Typography>}
            {subtitle && <Typography variant="caption" color="text.secondary">{subtitle}</Typography>}
          </Box>
          {actions}
        </Box>
      )}
      <Box sx={noPadding ? undefined : { p: 2 }}>{children}</Box>
    </Paper>
  )
}

/** Label/value pair used in definition grids. */
export function Field({ label, children, mono }: { label: string; children: ReactNode; mono?: boolean }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>{label}</Typography>
      <Typography variant="body2" component="div" className={mono ? 'mono' : undefined} sx={{ fontWeight: 500, wordBreak: 'break-word' }}>
        {children ?? '—'}
      </Typography>
    </Box>
  )
}

export function EmptyState({ icon, title, hint }: { icon?: ReactNode; title: string; hint?: ReactNode }) {
  return (
    <Box sx={{ py: 6, textAlign: 'center', color: 'text.secondary' }}>
      {icon && <Box sx={{ mb: 1, '& svg': { fontSize: 40, opacity: 0.5 } }}>{icon}</Box>}
      <Typography variant="subtitle2" color="text.primary">{title}</Typography>
      {hint && <Typography variant="body2" sx={{ mt: 0.5 }}>{hint}</Typography>}
    </Box>
  )
}
