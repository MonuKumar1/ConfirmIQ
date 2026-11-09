import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined'
import HourglassBottomOutlinedIcon from '@mui/icons-material/HourglassBottomOutlined'
import MarkEmailReadOutlinedIcon from '@mui/icons-material/MarkEmailReadOutlined'
import PendingActionsOutlinedIcon from '@mui/icons-material/PendingActionsOutlined'
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined'
import TaskAltOutlinedIcon from '@mui/icons-material/TaskAltOutlined'
import {
  Alert, Box, Chip, LinearProgress, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, ToggleButton,
  ToggleButtonGroup, Tooltip, Typography, useTheme,
} from '@mui/material'
import { BarChart } from '@mui/x-charts/BarChart'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useDashboard } from '../api/hooks'
import type { ConfirmationStatus, ProductType } from '../api/types'
import { dayjs, fmtDateTime, fromNow, PRODUCT_LABEL } from '../app/format'
import { useAuth } from '../auth/AuthContext'
import { KpiCard } from '../components/KpiCard'
import { PageHeader } from '../components/PageHeader'
import { Section } from '../components/Section'
import { STATUS_META, StatusChip } from '../components/StatusChip'

/** Categorical series colours, validated for CVD separation in both modes (see dataviz palette). */
function useSeries() {
  const dark = useTheme().palette.mode === 'dark'
  return dark
    ? { booked: '#3987e5', confirmed: '#199e70', breaks: '#d95926', single: '#3987e5' }
    : { booked: '#2a78d6', confirmed: '#1baf7a', breaks: '#eb6834', single: '#2a78d6' }
}

const PIPELINE: ConfirmationStatus[] = [
  'NEW', 'ENRICHED', 'DRAFTED', 'PENDING_APPROVAL', 'APPROVED', 'AWAITING_RESPONSE', 'MATCHED', 'MISMATCHED', 'DISPUTED',
]

export default function DashboardPage() {
  const [days, setDays] = useState(14)
  const { data, isLoading, error } = useDashboard(days)
  const { user } = useAuth()
  const colors = useSeries()
  const navigate = useNavigate()
  const greeting = dayjs().hour() < 12 ? 'Good morning' : dayjs().hour() < 18 ? 'Good afternoon' : 'Good evening'

  if (error) return <Alert severity="error">{errorMessage(error)}</Alert>
  const k = data?.kpis

  return (
    <>
      <PageHeader
        title={`${greeting}, ${user?.fullName.split(' ')[0]}`}
        subtitle={data ? `FX confirmations control view · as of ${fmtDateTime(data.asOf)}` : 'Loading control view…'}
      />

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(3, 1fr)', xl: 'repeat(6, 1fr)' }, mb: 2 }}>
        {isLoading || !k ? (
          Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} variant="rounded" height={104} />)
        ) : (
          <>
            <KpiCard label="Open confirmations" value={k.openConfirmations} hint={`${k.tradesToday} booked today`} to="/confirmations" icon={<ReceiptLongOutlinedIcon />} tone="primary" />
            <KpiCard label="Awaiting client" value={k.awaitingClient} hint="Sent, no response yet" to="/confirmations?queue=AWAITING_CLIENT" icon={<MarkEmailReadOutlinedIcon />} />
            <KpiCard label="Open breaks" value={k.breaks} hint="Mismatched or disputed" to="/confirmations?queue=BREAKS" icon={<ErrorOutlineIcon />} tone={k.breaks ? 'error' : undefined} />
            <KpiCard label="SLA breached" value={k.slaBreached} hint="Past client response deadline" to="/confirmations?queue=SLA_BREACHED" icon={<HourglassBottomOutlinedIcon />} tone={k.slaBreached ? 'warning' : undefined} />
            <KpiCard label="Pending approval" value={k.pendingApproval} hint="Awaiting four-eyes check" to="/approvals" icon={<PendingActionsOutlinedIcon />} />
            <KpiCard label="Confirmed today" value={k.confirmedToday} hint={`${k.totalTrades} trades in book`} icon={<TaskAltOutlinedIcon />} tone="success" />
          </>
        )}
      </Box>

      {k && (
        <Section sx={{ mb: 2 }} noPadding>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' }, '& > *:not(:last-child)': { borderRight: { lg: 1 }, borderColor: { lg: 'divider' } } }}>
            <RateTile label="First-time match rate" value={k.firstTimeMatchRate} unit="%" hint="Client paper matched on first receipt" />
            <RateTile label="Straight-through enrichment" value={k.enrichmentStpRate} unit="%" hint="Trades enriched without manual repair" />
            <RateTile label="Confirmed within SLA" value={k.slaCompliance} unit="%" hint="Of confirmed trades" />
            <RateTile label="Avg. time to confirm" value={k.avgHoursToConfirm} unit="h" hint="From dispatch to confirmation" plain />
          </Box>
        </Section>
      )}

      {data && (
        <Section title="Confirmation pipeline" subtitle="Open items by lifecycle stage – select a stage to open the blotter" sx={{ mb: 2 }}>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {PIPELINE.map((s, i) => (
              <Box key={s} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box
                  component={RouterLink}
                  to={`/confirmations?status=${s}`}
                  sx={{
                    textDecoration: 'none', color: 'inherit', px: 1.5, py: 1, borderRadius: 1.5, border: 1, borderColor: 'divider',
                    minWidth: 104, '&:hover': { borderColor: 'primary.main', bgcolor: 'action.hover' },
                  }}
                >
                  <Typography sx={{ fontSize: 20, fontWeight: 650, lineHeight: 1.2 }}>{data.byStatus[s] ?? 0}</Typography>
                  <Typography variant="caption" color="text.secondary" noWrap>{STATUS_META[s].label}</Typography>
                </Box>
                {i < PIPELINE.length - 1 && <Typography color="text.disabled" sx={{ display: { xs: 'none', xl: 'block' } }}>›</Typography>}
              </Box>
            ))}
          </Box>
        </Section>
      )}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' }, mb: 2 }}>
        <Section
          title="Daily activity"
          subtitle="Trades booked, confirmations completed and breaks raised per day"
          actions={
            <ToggleButtonGroup size="small" exclusive value={days} onChange={(_, v) => v && setDays(v)}>
              <ToggleButton value={7}>7D</ToggleButton>
              <ToggleButton value={14}>14D</ToggleButton>
              <ToggleButton value={30}>30D</ToggleButton>
            </ToggleButtonGroup>
          }
        >
          {data ? (
            <BarChart
              height={280}
              dataset={data.trend.map((d) => ({ ...d, label: dayjs(d.date).format('DD MMM') }))}
              xAxis={[{ scaleType: 'band', dataKey: 'label', tickLabelStyle: { fontSize: 11 } }]}
              yAxis={[{ tickLabelStyle: { fontSize: 11 }, width: 32 }]}
              series={[
                { dataKey: 'booked', label: 'Booked', color: colors.booked },
                { dataKey: 'confirmed', label: 'Confirmed', color: colors.confirmed },
                { dataKey: 'breaks', label: 'Breaks', color: colors.breaks },
              ]}
              borderRadius={4}
              grid={{ horizontal: true }}
              margin={{ left: 4, right: 8, top: 8, bottom: 4 }}
              slotProps={{ legend: { position: { vertical: 'top', horizontal: 'end' } } }}
              sx={{ '& .MuiChartsGrid-line': { strokeDasharray: '3 3', opacity: 0.6 } }}
            />
          ) : <Skeleton variant="rounded" height={280} />}
        </Section>

        <Section title="Ageing of open items" subtitle="Business days since trade date">
          {data ? (
            <BarChart
              height={280}
              dataset={Object.entries(data.ageing).map(([bucket, count]) => ({ bucket, count }))}
              xAxis={[{ scaleType: 'band', dataKey: 'bucket', tickLabelStyle: { fontSize: 11 } }]}
              yAxis={[{ tickLabelStyle: { fontSize: 11 }, width: 28 }]}
              series={[{ dataKey: 'count', label: 'Open confirmations', color: colors.single }]}
              borderRadius={4}
              grid={{ horizontal: true }}
              hideLegend
              margin={{ left: 4, right: 8, top: 8, bottom: 4 }}
              sx={{ '& .MuiChartsGrid-line': { strokeDasharray: '3 3', opacity: 0.6 } }}
            />
          ) : <Skeleton variant="rounded" height={280} />}
        </Section>
      </Box>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' } }}>
        <Section title="Requires attention" subtitle="Breaks, SLA breaches and escalations" noPadding>
          {data && data.attention.length === 0 ? (
            <Box sx={{ p: 3 }}><Typography color="text.secondary">Nothing outstanding – all open items are within SLA.</Typography></Box>
          ) : (
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Trade</TableCell>
                    <TableCell>Counterparty</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell>SLA due</TableCell>
                    <TableCell align="right">Chasers</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data?.attention.map((a) => (
                    <TableRow key={a.tradeId} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/trades/${a.tradeId}`)}>
                      <TableCell>
                        <Typography variant="body2" className="mono" sx={{ fontWeight: 600 }}>{a.tradeRef}</Typography>
                        <Typography variant="caption" color="text.secondary">{a.product}</Typography>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 220 }}><Typography variant="body2" noWrap>{a.counterparty}</Typography></TableCell>
                      <TableCell><StatusChip status={a.status} /></TableCell>
                      <TableCell>
                        <Chip size="small" label={a.reason} color={a.reason === 'Escalated' ? 'error' : a.reason === 'SLA breached' ? 'warning' : 'default'} variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Tooltip title={fmtDateTime(a.slaDueAt)}><span>{fromNow(a.slaDueAt)}</span></Tooltip>
                      </TableCell>
                      <TableCell align="right">{a.chaserCount}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </Section>

        <Box sx={{ display: 'grid', gap: 2, alignContent: 'start' }}>
          <Section title="Open items by counterparty">
            {data?.byCounterparty.map((c) => {
              const max = Math.max(...data.byCounterparty.map((x) => x.open), 1)
              return (
                <Box key={c.counterparty} sx={{ mb: 1.25 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                    <Typography variant="body2" noWrap sx={{ minWidth: 0 }}>{c.counterparty}</Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                      {c.open}
                      {c.breaks > 0 && <Typography component="span" variant="caption" color="error.main"> · {c.breaks} break{c.breaks > 1 ? 's' : ''}</Typography>}
                    </Typography>
                  </Box>
                  <LinearProgress variant="determinate" value={(c.open / max) * 100} sx={{ height: 6, borderRadius: 3, mt: 0.5, bgcolor: 'action.hover', '& .MuiLinearProgress-bar': { borderRadius: 3, bgcolor: colors.single } }} />
                </Box>
              )
            })}
          </Section>
          <Section title="Book by product">
            {data && (Object.entries(data.byProduct) as [ProductType, number][]).map(([p, n]) => (
              <Box key={p} sx={{ display: 'flex', justifyContent: 'space-between', py: 0.6, borderBottom: 1, borderColor: 'divider', '&:last-child': { border: 0 } }}>
                <Typography variant="body2">{PRODUCT_LABEL[p]}</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {n} <Typography component="span" variant="caption" color="text.secondary">({Math.round((n / Math.max(data.kpis.totalTrades, 1)) * 100)}%)</Typography>
                </Typography>
              </Box>
            ))}
          </Section>
        </Box>
      </Box>
    </>
  )
}

function RateTile({ label, value, unit, hint, plain }: { label: string; value: number; unit: string; hint: string; plain?: boolean }) {
  const tone = plain ? 'text.primary' : value >= 90 ? 'success.main' : value >= 70 ? 'warning.main' : 'error.main'
  return (
    <Box sx={{ px: 2, py: 1.75 }}>
      <Typography variant="overline" color="text.secondary">{label}</Typography>
      <Typography sx={{ fontSize: 22, fontWeight: 650, color: tone, lineHeight: 1.3 }}>
        {value}
        <Typography component="span" sx={{ fontSize: 14, ml: 0.25, color: 'text.secondary' }}>{unit}</Typography>
      </Typography>
      <Typography variant="caption" color="text.secondary">{hint}</Typography>
    </Box>
  )
}
