import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined'
import ApiOutlinedIcon from '@mui/icons-material/ApiOutlined'
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined'
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined'
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import FolderCopyOutlinedIcon from '@mui/icons-material/FolderCopyOutlined'
import HistoryEduOutlinedIcon from '@mui/icons-material/HistoryEduOutlined'
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined'
import LogoutIcon from '@mui/icons-material/Logout'
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined'
import MenuIcon from '@mui/icons-material/Menu'
import MoveToInboxOutlinedIcon from '@mui/icons-material/MoveToInboxOutlined'
import OutboxOutlinedIcon from '@mui/icons-material/OutboxOutlined'
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined'
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined'
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined'
import {
  AppBar, Avatar, Box, Chip, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText,
  ListSubheader, Menu, MenuItem, Toolbar, Tooltip, Typography, useMediaQuery, useTheme,
} from '@mui/material'
import { useState, type ReactNode } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useQueueCounts } from '../api/hooks'
import type { Queue, Role } from '../api/types'
import { useThemeMode } from '../app/ThemeMode'
import { useAuth } from '../auth/AuthContext'
import { Brand } from './Brand'
import { GlobalSearch } from './GlobalSearch'

const DRAWER = 248

interface NavItem {
  to: string
  label: string
  icon: ReactNode
  badge?: Queue
  badgeColor?: 'error' | 'warning' | 'info'
  roles?: Role[]
  external?: boolean
}

const NAV: { section: string; items: NavItem[] }[] = [
  { section: 'Overview', items: [{ to: '/', label: 'Dashboard', icon: <DashboardOutlinedIcon /> }] },
  {
    section: 'Confirmation workflow',
    items: [
      { to: '/confirmations', label: 'Confirmations', icon: <ReceiptLongOutlinedIcon />, badge: 'ACTION_REQUIRED', badgeColor: 'info' },
      { to: '/approvals', label: 'Approvals', icon: <FactCheckOutlinedIcon />, badge: 'PENDING_APPROVAL', badgeColor: 'warning' },
      { to: '/inbound', label: 'Client responses', icon: <MoveToInboxOutlinedIcon />, badge: 'BREAKS', badgeColor: 'error' },
      { to: '/ingestion', label: 'Trade ingestion', icon: <UploadFileOutlinedIcon />, roles: ['MAKER', 'SUPERVISOR'] },
    ],
  },
  {
    section: 'Client documents',
    items: [
      { to: '/documents', label: 'Document repository', icon: <FolderCopyOutlinedIcon /> },
      { to: '/templates', label: 'Templates', icon: <DescriptionOutlinedIcon /> },
      { to: '/outbox', label: 'Outbox', icon: <OutboxOutlinedIcon /> },
    ],
  },
  {
    section: 'Reference data & control',
    items: [
      { to: '/counterparties', label: 'Counterparties & SSIs', icon: <AccountBalanceOutlinedIcon /> },
      { to: '/reports', label: 'Reports', icon: <AssessmentOutlinedIcon /> },
      { to: '/audit', label: 'Audit trail', icon: <VerifiedUserOutlinedIcon /> },
    ],
  },
  {
    section: 'Help',
    items: [
      { to: '/kb', label: 'Knowledge base', icon: <MenuBookOutlinedIcon /> },
      { to: '/swagger-ui.html', label: 'API reference', icon: <ApiOutlinedIcon />, external: true },
    ],
  },
]

const ROLE_LABEL: Record<Role, string> = {
  MAKER: 'Operations · Maker',
  CHECKER: 'Operations · Checker',
  SUPERVISOR: 'Operations · Supervisor',
  CLIENT: 'Client',
}

export function AppShell() {
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('lg'))
  const [mobileOpen, setMobileOpen] = useState(false)
  const { user, logout } = useAuth()
  const { mode, toggle } = useThemeMode()
  const [menuEl, setMenuEl] = useState<HTMLElement | null>(null)
  const { data: counts } = useQueueCounts()
  const location = useLocation()

  const drawer = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', bgcolor: 'brand.sidebar', color: 'brand.sidebarText' }}>
      <Box sx={{ px: 2.25, py: 2.1 }}>
        <Brand subtitle="FX Operations" />
      </Box>
      <Divider sx={{ borderColor: 'rgba(255,255,255,0.08)' }} />
      <Box sx={{ flex: 1, overflowY: 'auto', py: 1 }}>
        {NAV.map((group) => {
          const items = group.items.filter((i) => !i.roles || (user && i.roles.includes(user.role)))
          if (!items.length) return null
          return (
            <List
              key={group.section}
              dense
              subheader={
                <ListSubheader disableSticky sx={{ bgcolor: 'transparent', color: 'rgba(255,255,255,0.42)', fontSize: 10.5, fontWeight: 700, letterSpacing: '0.09em', textTransform: 'uppercase', lineHeight: '30px', px: 2.5 }}>
                  {group.section}
                </ListSubheader>
              }
              sx={{ py: 0.25 }}
            >
              {items.map((item) => {
                const active = item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)
                const count = item.badge ? counts?.[item.badge] : undefined
                const linkProps = item.external
                  ? { component: 'a' as const, href: item.to, target: '_blank', rel: 'noreferrer' }
                  : { component: NavLink, to: item.to }
                return (
                  <ListItemButton
                    key={item.to}
                    {...linkProps}
                    onClick={() => setMobileOpen(false)}
                    sx={{
                      mx: 1.25, my: 0.15, borderRadius: 1.25, py: 0.7, color: active ? '#fff' : 'inherit',
                      bgcolor: active ? 'rgba(255,255,255,0.09)' : 'transparent',
                      position: 'relative',
                      '&:hover': { bgcolor: 'rgba(255,255,255,0.06)' },
                      '&::before': active
                        ? { content: '""', position: 'absolute', left: -10, top: 8, bottom: 8, width: 3, borderRadius: 2, bgcolor: '#4C9BE8' }
                        : {},
                    }}
                  >
                    <ListItemIcon sx={{ minWidth: 34, color: active ? '#8EC1F2' : 'rgba(255,255,255,0.55)', '& svg': { fontSize: 19 } }}>
                      {item.icon}
                    </ListItemIcon>
                    <ListItemText primary={item.label} slotProps={{ primary: { sx: { fontSize: 13.2, fontWeight: active ? 600 : 500 } } }} />
                    {!!count && (
                      <Chip
                        size="small"
                        label={count}
                        color={item.badgeColor}
                        sx={{ height: 19, fontSize: 10.5, minWidth: 24, '& .MuiChip-label': { px: 0.75 } }}
                      />
                    )}
                  </ListItemButton>
                )
              })}
            </List>
          )
        })}
      </Box>
      <Divider sx={{ borderColor: 'rgba(255,255,255,0.08)' }} />
      <Box sx={{ px: 2.5, py: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
        <HistoryEduOutlinedIcon sx={{ fontSize: 16, opacity: 0.5 }} />
        <Typography sx={{ fontSize: 11, opacity: 0.55 }}>v1.0 · Demo environment</Typography>
      </Box>
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Box component="nav" sx={{ width: { lg: DRAWER }, flexShrink: { lg: 0 } }}>
        {desktop ? (
          <Drawer variant="permanent" open sx={{ '& .MuiDrawer-paper': { width: DRAWER } }}>{drawer}</Drawer>
        ) : (
          <Drawer variant="temporary" open={mobileOpen} onClose={() => setMobileOpen(false)} ModalProps={{ keepMounted: true }} sx={{ '& .MuiDrawer-paper': { width: DRAWER } }}>
            {drawer}
          </Drawer>
        )}
      </Box>
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <AppBar position="sticky" color="inherit" sx={{ bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
          <Toolbar sx={{ gap: 1.5, minHeight: { xs: 56 }, px: { xs: 1.5, sm: 2.5 } }}>
            {!desktop && (
              <IconButton edge="start" onClick={() => setMobileOpen(true)} aria-label="Open navigation">
                <MenuIcon />
              </IconButton>
            )}
            <GlobalSearch />
            <Box sx={{ flex: 1 }} />
            <Chip label="UAT" size="small" variant="outlined" color="warning" sx={{ display: { xs: 'none', sm: 'inline-flex' } }} />
            <Tooltip title={mode === 'light' ? 'Dark mode' : 'Light mode'}>
              <IconButton onClick={toggle} size="small" aria-label="Toggle colour mode">
                {mode === 'light' ? <DarkModeOutlinedIcon fontSize="small" /> : <LightModeOutlinedIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
            <Box
              onClick={(e) => setMenuEl(e.currentTarget)}
              sx={{ display: 'flex', alignItems: 'center', gap: 1, cursor: 'pointer', pl: 1, borderLeft: 1, borderColor: 'divider' }}
            >
              <Avatar sx={{ width: 30, height: 30, fontSize: 12.5, bgcolor: 'primary.main' }}>
                {user?.fullName.split(' ').map((p) => p[0]).join('').slice(0, 2)}
              </Avatar>
              <Box sx={{ display: { xs: 'none', md: 'block' }, lineHeight: 1.1 }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{user?.fullName}</Typography>
                <Typography variant="caption" color="text.secondary">{user && ROLE_LABEL[user.role]}</Typography>
              </Box>
            </Box>
            <Menu anchorEl={menuEl} open={!!menuEl} onClose={() => setMenuEl(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
              <Box sx={{ px: 2, py: 1 }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{user?.fullName}</Typography>
                <Typography variant="caption" color="text.secondary">{user?.email}</Typography>
              </Box>
              <Divider />
              <MenuItem onClick={logout}>
                <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
                Sign out
              </MenuItem>
            </Menu>
          </Toolbar>
        </AppBar>
        <Box component="main" sx={{ flex: 1, p: { xs: 2, sm: 2.5, md: 3 }, maxWidth: 1680, width: '100%', mx: 'auto' }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  )
}
