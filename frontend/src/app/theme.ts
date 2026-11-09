import { alpha, createTheme, type PaletteMode } from '@mui/material/styles'

declare module '@mui/material/styles' {
  interface Palette {
    brand: { navy: string; navyContrast: string; accent: string; sidebar: string; sidebarText: string }
  }
  interface PaletteOptions {
    brand?: { navy: string; navyContrast: string; accent: string; sidebar: string; sidebarText: string }
  }
}

export const MONO = '"JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace'

export function buildTheme(mode: PaletteMode) {
  const light = mode === 'light'
  return createTheme({
    palette: {
      mode,
      primary: { main: light ? '#1F5FA8' : '#6AA9EC', contrastText: '#fff' },
      secondary: { main: light ? '#102A43' : '#C9D6E8' },
      success: { main: light ? '#1E7F4F' : '#4CC38A' },
      warning: { main: light ? '#B76E00' : '#F0B44C' },
      error: { main: light ? '#B42318' : '#F97066' },
      info: { main: light ? '#2563B5' : '#7DB4F0' },
      background: { default: light ? '#F3F5F8' : '#0A111D', paper: light ? '#FFFFFF' : '#101A2A' },
      text: {
        primary: light ? '#0F1C2E' : '#E6ECF4',
        secondary: light ? '#5B6778' : '#93A1B5',
      },
      divider: light ? '#E3E8EF' : '#1F2B3D',
      brand: {
        navy: '#102A43',
        navyContrast: '#FFFFFF',
        accent: '#1F6FB2',
        sidebar: light ? '#0E2238' : '#08101C',
        sidebarText: '#B8C5D6',
      },
    },
    shape: { borderRadius: 6 },
    typography: {
      fontFamily: '"Inter Variable", Inter, system-ui, -apple-system, "Segoe UI", sans-serif',
      fontSize: 13,
      h4: { fontWeight: 650, fontSize: '1.45rem', letterSpacing: '-0.01em' },
      h5: { fontWeight: 650, fontSize: '1.2rem', letterSpacing: '-0.01em' },
      h6: { fontWeight: 600, fontSize: '1rem' },
      subtitle1: { fontWeight: 600, fontSize: '0.95rem' },
      subtitle2: { fontWeight: 600, fontSize: '0.82rem' },
      body2: { fontSize: '0.82rem' },
      caption: { fontSize: '0.74rem' },
      overline: { fontWeight: 600, fontSize: '0.68rem', letterSpacing: '0.08em', lineHeight: 1.6 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: { fontFeatureSettings: '"tnum" 1, "cv11" 1' },
          '.mono': { fontFamily: MONO, fontSize: '0.92em' },
          a: { color: light ? '#1F5FA8' : '#6AA9EC', textDecorationColor: 'transparent', '&:hover': { textDecorationColor: 'currentColor' } },
        },
      },
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: ({ theme }) => ({ backgroundImage: 'none', border: `1px solid ${theme.palette.divider}` }),
        },
      },
      MuiAppBar: { styleOverrides: { root: { border: 'none' } } },
      MuiDrawer: { styleOverrides: { paper: { border: 'none' } } },
      MuiButton: {
        defaultProps: { disableElevation: true, size: 'small' },
        styleOverrides: { root: { borderRadius: 6, paddingInline: 12 } },
      },
      MuiChip: { styleOverrides: { root: { fontWeight: 600, borderRadius: 4 }, sizeSmall: { height: 22, fontSize: '0.7rem' } } },
      MuiTextField: { defaultProps: { size: 'small' } },
      MuiSelect: { defaultProps: { size: 'small' } },
      MuiTab: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600, minHeight: 42 } } },
      MuiTabs: { styleOverrides: { root: { minHeight: 42 } } },
      MuiTooltip: { defaultProps: { arrow: true } },
      MuiTableCell: {
        styleOverrides: {
          root: ({ theme }) => ({ borderColor: theme.palette.divider, paddingTop: 7, paddingBottom: 7 }),
          head: ({ theme }) => ({
            fontWeight: 600, fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.04em',
            color: theme.palette.text.secondary, background: alpha(theme.palette.text.primary, 0.025),
          }),
        },
      },
      MuiDialogTitle: { styleOverrides: { root: { fontWeight: 650, fontSize: '1.05rem' } } },
    },
  })
}
