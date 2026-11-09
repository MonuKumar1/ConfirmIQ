import { Box, MenuItem, Paper, TextField, Typography } from '@mui/material'
import { DataGrid, type GridColDef } from '@mui/x-data-grid'
import { useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useAudit, useUsers } from '../api/hooks'
import type { AuditView } from '../api/types'
import { fmtDateTime, humanize } from '../app/format'
import { PageHeader } from '../components/PageHeader'

const ENTITY_TYPES = ['TRADE', 'CONFIRMATION', 'DOCUMENT', 'COUNTERPARTY', 'TEMPLATE', 'USER']

export default function AuditPage() {
  const [actor, setActor] = useState('')
  const [entityType, setEntityType] = useState('')
  const [pagination, setPagination] = useState({ page: 0, pageSize: 50 })
  const { data, isFetching } = useAudit({ actor: actor || undefined, entityType: entityType || undefined, page: pagination.page, size: pagination.pageSize })
  const { data: users = [] } = useUsers()

  const columns = useMemo<GridColDef<AuditView>[]>(() => [
    { field: 'occurredAt', headerName: 'Time', width: 158, valueFormatter: (v: string) => fmtDateTime(v) },
    {
      field: 'actor', headerName: 'Actor', width: 150,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.3 }}>
          <Typography variant="body2" className="mono">{row.actor}</Typography>
          <Typography variant="caption" color="text.secondary">{humanize(row.actorRole)}</Typography>
        </Box>
      ),
    },
    { field: 'action', headerName: 'Action', width: 190, renderCell: ({ value }) => <Typography variant="body2" sx={{ fontWeight: 600 }}>{humanize(value)}</Typography> },
    { field: 'entityType', headerName: 'Entity', width: 120, valueFormatter: (v: string) => humanize(v) },
    {
      field: 'tradeId', headerName: 'Trade', width: 90,
      renderCell: ({ row }) => row.tradeId ? <RouterLink to={`/trades/${row.tradeId}`}>#{row.tradeId}</RouterLink> : '—',
    },
    {
      field: 'transition', headerName: 'Transition', width: 230, sortable: false,
      renderCell: ({ row }) => row.fromStatus && row.toStatus ? <span className="mono" style={{ fontSize: 12 }}>{row.fromStatus} → {row.toStatus}</span> : '',
    },
    {
      field: 'details', headerName: 'Details', flex: 1, minWidth: 280,
      renderCell: ({ row }) => (
        <Box sx={{ lineHeight: 1.3, py: 0.5, minWidth: 0 }}>
          <Typography variant="body2" noWrap title={row.details}>{row.details}</Typography>
          {row.comment && <Typography variant="caption" color="text.secondary" noWrap component="div" title={row.comment}>“{row.comment}”</Typography>}
        </Box>
      ),
    },
  ], [])

  return (
    <>
      <PageHeader title="Audit trail" subtitle="Append-only record of every state change, document, static-data edit and sign-in – who, what, when and why." />
      <Paper sx={{ overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', gap: 1.5, p: 1.5, flexWrap: 'wrap', borderBottom: 1, borderColor: 'divider' }}>
          <TextField select label="Actor" value={actor} onChange={(e) => { setActor(e.target.value); setPagination((p) => ({ ...p, page: 0 })) }} sx={{ width: 220 }}>
            <MenuItem value="">All actors</MenuItem>
            <MenuItem value="system">system</MenuItem>
            {users.map((u) => <MenuItem key={u.username} value={u.username}>{u.username} · {u.fullName}</MenuItem>)}
          </TextField>
          <TextField select label="Entity" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPagination((p) => ({ ...p, page: 0 })) }} sx={{ width: 180 }}>
            <MenuItem value="">All entities</MenuItem>
            {ENTITY_TYPES.map((t) => <MenuItem key={t} value={t}>{humanize(t)}</MenuItem>)}
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
          pageSizeOptions={[50, 100, 200]}
          rowHeight={52}
          autoHeight
          disableColumnMenu
          disableColumnSorting
          disableRowSelectionOnClick
          sx={{ border: 0, '& .MuiDataGrid-cell': { display: 'flex', alignItems: 'center' } }}
        />
      </Paper>
    </>
  )
}
