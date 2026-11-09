import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  ActionType, AmendRequest, AuditView, BlotterRow, CounterpartyDetail, CounterpartySummary, Dashboard,
  DocumentView, InboundView, OutboxView, Page, PortalRow, Queue, SsiView, TemplateDetail, TemplateSummary,
  TradeDetail, UserView,
} from './types'

const get = <T,>(url: string, params?: object) => api.get<T>(url, { params }).then((r) => r.data)

export interface BlotterParams {
  queue: Queue
  status?: string
  q?: string
  productType?: string
  counterpartyId?: number
  page: number
  size: number
  sort?: string
}

export const useBlotter = (p: BlotterParams) =>
  useQuery({
    queryKey: ['blotter', p],
    queryFn: () => get<Page<BlotterRow>>('/confirmations', p),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  })

export const useQueueCounts = () =>
  useQuery({ queryKey: ['queues'], queryFn: () => get<Record<Queue, number>>('/confirmations/queues'), refetchInterval: 30_000 })

export const useTrade = (id?: number) =>
  useQuery({ queryKey: ['trade', id], queryFn: () => get<TradeDetail>(`/trades/${id}`), enabled: !!id })

export const useTradeAction = (id: number) => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { action: ActionType; comment?: string; amendment?: AmendRequest }) =>
      api.post<TradeDetail>(`/trades/${id}/actions`, body).then((r) => r.data),
    onSuccess: (data) => {
      qc.setQueryData(['trade', id], data)
      void qc.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'trade' })
    },
  })
}

export const useDashboard = (days: number) =>
  useQuery({ queryKey: ['dashboard', days], queryFn: () => get<Dashboard>('/analytics/dashboard', { days }), refetchInterval: 60_000 })

export const useCounterparties = () =>
  useQuery({ queryKey: ['counterparties'], queryFn: () => get<CounterpartySummary[]>('/counterparties') })

export const useCounterparty = (id?: number) =>
  useQuery({ queryKey: ['counterparty', id], queryFn: () => get<CounterpartyDetail>(`/counterparties/${id}`), enabled: !!id })

export const useHouseSsis = () =>
  useQuery({ queryKey: ['house-ssis'], queryFn: () => get<SsiView[]>('/counterparties/house-ssis') })

export const useTemplates = () => useQuery({ queryKey: ['templates'], queryFn: () => get<TemplateSummary[]>('/templates') })

export const useTemplate = (id?: number) =>
  useQuery({ queryKey: ['template', id], queryFn: () => get<TemplateDetail>(`/templates/${id}`), enabled: !!id })

export const useDocuments = (p: { q?: string; docType?: string; direction?: string; page: number; size: number }) =>
  useQuery({ queryKey: ['documents', p], queryFn: () => get<Page<DocumentView>>('/documents', p), placeholderData: keepPreviousData })

export const useInbound = () =>
  useQuery({ queryKey: ['inbound'], queryFn: () => get<InboundView[]>('/inbound'), refetchInterval: 30_000 })

export const useOutbox = (p: { type?: string; q?: string; page: number; size: number }) =>
  useQuery({ queryKey: ['outbox', p], queryFn: () => get<Page<OutboxView>>('/outbox', p), placeholderData: keepPreviousData })

export const useAudit = (p: { actor?: string; action?: string; entityType?: string; tradeId?: number; page: number; size: number }) =>
  useQuery({ queryKey: ['audit', p], queryFn: () => get<Page<AuditView>>('/audit', p), placeholderData: keepPreviousData })

export const useUsers = () => useQuery({ queryKey: ['users'], queryFn: () => get<UserView[]>('/users') })

export const usePortalConfirmations = () =>
  useQuery({ queryKey: ['portal'], queryFn: () => get<PortalRow[]>('/portal/confirmations'), refetchInterval: 30_000 })

export const usePortalTrade = (id?: number) =>
  useQuery({ queryKey: ['portal-trade', id], queryFn: () => get<TradeDetail>(`/portal/confirmations/${id}`), enabled: !!id })
