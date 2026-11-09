import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined'
import GridOnOutlinedIcon from '@mui/icons-material/GridOnOutlined'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import TableChartOutlinedIcon from '@mui/icons-material/TableChartOutlined'
import { Box, Button, MenuItem, Paper, TextField, Typography } from '@mui/material'
import { useState, type ReactNode } from 'react'
import { downloadFile } from '../api/client'
import type { Queue } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { PdfDialog } from '../components/PdfViewer'

const SCOPES: { id: Queue; label: string }[] = [
  { id: 'ALL', label: 'Entire book' },
  { id: 'ACTION_REQUIRED', label: 'Action required' },
  { id: 'AWAITING_CLIENT', label: 'Awaiting client' },
  { id: 'BREAKS', label: 'Breaks' },
  { id: 'SLA_BREACHED', label: 'SLA breached' },
  { id: 'CONFIRMED', label: 'Confirmed' },
]

export default function ReportsPage() {
  const [scope, setScope] = useState<Queue>('ALL')
  const [preview, setPreview] = useState(false)
  return (
    <>
      <PageHeader title="Reports" subtitle="Operational and control reporting for FX confirmations." />
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', xl: 'repeat(3, 1fr)' } }}>
        <ReportCard
          icon={<PictureAsPdfOutlinedIcon />}
          title="Outstanding confirmations"
          description="End-of-day control report of every unconfirmed trade, oldest first, with SLA status and chaser count. Typically circulated to desk heads and operational risk."
          actions={
            <>
              <Button variant="contained" onClick={() => setPreview(true)}>Preview</Button>
              <Button startIcon={<DownloadOutlinedIcon />} onClick={() => downloadFile('/reports/outstanding.pdf', 'outstanding-confirmations.pdf')}>Download PDF</Button>
            </>
          }
        />
        <ReportCard
          icon={<GridOnOutlinedIcon />}
          title="Confirmation blotter extract"
          description="Full trade-level extract with economics, status, dispatch time, SLA and chaser data – for reconciliation and MI."
          extra={
            <TextField select size="small" label="Scope" value={scope} onChange={(e) => setScope(e.target.value as Queue)} sx={{ mt: 2, width: 220 }}>
              {SCOPES.map((s) => <MenuItem key={s.id} value={s.id}>{s.label}</MenuItem>)}
            </TextField>
          }
          actions={
            <>
              <Button variant="contained" startIcon={<TableChartOutlinedIcon />} onClick={() => downloadFile(`/reports/blotter.xlsx?queue=${scope}`, 'confirmations.xlsx')}>Excel</Button>
              <Button startIcon={<DownloadOutlinedIcon />} onClick={() => downloadFile(`/reports/blotter.csv?queue=${scope}`, 'confirmations.csv')}>CSV</Button>
            </>
          }
        />
      </Box>
      <PdfDialog open={preview} onClose={() => setPreview(false)} src="/reports/outstanding.pdf" title="Outstanding confirmations report" fileName="outstanding-confirmations.pdf" />
    </>
  )
}

function ReportCard({ icon, title, description, actions, extra }: { icon: ReactNode; title: string; description: string; actions: ReactNode; extra?: ReactNode }) {
  return (
    <Paper sx={{ p: 2.5, display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
        <Box sx={{ width: 40, height: 40, borderRadius: 1.5, display: 'grid', placeItems: 'center', bgcolor: 'action.hover', color: 'primary.main' }}>{icon}</Box>
        <Typography variant="subtitle1">{title}</Typography>
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5, flex: 1 }}>{description}</Typography>
      {extra}
      <Box sx={{ display: 'flex', gap: 1, mt: 2 }}>{actions}</Box>
    </Paper>
  )
}
