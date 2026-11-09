import { Box, Breadcrumbs, Link, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'

export function PageHeader({ title, subtitle, actions, crumbs }: {
  title: ReactNode
  subtitle?: ReactNode
  actions?: ReactNode
  crumbs?: { label: string; to?: string }[]
}) {
  return (
    <Box sx={{ mb: 2.5 }}>
      {crumbs && (
        <Breadcrumbs sx={{ mb: 0.75, fontSize: 12.5 }}>
          {crumbs.map((c) =>
            c.to ? (
              <Link key={c.label} component={RouterLink} to={c.to} underline="hover" color="text.secondary">{c.label}</Link>
            ) : (
              <Typography key={c.label} sx={{ fontSize: 12.5 }} color="text.primary">{c.label}</Typography>
            ),
          )}
        </Breadcrumbs>
      )}
      <Box sx={{ display: 'flex', alignItems: { xs: 'flex-start', md: 'center' }, gap: 2, flexDirection: { xs: 'column', md: 'row' } }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="h4" component="h1">{title}</Typography>
          {subtitle && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{subtitle}</Typography>}
        </Box>
        {actions && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>}
      </Box>
    </Box>
  )
}
