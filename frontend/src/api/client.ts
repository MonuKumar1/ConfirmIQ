import axios, { AxiosError } from 'axios'

export const TOKEN_KEY = 'ciq.token'

export const api = axios.create({ baseURL: '/api/v1' })

api.interceptors.request.use((config) => {
  const token = readToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      window.dispatchEvent(new Event('ciq:unauthorized'))
    }
    return Promise.reject(error)
  },
)

export function readToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

/** Extracts a human-readable message from an RFC 7807 problem response. */
export function errorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { detail?: string; errors?: Record<string, string> } | undefined
    if (data?.errors) {
      return `${data.detail ?? 'Validation failed'}: ${Object.entries(data.errors).map(([k, v]) => `${k} ${v}`).join('; ')}`
    }
    if (data?.detail) return data.detail
    if (!err.response) return 'Cannot reach the ConfirmIQ service. Is the backend running?'
    return `Request failed (${err.response.status})`
  }
  return err instanceof Error ? err.message : 'Unexpected error'
}

/** Fetches a binary resource with auth and returns an object URL (for previews and downloads). */
export async function fetchBlobUrl(url: string): Promise<{ url: string; fileName?: string }> {
  const res = await api.get(url, { responseType: 'blob' })
  const disposition = res.headers['content-disposition'] as string | undefined
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  return { url: URL.createObjectURL(res.data as Blob), fileName: match ? decodeURIComponent(match[1]) : undefined }
}

export async function downloadFile(url: string, fallbackName: string) {
  const { url: objectUrl, fileName } = await fetchBlobUrl(url)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = fileName ?? fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 10_000)
}
