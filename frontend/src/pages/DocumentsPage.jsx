import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Upload, Search, FileText, File, MoreVertical,
  Shield, Trash2, Download, ChevronLeft, ChevronRight,
  RefreshCw, Filter
} from 'lucide-react'
import { clsx } from 'clsx'
import toast from 'react-hot-toast'
import documentService from '../services/documentService'
import { UploadModal } from '../components/document/UploadModal'

const STATUSES = ['All', 'DRAFT', 'SENT', 'PARTIALLY_SIGNED', 'COMPLETED', 'EXPIRED', 'CANCELLED']

export default function DocumentsPage() {
  const navigate = useNavigate()

  const [data,         setData]         = useState(null)
  const [loading,      setLoading]      = useState(true)
  const [activeStatus, setActiveStatus] = useState('All')
  const [search,       setSearch]       = useState('')
  const [searchInput,  setSearchInput]  = useState('')
  const [page,         setPage]         = useState(0)
  const [showUpload,   setShowUpload]   = useState(false)
  const [menuOpen,     setMenuOpen]     = useState(null)

  const fetch = useCallback(async () => {
    setLoading(true)
    try {
      const result = await documentService.list({
        status: activeStatus === 'All' ? undefined : activeStatus,
        search: search || undefined,
        page,
        size: 15,
      })
      setData(result)
    } catch {
      toast.error('Failed to load documents')
    } finally {
      setLoading(false)
    }
  }, [activeStatus, search, page])

  useEffect(() => { fetch() }, [fetch])

  // Debounce search
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(0) }, 400)
    return () => clearTimeout(t)
  }, [searchInput])

  const handleDelete = async (doc) => {
    if (!window.confirm(`Delete "${doc.title}"? This cannot be undone.`)) return
    try {
      await documentService.delete(doc.id)
      toast.success('Document deleted')
      fetch()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Delete failed')
    }
  }

  const handleVerify = async (doc) => {
    setMenuOpen(null)
    const t = toast.loading('Verifying integrity…')
    try {
      const result = await documentService.verifyIntegrity(doc.id)
      toast.dismiss(t)
      if (result.intact) toast.success('✓ Document integrity verified')
      else               toast.error('⚠ Integrity check FAILED — document may be tampered')
    } catch {
      toast.dismiss(t)
      toast.error('Verification failed')
    }
  }

  return (
    <div className="min-h-screen bg-(--ink-950) px-6 py-8 max-w-6xl mx-auto">

      {/* ── Header ──────────────────────────────────────────── */}
      <div className="flex items-start justify-between mb-8 animate-fade-up">
        <div>
          <h1 className="text-2xl font-semibold text-(--ink-50)">Documents</h1>
          <p className="text-sm text-(--ink-400) mt-1">
            {data ? `${data.totalElements} document${data.totalElements !== 1 ? 's' : ''}` : '…'}
          </p>
        </div>
        <div className="flex gap-3">
          <button onClick={fetch}
                  className="h-10 w-10 rounded-lg border border-(--ink-700) flex items-center
                             justify-center text-(--ink-400) hover:text-(--ink-100)
                             hover:bg-(--ink-800) transition-all cursor-pointer">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
          </button>
          <button onClick={() => setShowUpload(true)}
                  className="h-10 px-4 rounded-lg bg-(--accent) text-(--ink-950)
                             font-semibold text-sm flex items-center gap-2
                             hover:bg-(--accent-hover) shadow-[0_0_16px_var(--accent-dim)]
                             transition-all cursor-pointer">
            <Upload size={15} />
            Upload
          </button>
        </div>
      </div>

      {/* ── Filters bar ─────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6 animate-fade-up delay-1">
        {/* Search */}
        <div className="relative flex-1">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-(--ink-500)" />
          <input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by title…"
            className="w-full h-10 pl-9 pr-3 rounded-lg text-sm
                       bg-(--ink-900) text-(--ink-100)
                       border border-(--ink-700) focus:border-(--accent)
                       placeholder:text-(--ink-500) outline-none transition-colors"
          />
        </div>

        {/* Status tabs */}
        <div className="flex gap-1 bg-(--ink-900) border border-(--ink-700)
                        rounded-lg p-1 overflow-x-auto">
          {STATUSES.map((s) => (
            <button key={s}
                    onClick={() => { setActiveStatus(s); setPage(0) }}
                    className={clsx(
                      'px-3 py-1 rounded-md text-xs whitespace-nowrap transition-all cursor-pointer',
                      activeStatus === s
                        ? 'bg-(--ink-700) text-(--ink-50)'
                        : 'text-(--ink-400) hover:text-(--ink-200)'
                    )}>
              {s === 'PARTIALLY_SIGNED' ? 'Partial' : s === 'All' ? 'All' : documentService.statusLabel(s)}
            </button>
          ))}
        </div>
      </div>

      {/* ── Document table ───────────────────────────────────── */}
      <div className="bg-(--ink-900) border border-(--ink-700) rounded-xl overflow-hidden
                      animate-fade-up delay-2">

        {/* Table header */}
        <div className="grid grid-cols-[2fr_1fr_1fr_1fr_80px] gap-4 px-5 py-3
                        border-b border-(--ink-800) bg-(--ink-850)">
          {['Document', 'Status', 'Size', 'Uploaded', ''].map((h) => (
            <span key={h} className="text-xs text-(--ink-500)
                                     uppercase tracking-widest font-(--font-mono)">
              {h}
            </span>
          ))}
        </div>

        {/* Rows */}
        {loading ? (
          <div className="flex flex-col gap-0">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="grid grid-cols-[2fr_1fr_1fr_1fr_80px] gap-4 px-5 py-4
                                      border-b border-(--ink-800) last:border-0">
                <div className="skeleton h-4 w-48 rounded" />
                <div className="skeleton h-5 w-20 rounded-full" />
                <div className="skeleton h-4 w-16 rounded" />
                <div className="skeleton h-4 w-24 rounded" />
                <div />
              </div>
            ))}
          </div>
        ) : data?.content?.length === 0 ? (
          <div className="py-20 flex flex-col items-center gap-3 text-(--ink-500)">
            <FileText size={40} strokeWidth={1} />
            <p className="text-sm">No documents found</p>
            <button onClick={() => setShowUpload(true)}
                    className="text-xs text-(--accent) hover:underline cursor-pointer mt-1">
              Upload your first document →
            </button>
          </div>
        ) : (
          data?.content?.map((doc) => (
            <div key={doc.id}
                 className="grid grid-cols-[2fr_1fr_1fr_1fr_80px] gap-4 px-5 py-4
                            border-b border-(--ink-800) last:border-0
                            hover:bg-(--ink-850) transition-colors group">

              {/* Title + icon */}
              <button
                onClick={() => navigate(`/documents/${doc.id}`)}
                className="flex items-center gap-3 text-left cursor-pointer min-w-0">
                <div className="shrink-0 w-8 h-8 rounded-lg bg-(--ink-800)
                                border border-(--ink-700) flex items-center justify-center">
                  {doc.mimeType === 'application/pdf'
                    ? <FileText size={14} className="text-(--accent)" />
                    : <File    size={14} className="text-blue-400" />}
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-(--ink-100) truncate
                                group-hover:text-(--accent) transition-colors">
                    {doc.title}
                  </p>
                  <p className="text-xs text-(--ink-500) truncate font-(--font-mono)">
                    v{doc.currentVersion} · {doc.pageCount ? `${doc.pageCount}p` : doc.originalName}
                  </p>
                </div>
              </button>

              {/* Status badge */}
              <div className="flex items-center">
                <span className={clsx('badge', documentService.statusClass(doc.status))}>
                  {documentService.statusLabel(doc.status)}
                </span>
              </div>

              {/* Size */}
              <div className="flex items-center text-sm text-(--ink-400) font-(--font-mono)">
                {documentService.formatFileSize(doc.fileSizeBytes)}
              </div>

              {/* Date */}
              <div className="flex items-center text-sm text-(--ink-400)">
                {new Date(doc.createdAt).toLocaleDateString('en-IN', {
                  day: '2-digit', month: 'short', year: 'numeric'
                })}
              </div>

              {/* Actions menu */}
              <div className="flex items-center justify-end relative">
                <button
                  onClick={() => setMenuOpen(menuOpen === doc.id ? null : doc.id)}
                  className="p-1.5 rounded-lg text-(--ink-500) hover:text-(--ink-200)
                             hover:bg-(--ink-800) opacity-0 group-hover:opacity-100
                             transition-all cursor-pointer">
                  <MoreVertical size={14} />
                </button>

                {menuOpen === doc.id && (
                  <div className="absolute right-0 top-8 z-20 w-44
                                  bg-(--ink-900) border border-(--ink-700)
                                  rounded-xl shadow-2xl py-1 animate-fade-in">
                    <MenuItem icon={<Download size={13}/>}
                              label="Download"
                              onClick={() => { setMenuOpen(null); documentService.downloadBlob(doc.id, doc.originalName) }} />
                    <MenuItem icon={<Shield size={13}/>}
                              label="Verify integrity"
                              onClick={() => handleVerify(doc)} />
                    {doc.status === 'DRAFT' && (
                      <MenuItem icon={<Trash2 size={13}/>}
                                label="Delete"
                                danger
                                onClick={() => { setMenuOpen(null); handleDelete(doc) }} />
                    )}
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* ── Pagination ───────────────────────────────────────── */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between mt-4 px-1 animate-fade-up delay-3">
          <p className="text-xs text-(--ink-500) font-(--font-mono)">
            Page {page + 1} of {data.totalPages}
          </p>
          <div className="flex gap-2">
            <PaginBtn onClick={() => setPage(p => p - 1)} disabled={page === 0}>
              <ChevronLeft size={14} />
            </PaginBtn>
            <PaginBtn onClick={() => setPage(p => p + 1)} disabled={data.last}>
              <ChevronRight size={14} />
            </PaginBtn>
          </div>
        </div>
      )}

      {/* Click outside to close menu */}
      {menuOpen && (
        <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(null)} />
      )}

      {/* Upload modal */}
      {showUpload && (
        <UploadModal
          onClose={() => setShowUpload(false)}
          onSuccess={() => fetch()}
        />
      )}
    </div>
  )
}

function MenuItem({ icon, label, onClick, danger = false }) {
  return (
    <button onClick={onClick}
            className={clsx(
              'w-full flex items-center gap-2.5 px-3 py-2 text-xs transition-colors cursor-pointer',
              danger
                ? 'text-(--danger) hover:bg-[#FF4D4D11]'
                : 'text-(--ink-300) hover:bg-(--ink-800) hover:text-(--ink-100)'
            )}>
      {icon}{label}
    </button>
  )
}

function PaginBtn({ children, onClick, disabled }) {
  return (
    <button onClick={onClick} disabled={disabled}
            className="h-8 w-8 rounded-lg border border-(--ink-700) flex items-center
                       justify-center text-(--ink-400) hover:text-(--ink-100)
                       hover:bg-(--ink-800) disabled:opacity-30 transition-all cursor-pointer">
      {children}
    </button>
  )
}