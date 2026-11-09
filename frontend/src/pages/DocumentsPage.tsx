import CallMadeIcon from '@mui/icons-material/CallMade'
import CallReceivedIcon from '@mui/icons-material/CallReceived'
import SearchIcon from '@mui/icons-material/Search'
import { Box, InputAdornment, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useDocuments } from '../api/hooks'
import type { DocumentView } from '../api/types'
import { fmtBytes, fmtDateTime, humanize } from '../app/format'
import { Hash } from '../components/Mono'
import { PageHeader } from '../components/PageHeader'
import { PdfDialog } from '../components/PdfViewer'
import { ToneChip } from '../components/StatusChip'

const TYPES = ['CONFIRMATION', 'AMENDED_CONFIRMATION', 'CANCELLATION', 'CLIENT_RESPONSE']

export default function DocumentsPage() {
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [docType, setDocType] = useState('')
  const [direction, setDirection] = useState('')
  const [pagination, setPagination] = useState({ page: 0, pageSize: 25 })
  const [preview, setPreview] = useState<DocumentView | null>(null)

  useEffect(() => {
    const t = setTimeout(() => setQ(search.trim()), 300)
    return () => clearTimeout(t)
  }, [search])
  useEffect(() => setPagination((p) => ({ ...p, page: 0 })), [q, docType, direction])

  const { data, isFetching } = useDocuments({
    q: q || undefined, docType: docType || undefined, direction: direction || undefined,
    page: pagination.page, size: pagination.pageSize,
  })

  const columns = useMemo<GridColDef<DocumentView>[]>(() => [
    {
      field: 'direction', headerName: '', width: 44, sortable: false,
      renderCell: ({ value }) => value === 'OUTBOUND'
        ? <Tooltip title="Outbound – house paper"><CallMadeIcon fontSize="small" color="primary" /></Tooltip>
        : <Tooltip title="Inbound – client paper"><CallReceivedIcon fontSize="small" color="secondary" /></Tooltip>,
    },
    {
      field: 'fileName', headerName: 'Document', flex: 1, minWidth: 260,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.3, minWidth: 0 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>{humanize(row.docType)}{row.direction === 'OUTBOUND' ? ` · v${row.version}` : ''}</Typography>
          <Typography variant="caption" color="text.secondary" className="mono" noWrap component="div">{row.fileName}</Typography>
        </Box>
      ),
    },
    {
      field: 'tradeRef', headerName: 'Trade', width: 220,
      renderCell: ({ row }) => row.tradeId ? (
        <Box sx={{ lineHeight: 1.3, minWidth: 0 }} onClick={(e) => e.stopPropagation()}>
          <Typography component={RouterLink} to={`/trades/${row.tradeId}`} variant="body2" className="mono" sx={{ display: 'block' }}>{row.tradeRef}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap component="div">{row.counterparty}</Typography>
        </Box>
      ) : <Typography variant="body2" color="text.disabled">Unlinked</Typography>,
    },
    { field: 'stage', headerName: 'Stage', width: 100, renderCell: ({ value }) => <ToneChip value={value} /> },
    {
      field: 'processingStatus', headerName: 'Source / result', width: 150,
      renderCell: ({ row }) => row.processingStatus ? <ToneChip value={row.processingStatus} /> : <Typography variant="body2" color="text.secondary">{humanize(row.source)}</Typography>,
    },
    { field: 'sizeBytes', headerName: 'Size', width: 84, align: 'right', headerAlign: 'right', valueFormatter: (v: number) => fmtBytes(v) },
    { field: 'sha256', headerName: 'SHA-256', width: 130, renderCell: ({ value }) => <Hash value={value} /> },
    {
      field: 'createdAt', headerName: 'Created', width: 160,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.3 }}>
          <Typography variant="body2">{fmtDateTime(row.createdAt)}</Typography>
          <Typography variant="caption" color="text.secondary">{row.createdBy}</Typography>
        </Box>
      ),
    },
  ], [])

  return (
    <>
      <PageHeader
        title="Document repository"
        subtitle="Every confirmation issued and every client document received – versioned, fingerprinted (SHA-256) and linked to its trade."
      />
      <Paper sx={{ overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', gap: 1.5, p: 1.5, flexWrap: 'wrap', borderBottom: 1, borderColor: 'divider' }}>
          <TextField placeholder="File name, trade ref, counterparty or hash" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ width: { xs: '100%', sm: 340 } }}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
          <TextField select label="Type" value={docType} onChange={(e) => setDocType(e.target.value)} sx={{ width: 220 }}>
            <MenuItem value="">All types</MenuItem>
            {TYPES.map((t) => <MenuItem key={t} value={t}>{humanize(t)}</MenuItem>)}
          </TextField>
          <TextField select label="Direction" value={direction} onChange={(e) => setDirection(e.target.value)} sx={{ width: 160 }}>
            <MenuItem value="">Both</MenuItem>
            <MenuItem value="OUTBOUND">Outbound</MenuItem>
            <MenuItem value="INBOUND">Inbound</MenuItem>
          </TextField>
        </Box>
        <DataGrid
          rows={data?.content ?? []}
          columns={columns}
          rowCount={data?.totalElements ?? 0}
          loading={isFetching}
          paginationMode="server"
          paginationModel={pagination}
          onPaginationModelChange={setPagination}
          pageSizeOptions={[25, 50, 100]}
          onRowClick={({ row }) => setPreview(row)}
          rowHeight={54}
          autoHeight
          disableColumnMenu
          disableColumnSorting
          disableRowSelectionOnClick
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' }, '& .MuiDataGrid-cell': { display: 'flex', alignItems: 'center' } }}
        />
      </Paper>
      {preview && (
        <PdfDialog open onClose={() => setPreview(null)} src={`/documents/${preview.id}/content`}
          title={`${humanize(preview.docType)}${preview.tradeRef ? ` · ${preview.tradeRef}` : ''}`}
          subtitle={`${preview.fileName} · sha256 ${preview.sha256}`} fileName={preview.fileName} />
      )}
    </>
  )
}
