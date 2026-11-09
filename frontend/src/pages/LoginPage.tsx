import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import { Alert, Box, Button, ButtonBase, Divider, Paper, Stack, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Brand } from '../layout/Brand'

const DEMO_PASSWORD = 'Demo@2026'

const DEMO_USERS = [
  { username: 'maker', name: 'Priya Raman', role: 'Maker', desc: 'Prepares, sends and chases confirmations' },
  { username: 'checker', name: 'Daniel Okafor', role: 'Checker', desc: 'Four-eyes approval of outbound documents' },
  { username: 'supervisor', name: 'Sophie Laurent', role: 'Supervisor', desc: 'Static data, templates, breaks, cancellations' },
  { username: 'client', name: 'James Whitfield', role: 'Client portal', desc: 'Northbridge Asset Management – affirm or dispute' },
]

const FEATURES = [
  'Four-eyes confirmation lifecycle with SLA chasers and escalation',
  'Versioned confirmation templates, e-stamped PDFs with QR fingerprint',
  'Automatic matching of client paper with field-level break analysis',
  'Counterparty SSIs, client portal and a complete audit trail',
]

export function LoginPage() {
  const { login, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={user.role === 'CLIENT' ? '/portal' : '/'} replace />

  const submit = async (u = username, p = password) => {
    setBusy(true)
    setError(undefined)
    try {
      const profile = await login(u, p)
      const from = (location.state as { from?: string } | null)?.from
      navigate(profile.role === 'CLIENT' ? '/portal' : from && from !== '/login' ? from : '/', { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    void submit()
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1.05fr 1fr' } }}>
      <Box
        sx={{
          display: { xs: 'none', md: 'flex' }, flexDirection: 'column', p: 6, color: '#fff', position: 'relative', overflow: 'hidden',
          background: 'linear-gradient(155deg, #0B1D33 0%, #102A43 55%, #16406B 100%)',
        }}
      >
        <Box sx={{ position: 'absolute', inset: 0, opacity: 0.06, backgroundImage: 'linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)', backgroundSize: '44px 44px' }} />
        <Box sx={{ position: 'relative' }}><Brand /></Box>
        <Box sx={{ position: 'relative', my: 'auto', maxWidth: 520 }}>
          <Typography sx={{ fontSize: 13, letterSpacing: '0.12em', textTransform: 'uppercase', color: '#8EC1F2', fontWeight: 600, mb: 2 }}>
            Post-trade operations
          </Typography>
          <Typography sx={{ fontSize: 38, fontWeight: 700, lineHeight: 1.15, letterSpacing: '-0.02em' }}>
            Confirm every trade.<br />Close every break.
          </Typography>
          <Typography sx={{ mt: 2, color: 'rgba(255,255,255,0.72)', fontSize: 15, lineHeight: 1.6 }}>
            ConfirmIQ automates FX trade confirmations from booking to client affirmation – generating, approving,
            delivering and matching confirmation documents with full control and auditability.
          </Typography>
          <Stack spacing={1.25} sx={{ mt: 4 }}>
            {FEATURES.map((f) => (
              <Box key={f} sx={{ display: 'flex', gap: 1.25, alignItems: 'flex-start' }}>
                <CheckCircleOutlineIcon sx={{ fontSize: 19, color: '#6FB6F5', mt: '1px' }} />
                <Typography sx={{ fontSize: 14, color: 'rgba(255,255,255,0.85)' }}>{f}</Typography>
              </Box>
            ))}
          </Stack>
        </Box>
        <Typography sx={{ position: 'relative', fontSize: 12, color: 'rgba(255,255,255,0.45)' }}>
          Meridian Capital is a fictional institution. Demonstration environment – no real client data.
        </Typography>
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', p: { xs: 2, sm: 4 }, bgcolor: 'background.default' }}>
        <Box sx={{ width: '100%', maxWidth: 420 }}>
          <Box sx={{ display: { xs: 'flex', md: 'none' }, mb: 3, p: 1.5, borderRadius: 1.5, bgcolor: 'brand.navy' }}><Brand /></Box>
          <Typography variant="h5">Sign in</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>
            Use your operations or client credentials.
          </Typography>
          <Box component="form" onSubmit={onSubmit}>
            <Stack spacing={2}>
              {error && <Alert severity="error">{error}</Alert>}
              <TextField label="Username" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus fullWidth size="medium" />
              <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" fullWidth size="medium" />
              <Button type="submit" variant="contained" size="large" disabled={busy || !username || !password} startIcon={<LockOutlinedIcon />}>
                {busy ? 'Signing in…' : 'Sign in'}
              </Button>
            </Stack>
          </Box>

          <Divider sx={{ my: 3 }}>
            <Typography variant="caption" color="text.secondary">DEMO ACCOUNTS · PASSWORD {DEMO_PASSWORD}</Typography>
          </Divider>
          <Stack spacing={1}>
            {DEMO_USERS.map((u) => (
              <Paper
                key={u.username}
                component={ButtonBase}
                onClick={() => void submit(u.username, DEMO_PASSWORD)}
                disabled={busy}
                sx={{ p: 1.5, display: 'flex', alignItems: 'center', gap: 1.5, textAlign: 'left', width: '100%', justifyContent: 'flex-start', '&:hover': { borderColor: 'primary.main' } }}
              >
                <Box sx={{ flex: 1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {u.role}{' '}
                    <Typography component="span" variant="caption" color="text.secondary">
                      · {u.name} · <span className="mono">{u.username}</span>
                    </Typography>
                  </Typography>
                  <Typography variant="caption" color="text.secondary">{u.desc}</Typography>
                </Box>
                <ArrowForwardIcon fontSize="small" color="action" />
              </Paper>
            ))}
          </Stack>
        </Box>
      </Box>
    </Box>
  )
}
