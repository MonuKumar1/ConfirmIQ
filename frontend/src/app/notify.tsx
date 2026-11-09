import { Alert, Snackbar } from '@mui/material'
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

type Severity = 'success' | 'error' | 'info' | 'warning'
const NotifyContext = createContext<(message: string, severity?: Severity) => void>(() => {})

export function NotifyProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ message: string; severity: Severity; key: number } | null>(null)
  const notify = useCallback((message: string, severity: Severity = 'success') => {
    setState({ message, severity, key: Date.now() })
  }, [])
  return (
    <NotifyContext.Provider value={notify}>
      {children}
      <Snackbar
        key={state?.key}
        open={!!state}
        autoHideDuration={state?.severity === 'error' ? 8000 : 4000}
        onClose={() => setState(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert variant="filled" severity={state?.severity ?? 'info'} onClose={() => setState(null)} sx={{ maxWidth: 520 }}>
          {state?.message}
        </Alert>
      </Snackbar>
    </NotifyContext.Provider>
  )
}

export const useNotify = () => useContext(NotifyContext)
