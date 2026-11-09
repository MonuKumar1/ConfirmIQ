import AttachFileIcon from '@mui/icons-material/AttachFile'
import MailOutlineIcon from '@mui/icons-material/MailOutlined'
import SearchIcon from '@mui/icons-material/Search'
import {
  Alert, Box, Button, Chip, InputAdornment, List, ListItemButton, MenuItem, Pagination, Paper, TextField, Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useOutbox } from '../api/hooks'
import type { MessageType, OutboxView } from '../api/types'
import { fmtDateTime, fromNow, humanize } from '../app/format'
import { PageHeader } from '../components/PageHeader'
import { PdfDialog } from '../components/PdfViewer'
import { EmptyState } from '../components/Section'

const TYPE_COLOR: Record<MessageType, 'primary' | 'warning' | 'error' | 'default' | 'info' | 'secondary'> = {
  CONFIRMATION: 'primary', AMENDED_CONFIRMATION: 'warning', CANCELLATION: 'default', CHASER: 'warning',
  ESCALATION: 'error', PORTAL_NOTIFICATION: 'info',
}

export default function OutboxPage() {
  const [type, setType] = useState('')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [picked, setSelected] = useState<OutboxView | null>(null)
  const [attachment, setAttachment] = useState(false)
  useEffect(() => {
    const t = setTimeout(() => setQ(search.trim()), 300)
    return () => clearTimeout(t)
  }, [search])
  useEffect(() => setPage(0), [type, q])
  const { data } = useOutbox({ type: type || undefined, q: q || undefined, page, size: 30 })
  const messages = data?.content ?? []

  const selected = messages.find((m) => m.id === picked?.id) ?? messages[0] ?? null

  return (
    <>
      <PageHeader title="Outbox" subtitle="Client communications issued by ConfirmIQ – confirmations, chasers, escalations and portal notifications." />
      <Alert severity="info" sx={{ mb: 2 }}>Delivery is simulated in this environment: messages are recorded here and never sent over SMTP.</Alert>
      <Paper sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '400px 1fr' }, minHeight: 560, overflow: 'hidden' }}>
        <Box sx={{ borderRight: { md: 1 }, borderColor: { md: 'divider' }, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          <Box sx={{ p: 1.5, display: 'flex', gap: 1, borderBottom: 1, borderColor: 'divider' }}>
            <TextField placeholder="Subject or recipient" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ flex: 1 }}
              slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
            <TextField select value={type} onChange={(e) => setType(e.target.value)} sx={{ width: 140 }} slotProps={{ select: { displayEmpty: true } }}>
              <MenuItem value="">All types</MenuItem>
              {Object.keys(TYPE_COLOR).map((t) => <MenuItem key={t} value={t}>{humanize(t)}</MenuItem>)}
            </TextField>
          </Box>
          <List disablePadding sx={{ flex: 1, overflowY: 'auto', maxHeight: { md: 'calc(100vh - 330px)' } }}>
            {messages.length === 0 && <EmptyState icon={<MailOutlineIcon />} title="No messages" />}
            {messages.map((m) => (
              <ListItemButton key={m.id} selected={m.id === selected?.id} onClick={() => setSelected(m)} sx={{ display: 'block', py: 1.25, borderBottom: 1, borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                  <Chip size="small" label={humanize(m.messageType)} color={TYPE_COLOR[m.messageType]} variant="outlined" />
                  {m.documentId && <AttachFileIcon sx={{ fontSize: 15, color: 'text.secondary' }} />}
                  <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>{fromNow(m.createdAt)}</Typography>
                </Box>
                <Typography variant="body2" sx={{ fontWeight: 600, mt: 0.5 }} noWrap>{m.subject}</Typography>
                <Typography variant="caption" color="text.secondary" noWrap component="div">To {m.recipients}</Typography>
              </ListItemButton>
            ))}
          </List>
          {data && data.totalElements > 30 && (
            <Box sx={{ p: 1, display: 'flex', justifyContent: 'center', borderTop: 1, borderColor: 'divider' }}>
              <Pagination size="small" count={Math.ceil(data.totalElements / 30)} page={page + 1} onChange={(_, p) => setPage(p - 1)} />
            </Box>
          )}
        </Box>
        <Box sx={{ p: { xs: 2, md: 3 }, minWidth: 0 }}>
          {selected ? (
            <>
              <Typography variant="h6">{selected.subject}</Typography>
              <Box sx={{ display: 'grid', gridTemplateColumns: '64px 1fr', rowGap: 0.5, mt: 1.5, fontSize: 13 }}>
                <Typography variant="body2" color="text.secondary">From</Typography><Typography variant="body2">fx.confirmations@meridian-capital.example ({selected.createdBy})</Typography>
                <Typography variant="body2" color="text.secondary">To</Typography><Typography variant="body2">{selected.recipients}</Typography>
                {selected.cc && <><Typography variant="body2" color="text.secondary">Cc</Typography><Typography variant="body2">{selected.cc}</Typography></>}
                <Typography variant="body2" color="text.secondary">Sent</Typography><Typography variant="body2">{fmtDateTime(selected.createdAt)} · {selected.status.toLowerCase()}</Typography>
              </Box>
              <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>
                {selected.documentId && <Button variant="outlined" startIcon={<AttachFileIcon />} onClick={() => setAttachment(true)}>View attachment</Button>}
                {selected.tradeId && <Button component={RouterLink} to={`/trades/${selected.tradeId}`}>Open trade</Button>}
              </Box>
              <Box component="pre" sx={{ mt: 2, p: 2, bgcolor: 'action.hover', borderRadius: 1, whiteSpace: 'pre-wrap', fontFamily: 'inherit', fontSize: 13.5, lineHeight: 1.6 }}>
                {selected.body}
              </Box>
              {attachment && selected.documentId && (
                <PdfDialog open onClose={() => setAttachment(false)} src={`/documents/${selected.documentId}/content`} title={`Attachment · ${selected.subject}`} />
              )}
            </>
          ) : <EmptyState icon={<MailOutlineIcon />} title="Select a message" />}
        </Box>
      </Paper>
    </>
  )
}
