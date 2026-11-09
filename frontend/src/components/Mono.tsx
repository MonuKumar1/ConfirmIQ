import { Box, Tooltip } from '@mui/material'
import type { ReactNode } from 'react'

export function Mono({ children, title }: { children: ReactNode; title?: string }) {
  const node = <Box component="span" className="mono">{children}</Box>
  return title ? <Tooltip title={title}>{node}</Tooltip> : node
}

export function Hash({ value, len = 12 }: { value?: string; len?: number }) {
  if (!value) return <>—</>
  return <Mono title={value}>{value.slice(0, len)}…</Mono>
}
