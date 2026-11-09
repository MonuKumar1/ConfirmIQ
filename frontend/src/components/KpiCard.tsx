import { Box, Paper, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'

export function KpiCard({ label, value, unit, hint, tone, to, icon }: {
  label: string
  value: ReactNode
  unit?: string
  hint?: ReactNode
  tone?: 'error' | 'warning' | 'success' | 'primary'
  to?: string
  icon?: ReactNode
}) {
  return (
    <Paper
      component={to ? RouterLink : 'div'}
      {...(to ? { to } : {})}
      sx={{
        p: 2, display: 'block', textDecoration: 'none', color: 'inherit', position: 'relative', overflow: 'hidden', height: '100%',
        transition: 'border-color .15s, transform .15s',
        ...(to ? { '&:hover': { borderColor: 'primary.main' } } : {}),
        '&::after': tone ? { content: '""', position: 'absolute', left: 0, top: 0, bottom: 0, width: 3, bgcolor: `${tone}.main` } : {},
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
        <Typography variant="overline" noWrap sx={{ flex: 1, minWidth: 0 }} title={label}>{label}</Typography>
        {icon && <Box sx={{ display: 'flex', '& svg': { fontSize: 18, opacity: 0.6 } }}>{icon}</Box>}
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5, mt: 0.25 }}>
        <Typography sx={{ fontSize: 26, fontWeight: 650, letterSpacing: '-0.02em', color: tone && tone !== 'primary' ? `${tone}.main` : 'text.primary' }}>
          {value}
        </Typography>
        {unit && <Typography variant="body2" color="text.secondary">{unit}</Typography>}
      </Box>
      {hint && <Typography variant="caption" color="text.secondary" noWrap component="div">{hint}</Typography>}
    </Paper>
  )
}
