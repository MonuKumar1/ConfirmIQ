import { CssBaseline, ThemeProvider, type PaletteMode } from '@mui/material'
import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { buildTheme } from './theme'

const ThemeModeContext = createContext<{ mode: PaletteMode; toggle: () => void }>({ mode: 'light', toggle: () => {} })

function initialMode(): PaletteMode {
  try {
    const saved = localStorage.getItem('ciq.theme')
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    /* storage unavailable */
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<PaletteMode>(initialMode)
  const theme = useMemo(() => buildTheme(mode), [mode])
  const value = useMemo(
    () => ({
      mode,
      toggle: () =>
        setMode((m) => {
          const next = m === 'light' ? 'dark' : 'light'
          try {
            localStorage.setItem('ciq.theme', next)
          } catch {
            /* ignore */
          }
          return next
        }),
    }),
    [mode],
  )
  return (
    <ThemeModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  )
}

export const useThemeMode = () => useContext(ThemeModeContext)
