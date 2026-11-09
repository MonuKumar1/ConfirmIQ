import { Alert, Box, Chip, Paper, Skeleton, Tab, Tabs, Typography } from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePortalConfirmations } from '../api/hooks'
import type { PortalRow } from '../api/types'
import { fmtAmount, fmtDate, fmtDateTime, fmtRate, fromNow } from '../app/format'
import { useAuth } from '../auth/AuthContext'
import { KpiCard } from '../components/KpiCard'
import { PageHeader } from '../components/PageHeader'
import { ToneChip } from '../components/StatusChip'

const STATUS_LABEL: Record<PortalRow['status'], string> = {
  ACTION_REQUIRED: 'Action required', MATCHED: 'Matched', UNDER_REVIEW: 'Under review', CONFIRMED: 'Confirmed',
}

export default function PortalHome() {
  const { data = [], isLoading } = usePortalConfirmations()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState<'open' | 'all'>('open')
  const open = data.filter((r) => r.status !== 'CONFIRMED')
  const rows = tab === 'open' ? open : data

  const columns = useMemo<GridColDef<PortalRow>[]>(() => [
    { field: 'tradeRef', headerName: 'Meridian ref', width: 160, renderCell: ({ value }) => <span className="mono" style={{ fontWeight: 600 }}>{value}</span> },
    { field: 'product', headerName: 'Product', width: 150 },
    { field: 'tradeDate', headerName: 'Trade date', width: 104, valueFormatter: (v: string) => fmtDate(v) },
    { field: 'valueDate', headerName: 'Value date', width: 104, valueFormatter: (v: string) => fmtDate(v) },
    { field: 'youBuyAmount', headerName: 'You buy', width: 160, align: 'right', headerAlign: 'right', renderCell: ({ row }) => <span className="mono">{row.youBuyCurrency} {fmtAmount(row.youBuyAmount, row.youBuyCurrency)}</span> },
    { field: 'youSellAmount', headerName: 'You sell', width: 160, align: 'right', headerAlign: 'right', renderCell: ({ row }) => <span className="mono">{row.youSellCurrency} {fmtAmount(row.youSellAmount, row.youSellCurrency)}</span> },
    { field: 'rate', headerName: 'Rate', width: 84, align: 'right', headerAlign: 'right', renderCell: ({ value }) => <span className="mono">{fmtRate(value)}</span> },
    { field: 'status', headerName: 'Status', width: 130, renderCell: ({ value }) => <ToneChip value={value} label={STATUS_LABEL[value as PortalRow['status']]} /> },
    {
      field: 'responseDueAt', headerName: 'Response due', flex: 1, minWidth: 120,
      renderCell: ({ row }) => row.status === 'ACTION_REQUIRED' && row.responseDueAt ? (
        <Typography variant="body2" color={row.overdue ? 'error.main' : 'text.primary'} title={fmtDateTime(row.responseDueAt)} sx={{ fontWeight: row.overdue ? 600 : 400 }}>
          {row.overdue ? `Overdue (${fromNow(row.responseDueAt)})` : fromNow(row.responseDueAt)}
        </Typography>
      ) : '—',
    },
  ], [])

  const actionRequired = data.filter((r) => r.status === 'ACTION_REQUIRED').length
  const overdue = data.filter((r) => r.overdue).length

  return (
    <>
      <PageHeader title="FX confirmations" subtitle={`${user?.counterpartyName} · Review, affirm or dispute confirmations issued by Meridian Capital.`} />
      {overdue > 0 && <Alert severity="warning" sx={{ mb: 2 }}>{overdue} confirmation(s) are past the agreed response time. Please review them as a priority.</Alert>}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, mb: 2 }}>
        {isLoading ? Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} variant="rounded" height={96} />) : (
          <>
            <KpiCard label="Action required" value={actionRequired} tone={actionRequired ? 'warning' : undefined} />
            <KpiCard label="Overdue" value={overdue} tone={overdue ? 'error' : undefined} />
            <KpiCard label="Under review" value={data.filter((r) => r.status === 'UNDER_REVIEW').length} />
            <KpiCard label="Confirmed" value={data.filter((r) => r.status === 'CONFIRMED').length} tone="success" />
          </>
        )}
      </Box>
      <Paper sx={{ overflow: 'hidden' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="open" label={<Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>Open <Chip size="small" label={open.length} /></Box>} />
          <Tab value="all" label="All confirmations" />
        </Tabs>
        <DataGrid rows={rows} columns={columns} getRowId={(r) => r.tradeId} loading={isLoading} autoHeight rowHeight={48}
          disableColumnMenu disableRowSelectionOnClick onRowClick={({ row }) => navigate(`/portal/confirmations/${row.tradeId}`)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }} pageSizeOptions={[25, 50]}
          localeText={{ noRowsLabel: 'No confirmations awaiting your attention' }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' }, '& .MuiDataGrid-cell': { display: 'flex', alignItems: 'center' } }} />
      </Paper>
    </>
  )
}
