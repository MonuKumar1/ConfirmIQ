import { Box, Typography } from '@mui/material'

export function BrandMark({ size = 30 }: { size?: number }) {
  return (
    <Box component="svg" viewBox="0 0 32 32" sx={{ width: size, height: size, flexShrink: 0 }} aria-hidden>
      <rect width="32" height="32" rx="6" fill="#1B3A5C" />
      <rect y="27" width="32" height="5" fill="#2F86D6" />
      <path d="M8 22V10h3l5 7 5-7h3v12h-3v-7l-5 7-5-7v7z" fill="#fff" />
    </Box>
  )
}

export function Brand({ subtitle = 'Meridian Capital' }: { subtitle?: string }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
      <BrandMark />
      <Box sx={{ lineHeight: 1 }}>
        <Typography sx={{ fontWeight: 700, fontSize: 15, letterSpacing: '-0.01em', color: '#fff', lineHeight: 1.2 }}>
          ConfirmIQ
        </Typography>
        <Typography sx={{ fontSize: 10.5, color: 'rgba(255,255,255,0.6)', letterSpacing: '0.04em', textTransform: 'uppercase' }}>
          {subtitle}
        </Typography>
      </Box>
    </Box>
  )
}
