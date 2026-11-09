import AddIcon from '@mui/icons-material/Add'
import SearchIcon from '@mui/icons-material/Search'
import { Box, Button, Chip, InputAdornment, Paper, TextField, Typography } from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCounterparties } from '../api/hooks'
import type { CounterpartySummary } from '../api/types'
import { humanize } from '../app/format'
import { useAuth } from '../auth/AuthContext'
import { PageHeader } from '../components/PageHeader'
import { CounterpartyDialog } from './CounterpartyPage'

export default function CounterpartiesPage() {
  const { data = [], isLoading } = useCounterparties()
  const [q, setQ] = useState('')
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const rows = data.filter((c) => !q || `${c.legalName} ${c.shortCode} ${c.lei}`.toLowerCase().includes(q.toLowerCase()))

  const columns = useMemo<GridColDef<CounterpartySummary>[]>(() => [
    {
      field: 'legalName', headerName: 'Counterparty', flex: 1, minWidth: 260,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.3, minWidth: 0 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>{row.legalName}</Typography>
          <Typography variant="caption" color="text.secondary" className="mono">{row.shortCode}</Typography>
        </Box>
      ),
    },
    { field: 'lei', headerName: 'LEI', width: 200, renderCell: ({ value }) => <span className="mono">{value}</span> },
    { field: 'country', headerName: 'Country', width: 84 },
    { field: 'clientType', headerName: 'Type', width: 140, valueFormatter: (v: string) => humanize(v) },
    { field: 'deliveryChannel', headerName: 'Delivery', width: 110, renderCell: ({ value }) => <Chip size="small" variant="outlined" label={value === 'PORTAL' ? 'Portal' : 'Email'} /> },
    { field: 'confirmationSlaHours', headerName: 'SLA', width: 80, valueFormatter: (v: number) => `${v}h` },
    { field: 'openConfirmations', headerName: 'Open', width: 80, align: 'right', headerAlign: 'right' },
    {
      field: 'breaks', headerName: 'Breaks', width: 84, align: 'right', headerAlign: 'right',
      renderCell: ({ value }) => <Typography variant="body2" color={value ? 'error.main' : 'text.secondary'} sx={{ fontWeight: value ? 700 : 400 }}>{value}</Typography>,
    },
    { field: 'active', headerName: 'Status', width: 96, renderCell: ({ value }) => <Chip size="small" label={value ? 'Active' : 'Inactive'} color={value ? 'success' : 'default'} variant="outlined" /> },
  ], [])

  return (
    <>
      <PageHeader
        title="Counterparties & SSIs"
        subtitle="Client static data: legal entity identifiers, confirmation contacts, delivery preferences and standard settlement instructions."
        actions={hasRole('SUPERVISOR') && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>New counterparty</Button>}
      />
      <Paper sx={{ overflow: 'hidden' }}>
        <Box sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <TextField placeholder="Name, code or LEI" value={q} onChange={(e) => setQ(e.target.value)} sx={{ width: { xs: '100%', sm: 320 } }}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        </Box>
        <DataGrid rows={rows} columns={columns} loading={isLoading} rowHeight={54} autoHeight hideFooter disableColumnMenu disableRowSelectionOnClick
          onRowClick={({ row }) => navigate(`/counterparties/${row.id}`)}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' }, '& .MuiDataGrid-cell': { display: 'flex', alignItems: 'center' } }} />
      </Paper>
      {creating && <CounterpartyDialog onClose={() => setCreating(false)} onSaved={(id) => navigate(`/counterparties/${id}`)} />}
    </>
  )
}
