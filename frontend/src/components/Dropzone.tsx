import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined'
import { Box, Typography } from '@mui/material'
import { useRef, useState } from 'react'

export function Dropzone({ accept, multiple = true, onFiles, title, hint, disabled }: {
  accept: string
  multiple?: boolean
  onFiles: (files: File[]) => void
  title: string
  hint?: string
  disabled?: boolean
}) {
  const [over, setOver] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const extensions = accept.split(',').map((a) => a.trim().toLowerCase())
  const accepted = (f: File) => extensions.some((ext) => (ext.startsWith('.') ? f.name.toLowerCase().endsWith(ext) : f.type === ext))

  return (
    <Box
      role="button"
      tabIndex={0}
      onClick={() => !disabled && input.current?.click()}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
      onDragOver={(e) => { e.preventDefault(); setOver(true) }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault()
        setOver(false)
        const files = Array.from(e.dataTransfer.files).filter(accepted)
        if (files.length && !disabled) onFiles(files)
      }}
      sx={{
        border: 2, borderStyle: 'dashed', borderRadius: 2, p: 3.5, textAlign: 'center', cursor: disabled ? 'default' : 'pointer',
        borderColor: over ? 'primary.main' : 'divider', bgcolor: over ? 'action.hover' : 'transparent', transition: 'all .15s',
        opacity: disabled ? 0.6 : 1, '&:hover': { borderColor: disabled ? 'divider' : 'primary.main' },
      }}
    >
      <CloudUploadOutlinedIcon sx={{ fontSize: 36, color: 'primary.main', opacity: 0.8 }} />
      <Typography variant="subtitle2" sx={{ mt: 1 }}>{title}</Typography>
      {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
      <input
        ref={input}
        type="file"
        hidden
        accept={accept}
        multiple={multiple}
        onChange={(e) => {
          const files = Array.from(e.target.files ?? [])
          if (files.length) onFiles(files)
          e.target.value = ''
        }}
      />
    </Box>
  )
}
