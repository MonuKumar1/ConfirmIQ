import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItemButton, Paper, Skeleton,
  Tab, Tabs, TextField, Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, errorMessage } from '../api/client'
import { useTemplate, useTemplates } from '../api/hooks'
import type { TemplateDetail, TemplateVersionView } from '../api/types'
import { fmtDateTime, PRODUCT_LABEL } from '../app/format'
import { useNotify } from '../app/notify'
import { useAuth } from '../auth/AuthContext'
import { PageHeader } from '../components/PageHeader'
import { PdfDialog } from '../components/PdfViewer'

const PLACEHOLDERS = ['counterpartyName', 'bankName', 'tradeRef', 'productName', 'tradeDate', 'masterAgreement', 'operationsEmail']

export default function TemplatesPage() {
  const { data: templates } = useTemplates()
  const [picked, setSelected] = useState<number>()
  const selected = picked ?? templates?.[0]?.id

  return (
    <>
      <PageHeader
        title="Confirmation templates"
        subtitle="Legal wording per product, with optional counterparty-specific overrides. Every change creates a new immutable version; exactly one version is active."
      />
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '300px 1fr' }, alignItems: 'start' }}>
        <Paper sx={{ overflow: 'hidden' }}>
          <List disablePadding>
            {templates?.map((t) => (
              <ListItemButton key={t.id} selected={t.id === selected} onClick={() => setSelected(t.id)} sx={{ display: 'block', borderBottom: 1, borderColor: 'divider' }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{t.name}</Typography>
                <Box sx={{ display: 'flex', gap: 0.75, mt: 0.5, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Chip size="small" label={PRODUCT_LABEL[t.productType]} variant="outlined" />
                  {t.counterparty ? <Chip size="small" label="Client override" color="secondary" variant="outlined" /> : <Chip size="small" label="Default" variant="outlined" />}
                  <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>v{t.activeVersion} active</Typography>
                </Box>
              </ListItemButton>
            ))}
          </List>
        </Paper>
        {selected ? <TemplateEditor id={selected} /> : <Skeleton variant="rounded" height={400} />}
      </Box>
    </>
  )
}

function TemplateEditor({ id }: { id: number }) {
  const { data: tpl } = useTemplate(id)
  const [version, setVersion] = useState<number>()
  const [editing, setEditing] = useState(false)
  const [preview, setPreview] = useState<number | null>(null)
  const { hasRole } = useAuth()
  const notify = useNotify()
  const qc = useQueryClient()

  useEffect(() => setVersion(tpl?.versions.find((v) => v.active)?.version ?? tpl?.versions[0]?.version), [tpl])
  if (!tpl) return <Skeleton variant="rounded" height={400} />
  const v = tpl.versions.find((x) => x.version === version) ?? tpl.versions[0]

  const activate = async () => {
    try {
      await api.post(`/templates/${tpl.id}/versions/${v.version}/activate`)
      notify(`${tpl.code} v${v.version} is now active`)
      await qc.invalidateQueries({ queryKey: ['template', id] })
      await qc.invalidateQueries({ queryKey: ['templates'] })
    } catch (e) {
      notify(errorMessage(e), 'error')
    }
  }

  return (
    <Paper sx={{ overflow: 'hidden' }}>
      <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', borderBottom: 1, borderColor: 'divider' }}>
        <Box sx={{ flex: 1, minWidth: 200 }}>
          <Typography variant="subtitle1">{tpl.name}</Typography>
          <Typography variant="caption" color="text.secondary" className="mono">{tpl.code}{tpl.counterparty ? ` · ${tpl.counterparty}` : ''}</Typography>
        </Box>
        <Button startIcon={<PictureAsPdfOutlinedIcon />} onClick={() => setPreview(v.version)}>Specimen PDF</Button>
        {hasRole('SUPERVISOR') && !v.active && <Button variant="outlined" onClick={activate}>Activate v{v.version}</Button>}
        {hasRole('SUPERVISOR') && <Button variant="contained" startIcon={<EditOutlinedIcon />} onClick={() => setEditing(true)}>New version</Button>}
      </Box>
      <Tabs value={v.version} onChange={(_, x) => setVersion(x)} variant="scrollable" sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
        {tpl.versions.map((x) => (
          <Tab key={x.version} value={x.version} label={<Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center' }}>v{x.version}{x.active && <Chip size="small" color="success" label="Active" sx={{ height: 18 }} />}</Box>} />
        ))}
      </Tabs>
      <Box sx={{ p: 2.5 }}>
        <Typography variant="caption" color="text.secondary">
          Created by {v.createdBy} · {fmtDateTime(v.createdAt)}{v.changeNote ? ` · “${v.changeNote}”` : ''}
        </Typography>
        <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 2 }}>Title</Typography>
        <Typography sx={{ fontWeight: 600 }}>{v.title}</Typography>
        <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 2 }}>Preamble</Typography>
        <Typography variant="body2" sx={{ lineHeight: 1.7 }}>{v.preamble}</Typography>
        <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 2 }}>Clauses</Typography>
        <Box component="ol" sx={{ m: 0, pl: 2.5 }}>
          {v.clauses.split(/\n\s*\n/).map((c, i) => <Typography key={i} component="li" variant="body2" sx={{ mb: 1, lineHeight: 1.7 }}>{c}</Typography>)}
        </Box>
        <Typography variant="overline" color="text.secondary" component="div" sx={{ mt: 1 }}>Closing</Typography>
        <Typography variant="body2" sx={{ lineHeight: 1.7 }}>{v.closing}</Typography>
      </Box>
      {editing && <VersionDialog tpl={tpl} base={v} onClose={() => setEditing(false)} onSaved={(n) => setVersion(n)} />}
      {preview != null && (
        <PdfDialog open onClose={() => setPreview(null)} src={`/templates/${tpl.id}/versions/${preview}/preview`} title={`${tpl.name} · v${preview} specimen`} fileName={`${tpl.code}-v${preview}.pdf`} />
      )}
    </Paper>
  )
}

function VersionDialog({ tpl, base, onClose, onSaved }: { tpl: TemplateDetail; base: TemplateVersionView; onClose: () => void; onSaved: (v: number) => void }) {
  const [form, setForm] = useState({ title: base.title, preamble: base.preamble, clauses: base.clauses, closing: base.closing, changeNote: '' })
  const [error, setError] = useState<string>()
  const qc = useQueryClient()
  const notify = useNotify()
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })
  const save = async () => {
    try {
      const { data } = await api.post<TemplateDetail>(`/templates/${tpl.id}/versions`, form)
      notify(`Version ${data.versions[0].version} created – activate it to use it for new documents`)
      await qc.invalidateQueries({ queryKey: ['template', tpl.id] })
      onSaved(data.versions[0].version)
      onClose()
    } catch (e) {
      setError(errorMessage(e))
    }
  }
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>New version of {tpl.name}</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          Placeholders: {PLACEHOLDERS.map((p) => <code key={p} style={{ marginRight: 6 }}>{`{{${p}}}`}</code>)}. Separate clauses with a blank line.
        </Alert>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Box sx={{ display: 'grid', gap: 2 }}>
          <TextField label="Title" value={form.title} onChange={set('title')} fullWidth />
          <TextField label="Preamble" value={form.preamble} onChange={set('preamble')} multiline minRows={3} fullWidth />
          <TextField label="Clauses" value={form.clauses} onChange={set('clauses')} multiline minRows={8} fullWidth />
          <TextField label="Closing" value={form.closing} onChange={set('closing')} multiline minRows={2} fullWidth />
          <TextField label="Change note (required)" value={form.changeNote} onChange={set('changeNote')} fullWidth placeholder="e.g. Updated governing law clause per Legal ref. LGL-1042" />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={!form.changeNote.trim()}>Save as v{Math.max(...tpl.versions.map((x) => x.version)) + 1}</Button>
      </DialogActions>
    </Dialog>
  )
}
