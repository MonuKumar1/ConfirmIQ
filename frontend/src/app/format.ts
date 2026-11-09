import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import utc from 'dayjs/plugin/utc'

dayjs.extend(relativeTime)
dayjs.extend(utc)

const ZERO_DECIMALS = new Set(['JPY', 'KRW'])

export function fmtAmount(amount: number | undefined | null, ccy?: string): string {
  if (amount == null) return '—'
  const digits = ccy && ZERO_DECIMALS.has(ccy) ? 0 : 2
  return amount.toLocaleString('en-GB', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

export function fmtCompact(amount: number, ccy?: string): string {
  const abs = Math.abs(amount)
  const s = abs >= 1e9 ? `${(amount / 1e9).toFixed(2)}bn` : abs >= 1e6 ? `${(amount / 1e6).toFixed(2)}m` : fmtAmount(amount, ccy)
  return ccy ? `${ccy} ${s}` : s
}

export function fmtRate(rate: number | undefined | null): string {
  if (rate == null) return '—'
  const s = String(rate)
  const decimals = s.includes('.') ? s.split('.')[1].length : 0
  return rate.toFixed(Math.max(decimals, 4))
}

export const fmtDate = (d?: string | null) => (d ? dayjs(d).format('DD MMM YYYY') : '—')
export const fmtDateTime = (d?: string | null) => (d ? dayjs(d).format('DD MMM YYYY HH:mm') : '—')
export const fmtTime = (d?: string | null) => (d ? dayjs(d).format('HH:mm') : '—')
export const fromNow = (d?: string | null) => (d ? dayjs(d).fromNow() : '—')

export function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

export const humanize = (s?: string | null) =>
  s ? s.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase()) : '—'

export const PRODUCT_LABEL: Record<string, string> = {
  FX_SPOT: 'FX Spot',
  FX_FORWARD: 'FX Forward',
  FX_SWAP: 'FX Swap',
  FX_NDF: 'NDF',
}

export { dayjs }
