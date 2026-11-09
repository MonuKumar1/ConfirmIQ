import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined'
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord'
import SearchIcon from '@mui/icons-material/Search'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert, Badge, Box, Button, Chip, InputAdornment, Menu, MenuItem, Paper, Tab, Tabs, TextField, Tooltip, Typography,
} from '@mui/material'
import { DataGrid, type GridColDef, type GridSortModel } from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { downloadFile, errorMessage } from '../api/client'
import { useBlotter, useCounterparties, useQueueCounts } from '../api/hooks'
import type { BlotterRow, ConfirmationStatus, Queue } from '../api/types'
import { fmtAmount, fmtDate, fmtDateTime, fmtRate, fromNow, PRODUCT_LABEL } from '../app/format'
import { PageHeader } from '../components/PageHeader'
import { STATUS_META, StatusChip } from '../components/StatusChip'

const QUEUES: { id: Queue; label: string; hint: string }[] = [
  { id: 'ACTION_REQUIRED', label: 'Action required', hint: 'Items the operations team needs to move forward' },
  { id: 'PENDING_APPROVAL', label: 'Pending approval', hint: 'Awaiting four-eyes check' },
  { id: 'AWAITING_CLIENT', label: 'Awaiting client', hint: 'Sent, no client response yet' },
  { id: 'BREAKS', label: 'Breaks', hint: 'Mismatched or disputed' },
  { id: 'SLA_BREACHED', label: 'SLA breached', hint: 'Past the client response deadline' },
  { id: 'ESCALATED', label: 'Escalated', hint: 'Escalated after repeated chasers' },
  { id: 'CONFIRMED', label: 'Confirmed', hint: 'Fully confirmed' },
  { id: 'CANCELLED', label: 'Cancelled', hint: 'Cancelled trades' },
  { id: 'ALL', label: 'All', hint: 'Entire book' },
]

const BADGE_COLOR: Partial<Record<Queue, 'error' | 'warning' | 'primary' | 'default'>> = {
  BREAKS: 'error', SLA_BREACHED: 'warning', ESCALATED: 'error', PENDING_APPROVAL: 'warning', ACTION_REQUIRED: 'primary',
}

export default function BlotterPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const queue = (params.get('queue') as Queue) || (params.get('status') ? 'ALL' : 'ACTION_REQUIRED')
  const status = params.get('status') as ConfirmationStatus | null
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [productType, setProductType] = useState('')
  const [counterpartyId, setCounterpartyId] = useState<number | ''>('')
  const [pagination, setPagination] = useState({ page: 0, pageSize: 25 })
  const [sortModel, setSortModel] = useState<GridSortModel>([])
  const [exportEl, setExportEl] = useState<HTMLElement | null>(null)

  useEffect(() => {
    const t = setTimeout(() => setQ(search.trim()), 300)
    return () => clearTimeout(t)
  }, [search])

  useEffect(() => setPagination((p) => ({ ...p, page: 0 })), [queue, status, q, productType, counterpartyId])

  const sort = sortModel[0] ? `${sortModel[0].field},${sortModel[0].sort}` : undefined
  const { data, isFetching, error } = useBlotter({
    queue, q: q || undefined, productType: productType || undefined, counterpartyId: counterpartyId || undefined,
    page: pagination.page, size: pagination.pageSize, sort, status: status ?? undefined,
  })
  const { data: counts } = useQueueCounts()
  const { data: counterparties } = useCounterparties()

  const columns = useMemo<GridColDef<BlotterRow>[]>(() => [
    {
      field: 'tradeRef', headerName: 'Trade ref', width: 160,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.25, py: 0.5 }}>
          <Typography variant="body2" className="mono" sx={{ fontWeight: 600 }}>{row.tradeRef}</Typography>
          <Typography variant="caption" color="text.secondary" className="mono">{row.externalRef}</Typography>
        </Box>
      ),
    },
    {
      field: 'counterparty', headerName: 'Counterparty', flex: 1, minWidth: 170,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.25, py: 0.5, minWidth: 0 }}>
          <Typography variant="body2" noWrap>{row.counterparty}</Typography>
          <Typography variant="caption" color="text.secondary">{row.counterpartyCode}</Typography>
        </Box>
      ),
    },
    {
      field: 'productType', headerName: 'Product', width: 116,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.25 }}>
          <Typography variant="body2">{PRODUCT_LABEL[row.productType]}</Typography>
          <Typography variant="caption" color="text.secondary" className="mono">{row.currencyPair}</Typography>
        </Box>
      ),
    },
    {
      field: 'buyAmount', headerName: 'Economics (we buy / we sell @ rate)', width: 230, align: 'right', headerAlign: 'right',
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.3, textAlign: 'right' }} className="mono">
          <Typography variant="body2" component="div" sx={{ fontFamily: 'inherit' }}>{row.buyCurrency} {fmtAmount(row.buyAmount, row.buyCurrency)}</Typography>
          <Typography variant="caption" color="text.secondary" component="div" sx={{ fontFamily: 'inherit' }}>
            {row.sellCurrency} {fmtAmount(row.sellAmount, row.sellCurrency)} @ {fmtRate(row.rate)}
          </Typography>
        </Box>
      ),
    },
    {
      field: 'tradeDate', headerName: 'Trade / value', width: 112,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.25 }}>
          <Typography variant="body2">{fmtDate(row.tradeDate)}</Typography>
          <Typography variant="caption" color="text.secondary">{fmtDate(row.valueDate)}</Typography>
        </Box>
      ),
    },
    {
      field: 'status', headerName: 'Status', width: 156,
      renderCell: ({ row }) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <StatusChip status={row.status} />
          {row.economicVersion > 1 && <Tooltip title={`Amended – economic version ${row.economicVersion}`}><Chip size="small" label={`v${row.economicVersion}`} sx={{ height: 18, fontSize: 10 }} /></Tooltip>}
        </Box>
      ),
    },
    {
      field: 'slaDueAt', headerName: 'Client SLA', width: 128,
      renderCell: ({ row }) =>
        row.slaDueAt && !['CONFIRMED', 'CANCELLED', 'MATCHED'].includes(row.status) ? (
          <Tooltip title={`Due ${fmtDateTime(row.slaDueAt)}${row.chaserCount ? ` · ${row.chaserCount} chaser(s)` : ''}`}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: row.slaBreached ? 'error.main' : 'text.secondary' }}>
              {row.slaBreached ? <WarningAmberRoundedIcon sx={{ fontSize: 16 }} /> : <FiberManualRecordIcon sx={{ fontSize: 8, color: 'success.main' }} />}
              <Typography variant="body2" sx={{ color: 'inherit', fontWeight: row.slaBreached ? 600 : 400 }}>
                {row.slaBreached ? `Overdue ${fromNow(row.slaDueAt).replace(' ago', '')}` : fromNow(row.slaDueAt)}
              </Typography>
            </Box>
          </Tooltip>
        ) : <Typography variant="body2" color="text.disabled">—</Typography>,
    },
    { field: 'ageDays', headerName: 'Age', width: 58, align: 'right', headerAlign: 'right', sortable: false, valueFormatter: (v: number) => `T+${v}` },
  ], [])

  const setQueue = (next: Queue) => {
    const p = new URLSearchParams()
    p.set('queue', next)
    setParams(p)
  }

  return (
    <>
      <PageHeader
        title="Confirmations"
        subtitle="Confirmation blotter across all FX products. Select a trade to open its confirmation workspace."
        actions={
          <>
            <Button variant="outlined" startIcon={<DownloadOutlinedIcon />} onClick={(e) => setExportEl(e.currentTarget)}>Export</Button>
            <Menu anchorEl={exportEl} open={!!exportEl} onClose={() => setExportEl(null)}>
              <MenuItem onClick={() => { setExportEl(null); void downloadFile(`/reports/blotter.xlsx?queue=${queue}${q ? `&q=${encodeURIComponent(q)}` : ''}`, 'confirmations.xlsx') }}>Excel (.xlsx)</MenuItem>
              <MenuItem onClick={() => { setExportEl(null); void downloadFile(`/reports/blotter.csv?queue=${queue}${q ? `&q=${encodeURIComponent(q)}` : ''}`, 'confirmations.csv') }}>CSV</MenuItem>
            </Menu>
          </>
        }
      />

      <Paper sx={{ overflow: 'hidden' }}>
        <Tabs value={status ? false : queue} onChange={(_, v) => setQueue(v)} variant="scrollable" scrollButtons="auto" sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
          {QUEUES.map((qu) => (
            <Tab
              key={qu.id}
              value={qu.id}
              title={qu.hint}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {qu.label}
                  {counts && (
                    <Badge
                      badgeContent={counts[qu.id]}
                      max={999}
                      showZero
                      color={(counts[qu.id] && BADGE_COLOR[qu.id]) || 'default'}
                      sx={{ '& .MuiBadge-badge': { position: 'static', transform: 'none', fontSize: 10.5, height: 18, minWidth: 20, ...(BADGE_COLOR[qu.id] && counts[qu.id] ? {} : { bgcolor: 'action.selected' }) } }}
                    />
                  )}
                </Box>
              }
            />
          ))}
        </Tabs>

        <Box sx={{ display: 'flex', gap: 1.5, p: 1.5, flexWrap: 'wrap', alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
          <TextField
            placeholder="Trade ref, FO ref or counterparty"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            sx={{ width: { xs: '100%', sm: 300 } }}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
          />
          <TextField select label="Product" value={productType} onChange={(e) => setProductType(e.target.value)} sx={{ width: 160 }}>
            <MenuItem value="">All products</MenuItem>
            {Object.entries(PRODUCT_LABEL).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
          </TextField>
          <TextField select label="Counterparty" value={counterpartyId} onChange={(e) => setCounterpartyId(e.target.value === '' ? '' : Number(e.target.value))} sx={{ width: 240 }}>
            <MenuItem value="">All counterparties</MenuItem>
            {counterparties?.map((c) => <MenuItem key={c.id} value={c.id}>{c.legalName}</MenuItem>)}
          </TextField>
          {status && (
            <Chip label={`Status: ${STATUS_META[status].label}`} onDelete={() => setQueue('ALL')} color="primary" variant="outlined" />
          )}
          <Box sx={{ flex: 1 }} />
          <Typography variant="caption" color="text.secondary">
            {data ? `${data.totalElements} confirmation${data.totalElements === 1 ? '' : 's'}` : ''}
          </Typography>
        </Box>

        {error && <Alert severity="error" sx={{ m: 1.5 }}>{errorMessage(error)}</Alert>}
        <DataGrid
          rows={data?.content ?? []}
          columns={columns}
          getRowId={(r) => r.tradeId}
          rowCount={data?.totalElements ?? 0}
          loading={isFetching}
          paginationMode="server"
          sortingMode="server"
          paginationModel={pagination}
          onPaginationModelChange={setPagination}
          sortModel={sortModel}
          onSortModelChange={setSortModel}
          pageSizeOptions={[25, 50, 100]}
          onRowClick={({ row }) => navigate(`/trades/${row.tradeId}`)}
          getRowClassName={({ row }) => (row.slaBreached ? 'row-breach' : '')}
          rowHeight={52}
          disableRowSelectionOnClick
          disableColumnMenu
          autoHeight
          sx={{
            border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' },
            '& .row-breach': { boxShadow: (t) => `inset 3px 0 0 ${t.palette.error.main}` },
            '& .MuiDataGrid-columnHeaderTitle': { fontWeight: 600, fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.03em' },
            '& .MuiDataGrid-cell': { display: 'flex', alignItems: 'center' },
          }}
          localeText={{ noRowsLabel: 'No confirmations in this queue' }}
        />
      </Paper>
    </>
  )
}
