import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined'
import LinkIcon from '@mui/icons-material/Link'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import {
  Alert, Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton,
  Paper, Tab, Tabs, TextField, Tooltip, Typography,
} from '@mui/material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { useInbound } from '../api/hooks'
import type { InboundResult, InboundView, SearchHit } from '../api/types'
import { fmtBytes, fmtDateTime, humanize } from '../app/format'
import { useNotify } from '../app/notify'
import { Dropzone } from '../components/Dropzone'
import { Hash } from '../components/Mono'
import { PageHeader } from '../components/PageHeader'
import { PdfDialog } from '../components/PdfViewer'
import { Section } from '../components/Section'
import { ToneChip } from '../components/StatusChip'

const FILTERS = [
  { id: 'ALL', label: 'All received' },
  { id: 'EXCEPTIONS', label: 'Exceptions' },
  { id: 'MISMATCHED', label: 'Mismatched' },
  { id: 'MATCHED', label: 'Matched' },
]

export default function InboundPage() {
  const { data = [], isFetching } = useInbound()
  const qc = useQueryClient()
  const notify = useNotify()
  const [filter, setFilter] = useState('ALL')
  const [busy, setBusy] = useState(false)
  const [results, setResults] = useState<InboundResult[]>([])
  const [preview, setPreview] = useState<InboundView | null>(null)
  const [linking, setLinking] = useState<InboundView | null>(null)
  const { data: inbox } = useQuery({ queryKey: ['inbox'], queryFn: () => api.get<{ directory: string }>('/inbound/inbox').then((r) => r.data) })

  const rows = data.filter((d) =>
    filter === 'ALL' ? true : filter === 'EXCEPTIONS' ? ['UNIDENTIFIED', 'NOT_EXPECTED', 'FAILED'].includes(d.status ?? '') : d.status === filter)
  const exceptions = data.filter((d) => ['UNIDENTIFIED', 'FAILED'].includes(d.status ?? '')).length

  const upload = async (files: File[]) => {
    setBusy(true)
    try {
      const form = new FormData()
      files.forEach((f) => form.append('files', f))
      const { data: res } = await api.post<InboundResult[]>('/inbound/upload', form)
      setResults(res)
      notify(`${res.length} document(s) processed`)
      await qc.invalidateQueries()
    } catch (e) {
      notify(errorMessage(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  const columns = useMemo<GridColDef<InboundView>[]>(() => [
    { field: 'receivedAt', headerName: 'Received', width: 150, valueFormatter: (v: string) => fmtDateTime(v) },
    {
      field: 'fileName', headerName: 'Document', flex: 1, minWidth: 220,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.3, py: 0.5, minWidth: 0 }}>
          <Typography variant="body2" noWrap className="mono">{row.fileName}</Typography>
          <Typography variant="caption" color="text.secondary">{humanize(row.source)} · {fmtBytes(row.sizeBytes)} · by {row.receivedBy}</Typography>
        </Box>
      ),
    },
    {
      field: 'tradeRef', headerName: 'Trade', width: 190,
      renderCell: ({ row }) => row.tradeId ? (
        <Box sx={{ lineHeight: 1.3, minWidth: 0 }}>
          <Typography component={RouterLink} to={`/trades/${row.tradeId}`} variant="body2" className="mono" sx={{ fontWeight: 600, display: 'block' }} onClick={(e: React.MouseEvent) => e.stopPropagation()}>{row.tradeRef}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap>{row.counterparty}</Typography>
        </Box>
      ) : <Typography variant="body2" color="warning.main">Not identified</Typography>,
    },
    { field: 'status', headerName: 'Result', width: 130, renderCell: ({ value }) => value && <ToneChip value={value} /> },
    { field: 'note', headerName: 'Processing note', flex: 1, minWidth: 240, renderCell: ({ value }) => <Tooltip title={value ?? ''}><Typography variant="body2" noWrap>{value}</Typography></Tooltip> },
    { field: 'sha256', headerName: 'SHA-256', width: 130, renderCell: ({ value }) => <Hash value={value} /> },
    {
      field: 'actions', headerName: '', width: 92, sortable: false, align: 'right',
      renderCell: ({ row }) => (
        <Box>
          {(row.status === 'UNIDENTIFIED' || row.status === 'NOT_EXPECTED') && !row.tradeId && (
            <Tooltip title="Link to trade"><IconButton size="small" onClick={(e) => { e.stopPropagation(); setLinking(row) }}><LinkIcon fontSize="small" /></IconButton></Tooltip>
          )}
          <Tooltip title="Preview"><IconButton size="small" onClick={() => setPreview(row)}><VisibilityOutlinedIcon fontSize="small" /></IconButton></Tooltip>
        </Box>
      ),
    },
  ], [])

  return (
    <>
      <PageHeader
        title="Client responses"
        subtitle="Signed confirmations and counterparty paper received from clients. Documents are identified by QR code or trade reference and matched field-by-field."
      />
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1.4fr 1fr' }, mb: 2 }}>
        <Section title="Upload client documents" subtitle="PDF only · multiple files supported">
          <Dropzone accept=".pdf,application/pdf" onFiles={upload} disabled={busy} title={busy ? 'Processing…' : 'Drop client PDFs here or click to browse'} hint="Our returned paper is recognised by its QR code; counterparty paper by the quoted Meridian reference." />
          {results.length > 0 && (
            <Box sx={{ mt: 2, display: 'grid', gap: 1 }}>
              {results.map((r) => (
                <Alert key={r.documentId} severity={r.status === 'MATCHED' ? 'success' : r.status === 'MISMATCHED' ? 'error' : 'warning'}
                  action={r.tradeId && <Button component={RouterLink} to={`/trades/${r.tradeId}`} color="inherit" size="small">Open</Button>}>
                  <strong className="mono">{r.tradeRef ?? r.fileName}</strong> – {r.note}
                </Alert>
              ))}
            </Box>
          )}
        </Section>
        <Section title="Inbox gateway" subtitle="Simulated email / SFTP ingestion">
          <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
            <FolderOpenOutlinedIcon color="primary" />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2">PDFs dropped into this folder are picked up automatically every 30 seconds and moved to <span className="mono">processed/</span> or <span className="mono">failed/</span>:</Typography>
              <Typography variant="body2" className="mono" sx={{ mt: 1, p: 1, bgcolor: 'action.hover', borderRadius: 1, wordBreak: 'break-all' }}>{inbox?.directory ?? '…'}</Typography>
            </Box>
          </Box>
          {exceptions > 0 && (
            <Alert severity="warning" sx={{ mt: 2 }}>{exceptions} document(s) could not be identified and need manual linking.</Alert>
          )}
        </Section>
      </Box>

      <Paper sx={{ overflow: 'hidden' }}>
        <Tabs value={filter} onChange={(_, v) => setFilter(v)} sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
          {FILTERS.map((f) => <Tab key={f.id} value={f.id} label={f.label} />)}
        </Tabs>
        <DataGrid
          rows={rows}
          columns={columns}
          loading={isFetching && !data.length}
          rowHeight={52}
          autoHeight
          disableColumnMenu
          disableRowSelectionOnClick
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          sx={{ border: 0, '& .MuiDataGrid-cell': { display: 'flex', alignItems: 'center' } }}
          localeText={{ noRowsLabel: 'No documents' }}
        />
      </Paper>

      {preview && (
        <PdfDialog open onClose={() => setPreview(null)} src={`/documents/${preview.id}/content`} title={preview.fileName} subtitle={`sha256 ${preview.sha256}`} fileName={preview.fileName} />
      )}
      {linking && <LinkDialog doc={linking} onClose={() => setLinking(null)} />}
    </>
  )
}

function LinkDialog({ doc, onClose }: { doc: InboundView; onClose: () => void }) {
  const [term, setTerm] = useState('')
  const [hit, setHit] = useState<SearchHit | null>(null)
  const qc = useQueryClient()
  const notify = useNotify()
  const { data = [] } = useQuery({
    queryKey: ['search', term],
    queryFn: () => api.get<SearchHit[]>('/trades/search', { params: { q: term } }).then((r) => r.data),
    enabled: term.length >= 2,
  })
  const link = async () => {
    try {
      const { data: r } = await api.post<InboundResult>(`/inbound/${doc.id}/link`, { tradeId: hit!.tradeId })
      notify(`${r.tradeRef}: ${r.note}`, r.status === 'MATCHED' ? 'success' : 'warning')
      await qc.invalidateQueries()
      onClose()
    } catch (e) {
      notify(errorMessage(e), 'error')
    }
  }
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Link document to trade</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          <span className="mono">{doc.fileName}</span> could not be identified automatically. Choose the trade it relates to; matching runs immediately.
        </DialogContentText>
        <Autocomplete
          options={data}
          value={hit}
          onChange={(_, v) => setHit(v)}
          onInputChange={(_, v) => setTerm(v)}
          getOptionLabel={(o) => `${o.tradeRef} · ${o.counterparty} · ${o.product} ${o.currencyPair}`}
          filterOptions={(x) => x}
          renderInput={(p) => <TextField {...p} label="Search trade" autoFocus />}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!hit} onClick={link}>Link & match</Button>
      </DialogActions>
    </Dialog>
  )
}
