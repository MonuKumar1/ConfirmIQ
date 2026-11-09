import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined'
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined'
import LogoutIcon from '@mui/icons-material/Logout'
import { AppBar, Box, Button, IconButton, Toolbar, Tooltip, Typography } from '@mui/material'
import { NavLink, Outlet } from 'react-router-dom'
import { useThemeMode } from '../app/ThemeMode'
import { useAuth } from '../auth/AuthContext'
import { Brand } from '../layout/Brand'

export function PortalShell() {
  const { user, logout } = useAuth()
  const { mode, toggle } = useThemeMode()
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <AppBar position="sticky" sx={{ bgcolor: 'brand.navy', color: '#fff' }}>
        <Toolbar sx={{ gap: 2, maxWidth: 1280, width: '100%', mx: 'auto' }}>
          <Brand subtitle="Client Portal" />
          <Box sx={{ display: { xs: 'none', sm: 'flex' }, gap: 0.5, ml: 3 }}>
            {[{ to: '/portal', label: 'Confirmations', end: true }, { to: '/portal/kb', label: 'Help & glossary' }].map((l) => (
              <Button key={l.to} component={NavLink} to={l.to} end={l.end} sx={{ color: 'rgba(255,255,255,0.75)', '&.active': { color: '#fff', bgcolor: 'rgba(255,255,255,0.1)' } }}>
                {l.label}
              </Button>
            ))}
          </Box>
          <Box sx={{ flex: 1 }} />
          <Box sx={{ textAlign: 'right', display: { xs: 'none', md: 'block' } }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{user?.fullName}</Typography>
            <Typography variant="caption" sx={{ opacity: 0.7 }}>{user?.counterpartyName}</Typography>
          </Box>
          <Tooltip title="Toggle colour mode">
            <IconButton onClick={toggle} sx={{ color: '#fff' }} size="small">
              {mode === 'light' ? <DarkModeOutlinedIcon fontSize="small" /> : <LightModeOutlinedIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Sign out">
            <IconButton onClick={logout} sx={{ color: '#fff' }} size="small" aria-label="Sign out"><LogoutIcon fontSize="small" /></IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>
      <Box component="main" sx={{ flex: 1, p: { xs: 2, md: 3 }, maxWidth: 1280, width: '100%', mx: 'auto' }}>
        <Outlet />
      </Box>
      <Box component="footer" sx={{ py: 2, textAlign: 'center', borderTop: 1, borderColor: 'divider' }}>
        <Typography variant="caption" color="text.secondary">
          Meridian Capital Markets Ltd. · FX Confirmations · fx.confirmations@meridian-capital.example · Fictional institution for demonstration purposes
        </Typography>
      </Box>
    </Box>
  )
}
