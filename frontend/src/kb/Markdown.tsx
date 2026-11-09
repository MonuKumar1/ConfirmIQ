import { Alert, Box, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { ReactNode } from 'react'

/** Renders the small markdown subset used by knowledge-base articles. */
export function Markdown({ source }: { source: string }) {
  const lines = source.trim().split('\n')
  const out: ReactNode[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) { i++; continue }
    if (line.startsWith('## ')) {
      out.push(<Typography key={i} variant="h6" sx={{ mt: 3, mb: 1 }}>{inline(line.slice(3))}</Typography>)
      i++
    } else if (line.startsWith('### ')) {
      out.push(<Typography key={i} variant="subtitle1" sx={{ mt: 2, mb: 0.5 }}>{inline(line.slice(4))}</Typography>)
      i++
    } else if (line.startsWith('> ')) {
      out.push(<Alert key={i} severity="info" variant="outlined" sx={{ my: 2 }}>{inline(line.slice(2))}</Alert>)
      i++
    } else if (line.startsWith('|')) {
      const rows: string[][] = []
      while (i < lines.length && lines[i].startsWith('|')) {
        if (!/^\|[\s-|]+\|$/.test(lines[i])) rows.push(lines[i].split('|').slice(1, -1).map((c) => c.trim()))
        i++
      }
      out.push(
        <Box key={i} sx={{ overflowX: 'auto', my: 2, border: 1, borderColor: 'divider', borderRadius: 1 }}>
          <Table size="small">
            <TableHead><TableRow>{rows[0].map((c, j) => <TableCell key={j}>{inline(c)}</TableCell>)}</TableRow></TableHead>
            <TableBody>
              {rows.slice(1).map((r, k) => <TableRow key={k}>{r.map((c, j) => <TableCell key={j}>{inline(c)}</TableCell>)}</TableRow>)}
            </TableBody>
          </Table>
        </Box>,
      )
    } else if (/^(- |\d+\. )/.test(line)) {
      const ordered = /^\d+\. /.test(line)
      const items: string[] = []
      while (i < lines.length && (ordered ? /^\d+\. /.test(lines[i]) : lines[i].startsWith('- '))) {
        items.push(lines[i].replace(/^(- |\d+\. )/, ''))
        i++
      }
      out.push(
        <Box key={i} component={ordered ? 'ol' : 'ul'} sx={{ pl: 3, my: 1 }}>
          {items.map((it, k) => <Typography key={k} component="li" variant="body2" sx={{ mb: 0.75, lineHeight: 1.7 }}>{inline(it)}</Typography>)}
        </Box>,
      )
    } else {
      const para: string[] = []
      while (i < lines.length && lines[i].trim() && !/^(## |### |> |\||- |\d+\. )/.test(lines[i])) {
        para.push(lines[i])
        i++
      }
      out.push(<Typography key={i} variant="body2" sx={{ my: 1.25, lineHeight: 1.75, fontSize: 14 }}>{inline(para.join(' '))}</Typography>)
    }
  }
  return <>{out}</>
}

function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g).filter(Boolean).map((part, i) => {
    if (part.startsWith('**')) return <strong key={i}>{part.slice(2, -2)}</strong>
    if (part.startsWith('`')) return <Box key={i} component="code" className="mono" sx={{ px: 0.5, py: 0.1, borderRadius: 0.5, bgcolor: 'action.hover', fontSize: '0.85em' }}>{part.slice(1, -1)}</Box>
    if (part.startsWith('*')) return <em key={i}>{part.slice(1, -1)}</em>
    return part
  })
}
