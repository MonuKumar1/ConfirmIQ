import CodeOutlinedIcon from '@mui/icons-material/CodeOutlined'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import {
  Alert, Box, Button, Link, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import type { FileIngestionResult } from '../api/types'
import { useNotify } from '../app/notify'
import { Dropzone } from '../components/Dropzone'
import { PageHeader } from '../components/PageHeader'
import { Section } from '../components/Section'
import { ToneChip } from '../components/StatusChip'

const API_EXAMPLE = `POST /api/v1/trades
Authorization: Bearer <token>
Content-Type: application/json

{
  "externalRef": "FO-2026-99001",
  "sourceSystem": "MUREX-FX",
  "productType": "FX_SPOT",
  "counterparty": "HELIX",
  "tradeDate": "2026-10-27",
  "executionTime": "2026-10-27T09:31:12Z",
  "valueDate": "2026-10-29",
  "buyCurrency": "EUR", "buyAmount": 5000000.00,
  "sellCurrency": "USD", "sellAmount": 5426500.00,
  "rate": 1.0853,
  "book": "FXG10-LDN", "trader": "a.kowalski"
}`

export default function IngestionPage() {
  const [results, setResults] = useState<FileIngestionResult[]>([])
  const [busy, setBusy] = useState(false)
  const notify = useNotify()
  const qc = useQueryClient()

  const upload = async (files: File[]) => {
    setBusy(true)
    try {
      const form = new FormData()
      files.forEach((f) => form.append('files', f))
      const { data } = await api.post<FileIngestionResult[]>('/trades/upload', form)
      setResults(data)
      const accepted = data.reduce((n, f) => n + f.accepted, 0)
      notify(`${accepted} trade(s) booked from ${data.length} file(s)`, accepted ? 'success' : 'warning')
      await qc.invalidateQueries()
    } catch (e) {
      notify(errorMessage(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Trade ingestion"
        subtitle="Book trades from upstream front-office systems. Messages are schema-validated, checked for business consistency and de-duplicated on the external reference."
      />
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', lg: '1.5fr 1fr' }, alignItems: 'start' }}>
        <Box sx={{ display: 'grid', gap: 2 }}>
          <Section title="Upload booking files" subtitle="tradeBooking XML (urn:meridian:confirmiq:booking:1.0)">
            <Dropzone accept=".xml,text/xml,application/xml" onFiles={upload} disabled={busy} title={busy ? 'Validating and booking…' : 'Drop XML booking files here or click to browse'} hint="Up to 500 trades per file. Each trade is processed independently." />
          </Section>

          {results.map((f) => (
            <Section key={f.fileName} title={f.fileName} subtitle={f.error ? 'File rejected' : `${f.accepted} of ${f.trades.length} trade(s) accepted`} noPadding>
              {f.error ? (
                <Alert severity="error" sx={{ m: 2 }}>{f.error}</Alert>
              ) : (
                <Box sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>External ref</TableCell>
                        <TableCell>Outcome</TableCell>
                        <TableCell>Trade</TableCell>
                        <TableCell>Detail</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {f.trades.map((t) => (
                        <TableRow key={t.externalRef}>
                          <TableCell className="mono">{t.externalRef}</TableCell>
                          <TableCell><ToneChip value={t.outcome} /></TableCell>
                          <TableCell>{t.tradeId ? <Link component={RouterLink} to={`/trades/${t.tradeId}`} className="mono">{t.tradeRef}</Link> : '—'}</TableCell>
                          <TableCell sx={{ color: t.outcome === 'REJECTED' ? 'error.main' : t.message.startsWith('Exception') ? 'warning.main' : 'text.secondary' }}>{t.message}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </Section>
          ))}
        </Box>

        <Box sx={{ display: 'grid', gap: 2 }}>
          <Section title="Samples & schema">
            {[
              ['booking-batch-mixed.xml', 'One trade per product: spot, forward, swap, NDF'],
              ['booking-with-errors.xml', 'Unknown counterparty, rate inconsistency, missing SSI'],
              ['trade-booking-v1.xsd', 'XML schema used for validation'],
            ].map(([file, desc]) => (
              <Box key={file} sx={{ display: 'flex', gap: 1.5, py: 1, borderBottom: 1, borderColor: 'divider', '&:last-child': { border: 0 } }}>
                <DescriptionOutlinedIcon color="action" fontSize="small" sx={{ mt: 0.25 }} />
                <Box sx={{ flex: 1 }}>
                  <Link href={`/samples/${file}`} download className="mono" sx={{ fontSize: 13 }}>{file}</Link>
                  <Typography variant="caption" color="text.secondary" component="div">{desc}</Typography>
                </Box>
              </Box>
            ))}
          </Section>
          <Section title="Processing rules">
            <Box component="ol" sx={{ m: 0, pl: 2.5, '& li': { mb: 0.75, fontSize: 13 } }}>
              <li>XSD validation (structure, ISO currency codes, positive amounts). XXE-safe parser.</li>
              <li>Idempotency: an external reference already booked is ignored, not duplicated.</li>
              <li>Business rules: currencies differ, value date is a business day, spot ≤ T+2, forward &gt; spot, swap far &gt; near, NDF fixing &lt; settlement, amounts consistent with rate (market convention, 0.1%).</li>
              <li>Straight-through enrichment when counterparty contacts and SSIs exist for every settlement currency; otherwise the trade lands in the exception queue.</li>
            </Box>
          </Section>
          <Section title="REST API" actions={<Button size="small" startIcon={<CodeOutlinedIcon />} href="/swagger-ui.html" target="_blank">OpenAPI</Button>}>
            <Paper variant="outlined" component="pre" sx={{ m: 0, p: 1.5, fontSize: 11.5, overflowX: 'auto', bgcolor: 'action.hover' }} className="mono">{API_EXAMPLE}</Paper>
          </Section>
        </Box>
      </Box>
    </>
  )
}
