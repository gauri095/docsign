import { useState, useEffect, useCallback } from 'react'
import {
  Shield, ShieldCheck, ShieldAlert, Download, RefreshCw,
  ChevronLeft, ChevronRight, User, FileText, Eye,
  PenLine, CheckCircle2, XCircle, Link, LogIn, LogOut,
  Upload, Clock, AlertTriangle
} from 'lucide-react'
import { clsx } from 'clsx'
import toast from 'react-hot-toast'
import api from '../../services/api'

// ── Event type metadata ────────────────────────────────────────
const EVENT_META = {
  DOCUMENT_UPLOADED:    { label: 'Uploaded',         icon: Upload,       color: 'text-blue-400'   },
  DOCUMENT_SENT:        { label: 'Sent for Signing', icon: Link,         color: 'text-purple-400' },
  DOCUMENT_VIEWED:      { label: 'Viewed',           icon: Eye,          color: 'text-[var(--ink-300)]' },
  DOCUMENT_SIGNED:      { label: 'Signed',           icon: PenLine,      color: 'text-[var(--accent)]'  },
  DOCUMENT_COMPLETED:   { label: 'Completed',        icon: CheckCircle2, color: 'text-[var(--accent)]'  },
  DOCUMENT_CANCELLED:   { label: 'Cancelled',        icon: XCircle,      color: 'text-[var(--danger)]'  },
  DOCUMENT_EXPIRED:     { label: 'Expired',          icon: Clock,        color: 'text-[var(--warning)]' },
  DOCUMENT_DOWNLOADED:  { label: 'Downloaded',       icon: Download,     color: 'text-blue-400'   },
  USER_REGISTERED:      { label: 'User Registered',  icon: User,         color: 'text-[var(--ink-300)]' },
  USER_LOGIN:           { label: 'Login',            icon: LogIn,        color: 'text-[var(--ink-300)]' },
  USER_LOGOUT:          { label: 'Logout',           icon: LogOut,       color: 'text-[var(--ink-300)]' },
  SIGNING_LINK_ACCESSED:{ label: 'Link Accessed',    icon: Link,         color: 'text-purple-400' },
  SIGNING_LINK_EXPIRED: { label: 'Link Expired',     icon: Clock,        color: 'text-[var(--warning)]' },
}

const DATE_FMT = new Intl.DateTimeFormat('en-IN', {
  day:    '2-digit', month: 'short', year: 'numeric',
  hour:   '2-digit', minute: '2-digit', second: '2-digit',
  hour12: false,     timeZone: 'UTC', timeZoneName: 'short'
})

export function AuditTrailPanel({ documentId }) {
  const [data,       setData]       = useState(null)
  const [loading,    setLoading]    = useState(true)
  const [page,       setPage]       = useState(0)
  const [verifying,  setVerifying]  = useState(false)
  const [chainResult,setChainResult]= useState(null)
  const [exporting,  setExporting]  = useState(false)

  const fetch = useCallback(async () => {
    if (!documentId) return
    setLoading(true)
    try {
      const { data: result } = await api.get(`/audit/documents/${documentId}`, {
        params: { page, size: 20 }
      })
      setData(result)
    } catch {
      toast.error('Failed to load audit log')
    } finally {
      setLoading(false)
    }
  }, [documentId, page])

  useEffect(() => { fetch() }, [fetch])

  // ── Chain verification ─────────────────────────────────────
  async function handleVerify() {
    setVerifying(true)
    try {
      const { data: result } = await api.post(`/audit/documents/${documentId}/verify`)
      setChainResult(result)
      if (result.intact) toast.success('✓ Audit chain integrity verified')
      else               toast.error('⚠ Chain integrity FAILURE detected')
    } catch {
      toast.error('Verification failed')
    } finally {
      setVerifying(false)
    }
  }

  // ── PDF export ─────────────────────────────────────────────
  async function handleExport() {
    setExporting(true)
    try {
      const response = await api.get(`/audit/documents/${documentId}/export`, {
        responseType: 'blob'
      })
      const url  = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
      const link = document.createElement('a')
      link.href     = url
      link.download = `audit-trail-${documentId}.pdf`
      link.click()
      window.URL.revokeObjectURL(url)
      toast.success('Audit trail PDF downloaded')
    } catch {
      toast.error('Export failed')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">

      {/* ── Header row ──────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Shield size={16} className="text-[var(--ink-400)]" />
          <h3 className="text-sm font-semibold text-[var(--ink-100)]">Audit Trail</h3>
          {data && (
            <span className="text-xs text-[var(--ink-500)] font-[var(--font-mono)]">
              {data.totalElements} events
            </span>
          )}
        </div>

        <div className="flex gap-2">
          <button
            onClick={fetch}
            disabled={loading}
            className="h-8 w-8 rounded-lg border border-[var(--ink-700)] flex items-center justify-center
                       text-[var(--ink-400)] hover:text-[var(--ink-100)] hover:bg-[var(--ink-800)]
                       transition-all cursor-pointer disabled:opacity-40"
          >
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
          </button>

          <button
            onClick={handleVerify}
            disabled={verifying}
            className="h-8 px-3 rounded-lg border border-[var(--ink-700)] flex items-center gap-1.5
                       text-xs text-[var(--ink-300)] hover:text-[var(--ink-100)]
                       hover:bg-[var(--ink-800)] transition-all cursor-pointer disabled:opacity-40"
          >
            <ShieldCheck size={13} />
            {verifying ? 'Verifying…' : 'Verify Chain'}
          </button>

          <button
            onClick={handleExport}
            disabled={exporting}
            className="h-8 px-3 rounded-lg bg-[var(--ink-800)] border border-[var(--ink-700)]
                       flex items-center gap-1.5 text-xs text-[var(--ink-300)]
                       hover:text-[var(--ink-100)] hover:border-[var(--ink-500)]
                       transition-all cursor-pointer disabled:opacity-40"
          >
            <Download size={13} />
            {exporting ? 'Exporting…' : 'Export PDF'}
          </button>
        </div>
      </div>

      {/* ── Chain integrity banner ───────────────────────────── */}
      {chainResult && (
        <div className={clsx(
          'rounded-xl border px-4 py-3 flex items-start gap-3 animate-fade-in',
          chainResult.intact
            ? 'bg-[#002A22] border-[#004A3A]'
            : 'bg-[#2A0E0E] border-[#4A1A1A]'
        )}>
          {chainResult.intact
            ? <ShieldCheck size={16} className="text-[var(--accent)] shrink-0 mt-0.5" />
            : <ShieldAlert  size={16} className="text-[var(--danger)] shrink-0 mt-0.5" />}
          <div>
            <p className={clsx('text-xs font-semibold',
              chainResult.intact ? 'text-[var(--accent)]' : 'text-[var(--danger)]')}>
              {chainResult.intact ? 'Chain Integrity Verified' : 'Integrity Failure Detected'}
            </p>
            <p className="text-xs text-[var(--ink-400)] mt-0.5">{chainResult.summary}</p>
            {chainResult.tamperedRows > 0 && (
              <p className="text-xs text-[var(--danger)] mt-1 font-[var(--font-mono)]">
                {chainResult.tamperedRows} row(s) failed HMAC verification
              </p>
            )}
          </div>
        </div>
      )}

      {/* ── Event timeline ───────────────────────────────────── */}
      <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl overflow-hidden">

        {loading ? (
          <div className="flex flex-col gap-0">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-start gap-3 px-4 py-3 border-b border-[var(--ink-800)] last:border-0">
                <div className="skeleton w-8 h-8 rounded-full shrink-0" />
                <div className="flex-1 flex flex-col gap-1.5 pt-1">
                  <div className="skeleton h-3.5 w-40 rounded" />
                  <div className="skeleton h-3 w-64 rounded" />
                </div>
              </div>
            ))}
          </div>
        ) : !data?.content?.length ? (
          <div className="py-12 flex flex-col items-center gap-2 text-[var(--ink-500)]">
            <FileText size={32} strokeWidth={1} />
            <p className="text-sm">No audit events yet</p>
          </div>
        ) : (
          <div className="relative">
            {/* Timeline spine */}
            <div className="absolute left-[34px] top-4 bottom-4 w-px bg-[var(--ink-800)]" />

            {data.content.map((entry, i) => {
              const meta = EVENT_META[entry.eventType] ?? {
                label: entry.eventType, icon: FileText, color: 'text-[var(--ink-400)]'
              }
              const Icon = meta.icon

              return (
                <div
                  key={entry.id}
                  className="relative flex items-start gap-3 px-4 py-3 border-b border-[var(--ink-800)]
                             last:border-0 hover:bg-[var(--ink-850)] transition-colors group"
                >
                  {/* Icon bubble */}
                  <div className={clsx(
                    'shrink-0 w-8 h-8 rounded-full flex items-center justify-center',
                    'bg-[var(--ink-850)] border border-[var(--ink-700)] z-10',
                    meta.color
                  )}>
                    <Icon size={13} />
                  </div>

                  {/* Content */}
                  <div className="flex-1 min-w-0 pt-0.5">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={clsx('text-sm font-medium', meta.color)}>
                        {meta.label}
                      </span>
                      {entry.actorEmail && (
                        <span className="text-xs text-[var(--ink-400)]">
                          by <span className="text-[var(--ink-200)]">{entry.actorEmail}</span>
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-3 mt-0.5 flex-wrap">
                      <span className="text-xs text-[var(--ink-500)] font-[var(--font-mono)]">
                        {DATE_FMT.format(new Date(entry.createdAt))}
                      </span>
                      {entry.ipAddress && (
                        <span className="text-xs text-[var(--ink-600)] font-[var(--font-mono)]">
                          {entry.ipAddress}
                        </span>
                      )}
                    </div>

                    {/* HMAC prefix */}
                    <div className="flex items-center gap-1 mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <ShieldCheck size={10} className="text-[var(--ink-600)]" />
                      <span className="text-[10px] text-[var(--ink-600)] font-[var(--font-mono)]">
                        {entry.hmacHashPrefix}
                      </span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* ── Pagination ───────────────────────────────────────── */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between px-1">
          <p className="text-xs text-[var(--ink-500)] font-[var(--font-mono)]">
            Page {page + 1} of {data.totalPages}
          </p>
          <div className="flex gap-2">
            <PaginBtn onClick={() => setPage(p => p - 1)} disabled={page === 0}>
              <ChevronLeft size={13} />
            </PaginBtn>
            <PaginBtn onClick={() => setPage(p => p + 1)} disabled={data.last}>
              <ChevronRight size={13} />
            </PaginBtn>
          </div>
        </div>
      )}
    </div>
  )
}

function PaginBtn({ children, onClick, disabled }) {
  return (
    <button onClick={onClick} disabled={disabled}
            className="h-8 w-8 rounded-lg border border-[var(--ink-700)] flex items-center
                       justify-center text-[var(--ink-400)] hover:text-[var(--ink-100)]
                       hover:bg-[var(--ink-800)] disabled:opacity-30 transition-all cursor-pointer">
      {children}
    </button>
  )
}