import CloseIcon from '@mui/icons-material/Close'
import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import { Alert, Box, Button, CircularProgress, Dialog, DialogContent, DialogTitle, IconButton, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { downloadFile, errorMessage, fetchBlobUrl } from '../api/client'

/** Loads a PDF through the authenticated API and renders it in the browser's viewer. */
export function PdfFrame({ src, height = '100%' }: { src: string; height?: number | string }) {
  const [url, setUrl] = useState<string>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    let revoked: string | undefined
    let cancelled = false
    setUrl(undefined)
    setError(undefined)
    fetchBlobUrl(src)
      .then(({ url: u }) => {
        if (cancelled) URL.revokeObjectURL(u)
        else {
          revoked = u
          setUrl(u)
        }
      })
      .catch((e) => !cancelled && setError(errorMessage(e)))
    return () => {
      cancelled = true
      if (revoked) URL.revokeObjectURL(revoked)
    }
  }, [src])

  if (error) return <Alert severity="error">{error}</Alert>
  if (!url) {
    return (
      <Box sx={{ height, minHeight: 300, display: 'grid', placeItems: 'center' }}>
        <CircularProgress size={26} />
      </Box>
    )
  }
  return <Box component="iframe" title="Document preview" src={`${url}#view=FitH&toolbar=1`} sx={{ border: 0, width: '100%', height, minHeight: 300, bgcolor: '#525659', borderRadius: 1 }} />
}

export function PdfDialog({ open, onClose, src, title, subtitle, fileName }: {
  open: boolean
  onClose: () => void
  src: string
  title: string
  subtitle?: string
  fileName?: string
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth slotProps={{ paper: { sx: { height: '92vh' } } }}>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, pr: 1.5 }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 650 }} noWrap>{title}</Typography>
          {subtitle && <Typography variant="caption" color="text.secondary" className="mono" noWrap component="div">{subtitle}</Typography>}
        </Box>
        <Button startIcon={<DownloadOutlinedIcon />} onClick={() => downloadFile(`${src}${src.includes('?') ? '&' : '?'}download=true`, fileName ?? 'document.pdf')}>
          Download
        </Button>
        <IconButton
          size="small"
          aria-label="Open in new tab"
          onClick={async () => window.open((await fetchBlobUrl(src)).url, '_blank')}
        >
          <OpenInNewIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={onClose} aria-label="Close"><CloseIcon fontSize="small" /></IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ p: 0, display: 'flex' }}>
        {open && <PdfFrame src={src} />}
      </DialogContent>
    </Dialog>
  )
}
