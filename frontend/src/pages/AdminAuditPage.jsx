import toast from 'react-hot-toast'
import api from '../services/api'

const EVENT_TYPES = [
  '', 'DOCUMENT_UPLOADED', 'DOCUMENT_SENT', 'DOCUMENT_VIEWED',
  'DOCUMENT_SIGNED', 'DOCUMENT_COMPLETED', 'DOCUMENT_CANCELLED',
  'DOCUMENT_EXPIRED', 'DOCUMENT_DOWNLOADED',
  'USER_REGISTERED', 'USER_LOGIN', 'USER_LOGOUT',
  'SIGNING_LINK_ACCESSED', 'SIGNING_LINK_EXPIRED'
]

const DATE_FMT = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit', month: 'short', year: 'numeric',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
  hour12: false, timeZone: 'UTC'
})

export default function AdminAuditPage() {
  const [logs,        setLogs]        = useState(null)
  const [loading,     setLoading]     = useState(true)
  const [verifying,   setVerifying]   = useState(false)
  const [chainResult, setChainResult] = useState(null)
  const [page,        setPage]        = useState(0)

  // Filters
  const [eventType,  setEventType]  = useState('')
  const [actorEmail, setActorEmail] = useState('')
  const [docIdInput, setDocIdInput] = useState('')

  const fetch = useCallback(async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ page, size: 50 })
      if (eventType)  params.set('eventType',  eventType)
      if (actorEmail) params.set('actorEmail', actorEmail)
      const { data } = await api.get(`/audit/search?${params}`)
      setLogs(data)
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Failed to load audit logs')
    } finally {
      setLoading(false)
    }
  }, [page, eventType, actorEmail])

  useEffect(() => { fetch() }, [fetch])

  async function handleVerifyFull() {
    setVerifying(true)
    try {
      const { data } = await api.post('/audit/verify-full')
      setChainResult(data)
      toast[data.intact ? 'success' : 'error'](
        data.intact ? '✓ Full chain integrity verified' : '⚠ Chain integrity failure detected'
      )
    } catch { toast.error('Verification failed') }
    finally   { setVerifying(false) }
  }

  async function handleDocExport(e) {
    e.preventDefault()
    if (!docIdInput.trim()) { toast.error('Enter a document ID'); return }
    const t = toast.loading('Generating audit PDF…')
    try {
      const res = await api.get(`/audit/documents/${docIdInput.trim()}/export`, { responseType: 'blob' })
      const url  = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a    = document.createElement('a')
      a.href = url; a.download = `audit-${docIdInput.trim()}.pdf`; a.click()
      window.URL.revokeObjectURL(url)
      toast.dismiss(t); toast.success('Audit PDF downloaded')
    } catch {
      toast.dismiss(t); toast.error('Export failed — check document ID')
    }
  }

  return (
    <div className="p-8 max-w-7xl mx-auto">

      {/* ── Header ──────────────────────────────────────────── */}
      <div className="flex items-start justify-between mb-6 animate-fade-up">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--ink-50)]">Audit Admin</h1>
          <p className="text-sm text-[var(--ink-400)] mt-1">Platform-wide immutable audit log</p>
        </div>
        <div className="flex gap-2">
          <button onClick={fetch} disabled={loading}
                  className="h-9 w-9 rounded-lg border border-[var(--ink-700)] flex items-center justify-center
                             text-[var(--ink-400)] hover:text-[var(--ink-100)] hover:bg-[var(--ink-800)]
                             transition-all cursor-pointer disabled:opacity-40">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          </button>
          <button onClick={handleVerifyFull} disabled={verifying}
                  className="h-9 px-4 rounded-lg border border-[var(--ink-700)] flex items-center gap-2
                             text-sm text-[var(--ink-300)] hover:text-[var(--ink-100)]
                             hover:bg-[var(--ink-800)] transition-all cursor-pointer disabled:opacity-40">
            <ShieldCheck size={14} />
            {verifying ? 'Verifying…' : 'Verify Full Chain'}
          </button>
        </div>
      </div>

      {/* ── Chain result banner ──────────────────────────────── */}
      {chainResult && (
        <div className={clsx(
          'rounded-xl border px-5 py-4 mb-6 flex items-start gap-3 animate-fade-in',
          chainResult.intact
            ? 'bg-[#002A22] border-[#004A3A]'
            : 'bg-[#2A0E0E] border-[#4A1A1A]'
        )}>
          {chainResult.intact
            ? <ShieldCheck size={18} className="text-[var(--accent)] shrink-0 mt-0.5" />
            : <ShieldAlert  size={18} className="text-[var(--danger)] shrink-0 mt-0.5" />}
          <div>
            <p className={clsx('text-sm font-semibold',
              chainResult.intact ? 'text-[var(--accent)]' : 'text-[var(--danger)]')}>
              {chainResult.intact ? 'Platform Chain Integrity Verified' : '⚠ Integrity Failures Detected'}
            </p>
            <p className="text-xs text-[var(--ink-400)] mt-1">{chainResult.summary}</p>
            <p className="text-xs text-[var(--ink-500)] mt-1 font-[var(--font-mono)]">
              {chainResult.totalRows} rows verified · {chainResult.tamperedRows} failed
              · checked {new Date(chainResult.verifiedAt).toLocaleString()}
            </p>
          </div>
        </div>
      )}

      {/* ── Two-column: filters + export ────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_280px] gap-6 mb-6">

        {/* Filters */}
        <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-4 animate-fade-up delay-1">
          <div className="flex items-center gap-2 mb-3">
            <Filter size={13} className="text-[var(--ink-400)]" />
            <span className="text-xs font-medium text-[var(--ink-300)] uppercase tracking-widest font-[var(--font-mono)]">
              Filters
            </span>
          </div>
          <div className="flex gap-3 flex-wrap">
            <select
              value={eventType} onChange={e => { setEventType(e.target.value); setPage(0) }}
              className="h-9 px-3 text-sm rounded-lg bg-[var(--ink-850)] text-[var(--ink-100)]
                         border border-[var(--ink-700)] focus:border-[var(--accent)] outline-none
                         cursor-pointer min-w-[200px]"
            >
              {EVENT_TYPES.map(t => (
                <option key={t} value={t}>{t || 'All event types'}</option>
              ))}
            </select>
            <div className="relative flex-1 min-w-[200px]">
              <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--ink-500)]" />
              <input
                value={actorEmail}
                onChange={e => { setActorEmail(e.target.value); setPage(0) }}
                placeholder="Filter by actor email…"
                className="w-full h-9 pl-9 pr-3 text-sm rounded-lg bg-[var(--ink-850)] text-[var(--ink-100)]
                           border border-[var(--ink-700)] focus:border-[var(--accent)] outline-none
                           placeholder:text-[var(--ink-500)] transition-colors"
              />
            </div>
          </div>
        </div>

        {/* PDF Export by document ID */}
        <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-4 animate-fade-up delay-2">
          <h3 className="text-xs font-medium text-[var(--ink-400)] uppercase tracking-widest
                         font-[var(--font-mono)] mb-3">Export Audit PDF</h3>
          <form onSubmit={handleDocExport} className="flex flex-col gap-2">
            <input
              value={docIdInput}
              onChange={e => setDocIdInput(e.target.value)}
              placeholder="Document UUID"
              className="h-9 px-3 text-sm rounded-lg bg-[var(--ink-850)] text-[var(--ink-100)]
                         border border-[var(--ink-700)] focus:border-[var(--accent)] outline-none
                         placeholder:text-[var(--ink-500)] transition-colors font-[var(--font-mono)]"
            />
            <button type="submit"
                    className="h-9 rounded-lg bg-[var(--ink-800)] border border-[var(--ink-700)]
                               text-sm text-[var(--ink-300)] hover:text-[var(--ink-100)]
                               hover:border-[var(--ink-500)] flex items-center justify-center gap-2
                               transition-all cursor-pointer">
              <Download size={13} /> Generate PDF
            </button>
          </form>
        </div>
      </div>

      {/* ── Log table ───────────────────────────────────────── */}
      <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl overflow-hidden animate-fade-up delay-3">

        {/* Table header */}
        <div className="grid grid-cols-[180px_180px_1fr_120px_140px_80px] gap-3 px-5 py-3
                        border-b border-[var(--ink-800)] bg-[var(--ink-850)]">
          {['Timestamp', 'Event', 'Actor', 'IP', 'HMAC (12)', ''].map(h => (
            <span key={h} className="text-[10px] font-medium text-[var(--ink-500)]
                                     uppercase tracking-widest font-[var(--font-mono)]">{h}</span>
          ))}
        </div>

        {loading ? (
          <div className="flex flex-col gap-0">
            {Array.from({length: 8}).map((_, i) => (
              <div key={i} className="grid grid-cols-[180px_180px_1fr_120px_140px_80px] gap-3
                                      px-5 py-3.5 border-b border-[var(--ink-800)] last:border-0">
                {[180, 140, 200, 100, 130, 0].map((w, j) => (
                  w > 0 ? <div key={j} className="skeleton h-3.5 rounded" style={{ width: w }} /> : <div key={j} />
                ))}
              </div>
            ))}
          </div>
        ) : logs?.content?.length === 0 ? (
          <div className="py-16 flex flex-col items-center gap-2 text-[var(--ink-500)]">
            <ShieldCheck size={36} strokeWidth={1} />
            <p className="text-sm">No audit entries match your filters</p>
          </div>
        ) : (
          logs?.content?.map((entry, i) => (
            <div key={entry.id}
                 className={clsx(
                   'grid grid-cols-[180px_180px_1fr_120px_140px_80px] gap-3 px-5 py-3.5',
                   'border-b border-[var(--ink-800)] last:border-0 transition-colors',
                   i % 2 === 0 ? 'hover:bg-[var(--ink-850)]' : 'bg-[var(--ink-900)]/50 hover:bg-[var(--ink-850)]'
                 )}>
              <span className="text-xs text-[var(--ink-400)] font-[var(--font-mono)] truncate">
                {DATE_FMT.format(new Date(entry.createdAt))}
              </span>
              <span className="text-xs text-[var(--ink-200)] font-medium truncate">
                {entry.eventType?.replace(/_/g, ' ')}
              </span>
              <span className="text-xs text-[var(--ink-400)] truncate">
                {entry.actorEmail ?? '—'}
              </span>
              <span className="text-xs text-[var(--ink-500)] font-[var(--font-mono)] truncate">
                {entry.ipAddress ?? '—'}
              </span>
              <span className="text-xs text-[var(--ink-600)] font-[var(--font-mono)] truncate">
                {entry.hmacHashPrefix ?? '—'}
              </span>
              <div className="flex items-center">
                <CheckCircle2 size={12} className="text-[var(--accent)]" />
              </div>
            </div>
          ))
        )}
      </div>

      {/* ── Pagination ───────────────────────────────────────── */}
      {logs && logs.totalPages > 1 && (
        <div className="flex items-center justify-between mt-4 px-1">
          <p className="text-xs text-[var(--ink-500)] font-[var(--font-mono)]">
            {logs.totalElements} total entries · Page {page + 1} of {logs.totalPages}
          </p>
          <div className="flex gap-2">
            <PaginBtn onClick={() => setPage(p => p - 1)} disabled={page === 0}>
              <ChevronLeft size={14} />
            </PaginBtn>
            <PaginBtn onClick={() => setPage(p => p + 1)} disabled={logs.last}>
              <ChevronRight size={14} />
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