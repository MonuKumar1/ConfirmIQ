export type Role = 'MAKER' | 'CHECKER' | 'SUPERVISOR' | 'CLIENT'

export type ConfirmationStatus =
  | 'NEW' | 'ENRICHED' | 'DRAFTED' | 'PENDING_APPROVAL' | 'APPROVED' | 'AWAITING_RESPONSE'
  | 'MATCHED' | 'MISMATCHED' | 'DISPUTED' | 'CONFIRMED' | 'CANCELLED'

export type ProductType = 'FX_SPOT' | 'FX_FORWARD' | 'FX_SWAP' | 'FX_NDF'

export type Queue =
  | 'ALL' | 'ACTION_REQUIRED' | 'PENDING_APPROVAL' | 'AWAITING_CLIENT' | 'BREAKS' | 'SLA_BREACHED'
  | 'ESCALATED' | 'CONFIRMED' | 'CANCELLED'

export type ActionType =
  | 'ENRICH' | 'GENERATE' | 'SUBMIT' | 'APPROVE' | 'REJECT' | 'SEND' | 'CHASE' | 'CONFIRM'
  | 'FORCE_MATCH' | 'DISPUTE' | 'AFFIRM' | 'AMEND' | 'CANCEL'

export interface UserProfile {
  username: string
  fullName: string
  email: string
  role: Role
  counterpartyId?: number
  counterpartyName?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  page: number
  size: number
}

export interface BlotterRow {
  tradeId: number
  tradeRef: string
  externalRef: string
  productType: ProductType
  counterparty: string
  counterpartyCode: string
  counterpartyId: number
  tradeDate: string
  valueDate: string
  currencyPair: string
  buyCurrency: string
  buyAmount: number
  sellCurrency: string
  sellAmount: number
  rate: number
  status: ConfirmationStatus
  preparedBy?: string
  approvedBy?: string
  sentAt?: string
  slaDueAt?: string
  slaBreached: boolean
  chaserCount: number
  escalated: boolean
  economicVersion: number
  statusChangedAt: string
  ageDays: number
}

export interface TermField {
  key: string
  label: string
  counterpartyLabel: string
  value: string
  type: 'TEXT' | 'DATE' | 'AMOUNT' | 'RATE'
}

export interface SsiView {
  id: number
  currency: string
  beneficiaryName: string
  bankName: string
  bankBic: string
  accountNumber: string
  intermediaryBic?: string
  effectiveFrom: string
}

export type DocType = 'CONFIRMATION' | 'AMENDED_CONFIRMATION' | 'CANCELLATION' | 'CLIENT_RESPONSE' | 'REPORT'
export type ProcessingStatus = 'MATCHED' | 'MISMATCHED' | 'UNIDENTIFIED' | 'NOT_EXPECTED' | 'FAILED'
export type DocSource = 'GENERATED' | 'OPS_UPLOAD' | 'CLIENT_PORTAL' | 'INBOX' | 'SIMULATOR'

export interface DocumentView {
  id: number
  tradeId?: number
  tradeRef?: string
  counterparty?: string
  docType: DocType
  direction: 'OUTBOUND' | 'INBOUND'
  stage: 'DRAFT' | 'FINAL' | 'RECEIVED'
  version: number
  fileName: string
  sha256: string
  sizeBytes: number
  source: DocSource
  processingStatus?: ProcessingStatus
  processingNote?: string
  termsHash?: string
  createdBy: string
  createdAt: string
}

export interface FieldResult {
  key: string
  label: string
  expected: string
  actual?: string
  status: 'MATCH' | 'MISMATCH' | 'MISSING' | 'UNREADABLE'
  difference?: string
}

export interface MatchView {
  id: number
  documentId: number
  outcome: 'MATCHED' | 'MISMATCHED'
  identifiedBy: string
  qrVerified: boolean
  createdAt: string
  fields: FieldResult[]
}

export interface AuditView {
  id: number
  tradeId?: number
  entityType: string
  entityId?: number
  action: string
  fromStatus?: string
  toStatus?: string
  actor: string
  actorRole?: string
  details?: string
  comment?: string
  occurredAt: string
}

export type MessageType =
  | 'CONFIRMATION' | 'AMENDED_CONFIRMATION' | 'CANCELLATION' | 'CHASER' | 'ESCALATION' | 'PORTAL_NOTIFICATION'

export interface OutboxView {
  id: number
  tradeId?: number
  messageType: MessageType
  recipients: string
  cc?: string
  subject: string
  body: string
  documentId?: number
  status: string
  createdBy: string
  createdAt: string
}

export interface AllowedAction {
  action: ActionType
  label: string
  commentRequired: boolean
  enabled: boolean
  disabledReason?: string
}

export interface TradeDetail {
  id: number
  tradeRef: string
  externalRef: string
  sourceSystem: string
  ingestionChannel: string
  productType: ProductType
  productName: string
  tradeDate: string
  executionTime: string
  valueDate: string
  currencyPair: string
  buyCurrency: string
  buyAmount: number
  sellCurrency: string
  sellAmount: number
  rate: number
  farValueDate?: string
  farBuyAmount?: number
  farSellAmount?: number
  farRate?: number
  settlementCurrency?: string
  fixingDate?: string
  fixingSource?: string
  book?: string
  trader?: string
  economicVersion: number
  createdAt: string
  counterparty: {
    id: number
    shortCode: string
    legalName: string
    lei: string
    deliveryChannel: 'EMAIL' | 'PORTAL'
    confirmationSlaHours: number
    masterAgreement?: string
  }
  confirmation: {
    status: ConfirmationStatus
    statusDescription: string
    preparedBy?: string
    approvedBy?: string
    sentAt?: string
    slaDueAt?: string
    slaBreached: boolean
    chaserCount: number
    lastChasedAt?: string
    escalated: boolean
    disputeReason?: string
    matchedAt?: string
    confirmedAt?: string
    statusChangedAt: string
  }
  terms: TermField[]
  settlement: { currency: string; house?: SsiView; counterparty?: SsiView }[]
  documents: DocumentView[]
  matches: MatchView[]
  timeline: AuditView[]
  messages: OutboxView[]
  allowedActions: AllowedAction[]
  enrichmentIssues: string[]
}

export interface AmendRequest {
  valueDate?: string
  buyAmount?: number
  sellAmount?: number
  rate?: number
  farValueDate?: string
  farBuyAmount?: number
  farSellAmount?: number
  farRate?: number
  fixingDate?: string
  fixingSource?: string
}

export interface Dashboard {
  kpis: {
    totalTrades: number
    openConfirmations: number
    tradesToday: number
    awaitingClient: number
    breaks: number
    pendingApproval: number
    slaBreached: number
    confirmedToday: number
    firstTimeMatchRate: number
    avgHoursToConfirm: number
    slaCompliance: number
    enrichmentStpRate: number
    sentTotal: number
  }
  byStatus: Record<ConfirmationStatus, number>
  ageing: Record<string, number>
  trend: { date: string; booked: number; confirmed: number; breaks: number }[]
  byCounterparty: { counterparty: string; open: number; breaks: number; slaBreached: number }[]
  byProduct: Record<ProductType, number>
  attention: {
    tradeId: number
    tradeRef: string
    counterparty: string
    product: string
    status: ConfirmationStatus
    reason: string
    slaDueAt?: string
    chaserCount: number
  }[]
  asOf: string
}

export interface CounterpartySummary {
  id: number
  shortCode: string
  legalName: string
  lei: string
  country: string
  clientType: string
  deliveryChannel: 'EMAIL' | 'PORTAL'
  confirmationSlaHours: number
  active: boolean
  openConfirmations: number
  breaks: number
}

export interface ContactView {
  id: number
  name: string
  email: string
  phone?: string
  role: string
  primary: boolean
}

export interface CounterpartyDetail {
  id: number
  shortCode: string
  legalName: string
  lei: string
  bic?: string
  country: string
  clientType: string
  deliveryChannel: 'EMAIL' | 'PORTAL'
  confirmationSlaHours: number
  masterAgreement?: string
  active: boolean
  contacts: ContactView[]
  settlementInstructions: SsiView[]
}

export interface TemplateSummary {
  id: number
  code: string
  name: string
  productType: ProductType
  counterparty?: string
  activeVersion?: number
  versionCount: number
  lastChanged: string
}

export interface TemplateVersionView {
  id: number
  version: number
  title: string
  preamble: string
  clauses: string
  closing: string
  changeNote?: string
  active: boolean
  createdBy: string
  createdAt: string
}

export interface TemplateDetail {
  id: number
  code: string
  name: string
  productType: ProductType
  counterparty?: string
  versions: TemplateVersionView[]
}

export interface IngestionResult {
  externalRef: string
  outcome: 'ACCEPTED' | 'DUPLICATE' | 'REJECTED'
  tradeId?: number
  tradeRef?: string
  message: string
  errors: string[]
}

export interface FileIngestionResult {
  fileName: string
  error?: string
  trades: IngestionResult[]
  accepted: number
}

export interface InboundResult {
  documentId: number
  fileName: string
  status: ProcessingStatus
  tradeId?: number
  tradeRef?: string
  note: string
}

export interface InboundView {
  id: number
  fileName: string
  source: DocSource
  status?: ProcessingStatus
  note?: string
  tradeId?: number
  tradeRef?: string
  counterparty?: string
  sizeBytes: number
  sha256: string
  receivedBy: string
  receivedAt: string
}

export interface SearchHit {
  tradeId: number
  tradeRef: string
  externalRef: string
  counterparty: string
  product: string
  currencyPair: string
}

export interface PortalRow {
  tradeId: number
  tradeRef: string
  product: string
  tradeDate: string
  valueDate: string
  currencyPair: string
  youBuyCurrency: string
  youBuyAmount: number
  youSellCurrency: string
  youSellAmount: number
  rate: number
  status: 'ACTION_REQUIRED' | 'MATCHED' | 'UNDER_REVIEW' | 'CONFIRMED'
  sentAt?: string
  responseDueAt?: string
  overdue: boolean
}

export interface UserView {
  id: number
  username: string
  fullName: string
  email: string
  role: Role
  counterparty?: string
  active: boolean
  lastLoginAt?: string
}
