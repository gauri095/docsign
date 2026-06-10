import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FileText, PenLine, CheckCircle2, Clock,
  Upload, ArrowRight, TrendingUp, AlertCircle,
  FilePlus, Loader2
} from 'lucide-react'
import { clsx } from 'clsx'
import toast from 'react-hot-toast'
import documentService from '../services/documentService'
import { UploadModal } from '../components/document/UploadModal'
import { useAuth } from '../hooks/useAuth'

const DATE_FMT = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit', month: 'short', year: 'numeric'
})

export default function DashboardPage() {
  const { user } = useAuth()
  const navigate  = useNavigate()

  const [stats,  setStats]  = useState(null)
  const [recent, setRecent] = useState([])
  const [loading, setLoading] = useState(true)
  const [showUpload, setShowUpload] = useState(false)

  useEffect(() => {
    async function load() {
      try {
        const [statsData, docsData] = await Promise.all([
          documentService.getStats(),
          documentService.list({ page: 0, size: 5 })
        ])
        setStats(statsData)
        setRecent(docsData.content ?? [])
      } catch { toast.error('Failed to load dashboard') }
      finally   { setLoading(false) }
    }
    load()
  }, [])

  const STAT_CARDS = stats ? [
    { label: 'Total Documents', value: stats.totalDocuments,    icon: FileText,      color: 'text-blue-400',          bg: '#0E2A4A' },
    { label: 'Awaiting Signatures', value: (stats.sent ?? 0) + (stats.partiallySigned ?? 0), icon: PenLine, color: 'text-purple-400', bg: '#1A0E2A' },
    { label: 'Completed',       value: stats.completed ?? 0,    icon: CheckCircle2,  color: 'text-[var(--accent)]',   bg: '#002A22' },
    { label: 'Expired',         value: stats.expired ?? 0,      icon: AlertCircle,   color: 'text-[var(--warning)]',  bg: '#2A1E00' },
  ] : []

  return (
    <div className="p-8 max-w-6xl mx-auto">

      {/* ── Header ──────────────────────────────────────────── */}
      <div className="flex items-start justify-between mb-8 animate-fade-up">
        <div>
          <h1 className="text-2xl font-semibold text-[var(--ink-50)]">
            Good {getGreeting()}, {user?.name?.split(' ')[0]}
          </h1>
          <p className="text-sm text-[var(--ink-400)] mt-1">
            {new Date().toLocaleDateString('en-IN', { weekday:'long', day:'2-digit', month:'long', year:'numeric' })}
          </p>
        </div>
        <button
          onClick={() => setShowUpload(true)}
          className="h-10 px-4 rounded-lg bg-[var(--accent)] text-[var(--ink-950)] font-semibold
                     text-sm flex items-center gap-2 hover:bg-[var(--accent-hover)]
                     shadow-[0_0_16px_var(--accent-dim)] transition-all cursor-pointer"
        >
          <Upload size={15} /> Upload Document
        </button>
      </div>

      {/* ── Stat cards ──────────────────────────────────────── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8 animate-fade-up delay-1">
        {loading
          ? Array.from({length:4}).map((_,i) => (
              <div key={i} className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-5">
                <div className="skeleton h-4 w-24 rounded mb-3" />
                <div className="skeleton h-8 w-16 rounded" />
              </div>
            ))
          : STAT_CARDS.map(({ label, value, icon: Icon, color, bg }) => (
              <div key={label}
                   className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-5
                              hover:border-[var(--ink-600)] transition-colors">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs text-[var(--ink-400)] font-medium">{label}</span>
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center"
                       style={{ background: bg }}>
                    <Icon size={15} className={color} />
                  </div>
                </div>
                <p className="text-3xl font-semibold text-[var(--ink-50)]">{value}</p>
              </div>
            ))
        }
      </div>

      {/* ── Two-column grid ──────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-6">

        {/* Recent documents */}
        <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl overflow-hidden animate-fade-up delay-2">
          <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--ink-800)]">
            <h2 className="text-sm font-semibold text-[var(--ink-100)]">Recent Documents</h2>
            <button onClick={() => navigate('/documents')}
                    className="text-xs text-[var(--accent)] hover:text-[var(--accent-hover)]
                               flex items-center gap-1 transition-colors cursor-pointer">
              View all <ArrowRight size={12} />
            </button>
          </div>

          {loading ? (
            <div className="p-4 flex flex-col gap-3">
              {Array.from({length:4}).map((_,i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className="skeleton w-9 h-9 rounded-lg" />
                  <div className="flex-1">
                    <div className="skeleton h-3.5 w-48 rounded mb-1.5" />
                    <div className="skeleton h-3 w-32 rounded" />
                  </div>
                </div>
              ))}
            </div>
          ) : recent.length === 0 ? (
            <div className="py-16 flex flex-col items-center gap-3 text-[var(--ink-500)]">
              <FilePlus size={36} strokeWidth={1} />
              <p className="text-sm">No documents yet</p>
              <button onClick={() => setShowUpload(true)}
                      className="text-xs text-[var(--accent)] hover:underline cursor-pointer">
                Upload your first document →
              </button>
            </div>
          ) : (
            recent.map(doc => (
              <button key={doc.id} onClick={() => navigate(`/documents/${doc.id}`)}
                      className="w-full flex items-center gap-3 px-5 py-3.5 text-left
                                 border-b border-[var(--ink-800)] last:border-0
                                 hover:bg-[var(--ink-850)] transition-colors group cursor-pointer">
                <div className="w-9 h-9 rounded-lg bg-[var(--ink-800)] border border-[var(--ink-700)]
                                flex items-center justify-center shrink-0">
                  <FileText size={15} className="text-[var(--accent)]" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-[var(--ink-100)] truncate
                                group-hover:text-[var(--accent)] transition-colors">
                    {doc.title}
                  </p>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className={clsx('badge text-[9px]', documentService.statusClass(doc.status))}>
                      {documentService.statusLabel(doc.status)}
                    </span>
                    <span className="text-[11px] text-[var(--ink-500)]">
                      {DATE_FMT.format(new Date(doc.createdAt))}
                    </span>
                  </div>
                </div>
                <ArrowRight size={13} className="text-[var(--ink-600)] group-hover:text-[var(--ink-400)]
                                                  transition-colors shrink-0" />
              </button>
            ))
          )}
        </div>

        {/* Quick actions + funnel */}
        <div className="flex flex-col gap-4">

          {/* Signing funnel */}
          {stats && (
            <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-5 animate-fade-up delay-3">
              <h3 className="text-sm font-semibold text-[var(--ink-100)] mb-4">Signing Funnel</h3>
              {[
                { label: 'Draft',            value: stats.draft ?? 0,             color: '#526880' },
                { label: 'Sent',             value: stats.sent ?? 0,              color: '#4DA6FF' },
                { label: 'Partial',          value: stats.partiallySigned ?? 0,   color: '#FFAA00' },
                { label: 'Completed',        value: stats.completed ?? 0,         color: '#00D4AA' },
              ].map(({ label, value, color }) => {
                const max = Math.max(stats.totalDocuments, 1)
                const pct = Math.round((value / max) * 100)
                return (
                  <div key={label} className="mb-3 last:mb-0">
                    <div className="flex justify-between text-xs mb-1">
                      <span className="text-[var(--ink-400)]">{label}</span>
                      <span className="text-[var(--ink-200)] font-[var(--font-mono)]">{value}</span>
                    </div>
                    <div className="h-1.5 bg-[var(--ink-800)] rounded-full overflow-hidden">
                      <div className="h-full rounded-full transition-all duration-700"
                           style={{ width: `${pct}%`, background: color }} />
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {/* Quick actions */}
          <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-5 animate-fade-up delay-4">
            <h3 className="text-sm font-semibold text-[var(--ink-100)] mb-3">Quick Actions</h3>
            <div className="flex flex-col gap-2">
              {[
                { label: 'Upload new document', icon: Upload,      action: () => setShowUpload(true) },
                { label: 'Browse all documents', icon: FileText,   action: () => navigate('/documents') },
                { label: 'Notifications',        icon: Clock,      action: () => navigate('/notifications') },
              ].map(({ label, icon: Icon, action }) => (
                <button key={label} onClick={action}
                        className="flex items-center gap-2.5 px-3 py-2.5 rounded-lg
                                   bg-[var(--ink-850)] border border-[var(--ink-700)]
                                   text-sm text-[var(--ink-300)] hover:text-[var(--ink-100)]
                                   hover:border-[var(--ink-600)] transition-all cursor-pointer text-left">
                  <Icon size={14} className="text-[var(--ink-400)]" />
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {showUpload && (
        <UploadModal
          onClose={() => setShowUpload(false)}
          onSuccess={(doc) => { navigate(`/documents/${doc.id}`) }}
        />
      )}
    </div>
  )
}

function getGreeting() {
  const h = new Date().getHours()
  if (h < 12) return 'morning'
  if (h < 17) return 'afternoon'
  return 'evening'
}