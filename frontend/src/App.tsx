import { Box, CircularProgress } from '@mui/material'
import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/guards'
import { AppShell } from './layout/AppShell'
import { LoginPage } from './pages/LoginPage'
import { PortalShell } from './portal/PortalShell'

const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const BlotterPage = lazy(() => import('./pages/BlotterPage'))
const TradePage = lazy(() => import('./pages/TradePage'))
const ApprovalsPage = lazy(() => import('./pages/ApprovalsPage'))
const InboundPage = lazy(() => import('./pages/InboundPage'))
const IngestionPage = lazy(() => import('./pages/IngestionPage'))
const DocumentsPage = lazy(() => import('./pages/DocumentsPage'))
const TemplatesPage = lazy(() => import('./pages/TemplatesPage'))
const OutboxPage = lazy(() => import('./pages/OutboxPage'))
const CounterpartiesPage = lazy(() => import('./pages/CounterpartiesPage'))
const CounterpartyPage = lazy(() => import('./pages/CounterpartyPage'))
const ReportsPage = lazy(() => import('./pages/ReportsPage'))
const AuditPage = lazy(() => import('./pages/AuditPage'))
const KnowledgeBasePage = lazy(() => import('./kb/KnowledgeBasePage'))
const PortalHome = lazy(() => import('./portal/PortalHome'))
const PortalTradePage = lazy(() => import('./portal/PortalTradePage'))

const Loading = () => (
  <Box sx={{ display: 'grid', placeItems: 'center', py: 12 }}>
    <CircularProgress size={26} />
  </Box>
)

const OPS = ['MAKER', 'CHECKER', 'SUPERVISOR'] as const

export function App() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth roles={[...OPS]}><AppShell /></RequireAuth>}>
          <Route index element={<DashboardPage />} />
          <Route path="confirmations" element={<BlotterPage />} />
          <Route path="trades/:id" element={<TradePage />} />
          <Route path="approvals" element={<ApprovalsPage />} />
          <Route path="inbound" element={<InboundPage />} />
          <Route path="ingestion" element={<IngestionPage />} />
          <Route path="documents" element={<DocumentsPage />} />
          <Route path="templates" element={<TemplatesPage />} />
          <Route path="outbox" element={<OutboxPage />} />
          <Route path="counterparties" element={<CounterpartiesPage />} />
          <Route path="counterparties/:id" element={<CounterpartyPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="audit" element={<AuditPage />} />
          <Route path="kb" element={<KnowledgeBasePage />} />
          <Route path="kb/:slug" element={<KnowledgeBasePage />} />
        </Route>
        <Route path="/portal" element={<RequireAuth roles={['CLIENT']}><PortalShell /></RequireAuth>}>
          <Route index element={<PortalHome />} />
          <Route path="confirmations/:id" element={<PortalTradePage />} />
          <Route path="kb" element={<KnowledgeBasePage />} />
          <Route path="kb/:slug" element={<KnowledgeBasePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
