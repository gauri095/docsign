import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, Download, Shield, Send, Users, Clock,
  FileText, CheckCircle2, XCircle, Eye, PenLine,
  ChevronDown, Loader2, Copy, ExternalLink, Trash2
} from 'lucide-react'
import { clsx } from 'clsx'
import toast from 'react-hot-toast'
import api from '../services/api'
import documentService from '../services/documentService'
import { PdfViewer } from '../components/document/PdfViewer'
import { AuditTrailPanel } from '../components/audit/AuditTrailPanel'
import { useAuth } from '../hooks/useAuth'

const DATE_FMT = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit', month: 'short', year: 'numeric',
  hour: '2-digit', minute: '2-digit', hour12: false
})

const TABS = ['overview', 'signers', 'audit']

export default function DocumentDetailPage() {
  const { id }     = useParams()
  const navigate   = useNavigate()
  const { user }   = useAuth()

  const [doc,      setDoc]      = useState(null)
  const [signers,  setSigners]  = useState([])
  const [pdfUrl,   setPdfUrl]   = useState(null)
  const [pageNum,  setPageNum]  = useState(1)
  const [tab,      setTab]      = useState('overview')
  const [loading,  setLoading]  = useState(true)
  const [sending,  setSending]  = useState(false)
  const [showSend, setShowSend] = useState(false)

  // Send-for-signing form state
  const [signerForms, setSignerForms] = useState([{ name: '', email: '', signingOrder: 1 }])

  useEffect(() => {
    async function load() {
      try {
        const [docData, signersData] = await Promise.all([
          api.get(`/documents/${id}`).then(r => r.data),
          api.get(`/documents/${id}/signing/signers`).then(r => r.data).catch(() => [])
        ])
        setDoc(docData)
        setSigners(signersData)

        // Get presigned download URL for PDF viewer
        const urlRes = await api.get(`/documents/${id}/download-url`)
        setPdfUrl(urlRes.data.url)
      } catch (err) {
        toast.error(err.response?.data?.message ?? 'Failed to load document')
        navigate('/documents')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [id])

  // ── Send for signing ─────────────────────────────────────
  async function handleSend(e) {
    e.preventDefault()
    const valid = signerForms.every(s => s.name.trim() && s.email.trim())
    if (!valid) { toast.error('All signers need a name and email'); return }

    setSending(true)
    try {
      await api.post(`/documents/${id}/signing/send`, {
        signers: signerForms.map(s => ({
          name: s.name.trim(), email: s.email.trim().toLowerCase(),
          signingOrder: parseInt(s.signingOrder) || 1
        }))
      })
      toast.success('Document sent for signing!')
      setShowSend(false)
      // Refresh
      const [docData, signersData] = await Promise.all([
        api.get(`/documents/${id}`).then(r => r.data),
        api.get(`/documents/${id}/signing/signers`).then(r => r.data)
      ])
      setDoc(docData)
      setSigners(signersData)
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Send failed')
    } finally {
      setSending(false)
    }
  }

  // ── Download ─────────────────────────────────────────────
  async function handleDownload() {
    const t = toast.loading('Preparing download…')
    try {
      await documentService.downloadBlob(id, doc.originalName)
      toast.dismiss(t)
    } catch {
      toast.dismiss(t)
      toast.error('Download failed')
    }
  }

  // ── Copy signing link ─────────────────────────────────────
  async function copySigningLink(signerId) {
    try {
      const { data } = await api.get(`/documents/${id}/signing/signers/${signerId}/link`)
      await navigator.clipboard.writeText(data.link)
      toast.success('Signing link copied')
    } catch { toast.error('Could not get signing link') }
  }

  if (loading) return <LoadingState />

  return (
    <div className="p-8 max-w-6xl mx-auto">

      {/* ── Back + header ───────────────────────────────────── */}
      <div className="flex items-start gap-4 mb-6 animate-fade-up">
        <button onClick={() => navigate('/documents')}
                className="mt-1 p-1.5 rounded-lg text-[var(--ink-400)] hover:text-[var(--ink-100)]
                           hover:bg-[var(--ink-800)] transition-all cursor-pointer">
          <ArrowLeft size={16} />
        </button>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-xl font-semibold text-[var(--ink-50)] truncate">{doc.title}</h1>
            <span className={clsx('badge', documentService.statusClass(doc.status))}>
              {documentService.statusLabel(doc.status)}
            </span>
          </div>
          <div className="flex items-center gap-4 mt-1.5 flex-wrap">
            <span className="text-xs text-[var(--ink-500)] font-[var(--font-mono)]">
              v{doc.currentVersion} · {doc.pageCount ? `${doc.pageCount} pages` : doc.originalName}
            </span>
            <span className="text-xs text-[var(--ink-500)]">
              {documentService.formatFileSize(doc.fileSizeBytes)}
            </span>
            <span className="text-xs text-[var(--ink-500)]">
              {DATE_FMT.format(new Date(doc.createdAt))}
            </span>
          </div>
        </div>

        {/* Action buttons */}
        <div className="flex gap-2 shrink-0">
          <button onClick={handleDownload}
                  className="h-9 px-3 rounded-lg border border-[var(--ink-700)] flex items-center gap-2
                             text-xs text-[var(--ink-300)] hover:text-[var(--ink-100)]
                             hover:bg-[var(--ink-800)] transition-all cursor-pointer">
            <Download size={13} /> Download
          </button>
          {doc.status === 'DRAFT' && (
            <button onClick={() => setShowSend(true)}
                    className="h-9 px-4 rounded-lg bg-[var(--accent)] text-[var(--ink-950)]
                               font-semibold text-xs flex items-center gap-2
                               hover:bg-[var(--accent-hover)] shadow-[0_0_12px_var(--accent-dim)]
                               transition-all cursor-pointer">
              <Send size={13} /> Send for Signing
            </button>
          )}
        </div>
      </div>

      {/* ── Tabs ───────────────────────────────────────────── */}
      <div className="flex gap-1 mb-6 bg-[var(--ink-900)] border border-[var(--ink-700)]
                      rounded-xl p-1 w-fit animate-fade-up delay-1">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
                  className={clsx(
                    'px-4 py-1.5 rounded-lg text-sm font-medium capitalize transition-all cursor-pointer',
                    tab === t
                      ? 'bg-[var(--ink-700)] text-[var(--ink-50)]'
                      : 'text-[var(--ink-400)] hover:text-[var(--ink-200)]'
                  )}>
            {t}
          </button>
        ))}
      </div>

      {/* ── Tab: Overview ──────────────────────────────────── */}
      {tab === 'overview' && (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-6 animate-fade-up delay-2">
          {/* PDF viewer */}
          <div>
            {pdfUrl ? (
              <PdfViewer
                pdfUrl={pdfUrl}
                currentPage={pageNum}
                onPageChange={setPageNum}
                onPageCount={() => {}}
              />
            ) : (
              <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl
                              aspect-[8.5/11] flex items-center justify-center text-[var(--ink-500)]">
                <div className="flex flex-col items-center gap-2">
                  <FileText size={40} strokeWidth={1} />
                  <p className="text-sm">Preview unavailable</p>
                </div>
              </div>
            )}
          </div>

          {/* Metadata sidebar */}
          <div className="flex flex-col gap-4">

            {/* Document info */}
            <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-4">
              <h3 className="text-xs font-medium text-[var(--ink-400)] uppercase tracking-widest
                             font-[var(--font-mono)] mb-3">Document Info</h3>
              <dl className="flex flex-col gap-2.5">
                {[
                  { label: 'File', value: doc.originalName },
                  { label: 'Type', value: doc.mimeType === 'application/pdf' ? 'PDF' : 'DOCX' },
                  { label: 'Size', value: documentService.formatFileSize(doc.fileSizeBytes) },
                  { label: 'Version', value: `v${doc.currentVersion}` },
                  { label: 'Pages', value: doc.pageCount ?? '—' },
                ].map(({ label, value }) => (
                  <div key={label} className="flex justify-between">
                    <dt className="text-xs text-[var(--ink-500)]">{label}</dt>
                    <dd className="text-xs text-[var(--ink-200)] font-[var(--font-mono)] text-right truncate max-w-[150px]">
                      {value}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>

            {/* SHA-256 hash */}
            <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-4">
              <h3 className="text-xs font-medium text-[var(--ink-400)] uppercase tracking-widest
                             font-[var(--font-mono)] mb-2">SHA-256 Fingerprint</h3>
              <div className="flex items-center gap-2">
                <p className="text-[10px] text-[var(--ink-500)] font-[var(--font-mono)] truncate flex-1">
                  {doc.sha256Hash}
                </p>
                <button onClick={() => { navigator.clipboard.writeText(doc.sha256Hash); toast.success('Copied') }}
                        className="shrink-0 text-[var(--ink-500)] hover:text-[var(--ink-200)] cursor-pointer">
                  <Copy size={11} />
                </button>
              </div>
            </div>

            {/* Tags */}
            {doc.tags?.length > 0 && (
              <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl p-4">
                <h3 className="text-xs font-medium text-[var(--ink-400)] uppercase tracking-widest
                               font-[var(--font-mono)] mb-2">Tags</h3>
                <div className="flex flex-wrap gap-1.5">
                  {doc.tags.map(tag => (
                    <span key={tag} className="text-[10px] px-2 py-0.5 rounded-full
                                               bg-[var(--ink-800)] border border-[var(--ink-700)]
                                               text-[var(--ink-400)] font-[var(--font-mono)]">
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Tab: Signers ───────────────────────────────────── */}
      {tab === 'signers' && (
        <div className="animate-fade-up delay-1">
          {signers.length === 0 ? (
            <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl
                            py-16 flex flex-col items-center gap-3 text-[var(--ink-500)]">
              <Users size={36} strokeWidth={1} />
              <p className="text-sm">No signers yet</p>
              {doc.status === 'DRAFT' && (
                <button onClick={() => setShowSend(true)}
                        className="text-xs text-[var(--accent)] hover:underline cursor-pointer mt-1">
                  Send for signing →
                </button>
              )}
            </div>
          ) : (
            <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl overflow-hidden">
              <div className="grid grid-cols-[2fr_1fr_1fr_1fr_80px] gap-4 px-5 py-3
                              border-b border-[var(--ink-800)] bg-[var(--ink-850)]">
                {['Signer', 'Status', 'Signed', 'Expires', ''].map(h => (
                  <span key={h} className="text-xs font-medium text-[var(--ink-500)]
                                            uppercase tracking-widest font-[var(--font-mono)]">{h}</span>
                ))}
              </div>
              {signers.map(signer => (
                <div key={signer.id}
                     className="grid grid-cols-[2fr_1fr_1fr_1fr_80px] gap-4 px-5 py-4
                                border-b border-[var(--ink-800)] last:border-0 hover:bg-[var(--ink-850)]
                                transition-colors group">
                  <div>
                    <p className="text-sm font-medium text-[var(--ink-100)]">{signer.name}</p>
                    <p className="text-xs text-[var(--ink-500)]">{signer.email}</p>
                  </div>
                  <div className="flex items-center">
                    <SignerStatusBadge status={signer.status} />
                  </div>
                  <div className="flex items-center text-xs text-[var(--ink-400)]">
                    {signer.signedAt ? DATE_FMT.format(new Date(signer.signedAt)) : '—'}
                  </div>
                  <div className="flex items-center text-xs text-[var(--ink-400)]">
                    {signer.tokenExpiresAt ? DATE_FMT.format(new Date(signer.tokenExpiresAt)) : '—'}
                  </div>
                  <div className="flex items-center justify-end">
                    {signer.status === 'PENDING' || signer.status === 'VIEWED' ? (
                      <button onClick={() => copySigningLink(signer.id)}
                              className="opacity-0 group-hover:opacity-100 p-1.5 rounded-lg
                                         text-[var(--ink-400)] hover:text-[var(--ink-100)]
                                         hover:bg-[var(--ink-800)] transition-all cursor-pointer">
                        <Copy size={13} />
                      </button>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── Tab: Audit ─────────────────────────────────────── */}
      {tab === 'audit' && (
        <div className="animate-fade-up delay-1">
          <AuditTrailPanel documentId={id} />
        </div>
      )}

      {/* ── Send for signing modal ──────────────────────────── */}
      {showSend && (
        <SendModal
          forms={signerForms}
          setForms={setSignerForms}
          onSubmit={handleSend}
          onClose={() => setShowSend(false)}
          sending={sending}
        />
      )}
    </div>
  )
}

// ── Sub-components ─────────────────────────────────────────────

function SignerStatusBadge({ status }) {
  const map = {
    PENDING:  { label: 'Pending',  color: 'text-[var(--ink-400)]', icon: Clock },
    VIEWED:   { label: 'Viewed',   color: 'text-blue-400',          icon: Eye   },
    SIGNED:   { label: 'Signed',   color: 'text-[var(--accent)]',   icon: CheckCircle2 },
    DECLINED: { label: 'Declined', color: 'text-[var(--danger)]',   icon: XCircle },
  }
  const { label, color, icon: Icon } = map[status] ?? map.PENDING
  return (
    <span className={clsx('flex items-center gap-1.5 text-xs font-medium', color)}>
      <Icon size={12} />{label}
    </span>
  )
}

function SendModal({ forms, setForms, onSubmit, onClose, sending }) {
  const addSigner = () => setForms(f => [...f, { name: '', email: '', signingOrder: f.length + 1 }])
  const removeSigner = (i) => setForms(f => f.filter((_, idx) => idx !== i))
  const update = (i, field, val) => setForms(f => f.map((s, idx) => idx === i ? { ...s, [field]: val } : s))

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4"
         onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="absolute inset-0 bg-[var(--ink-950)]/80 backdrop-blur-sm" />
      <div className="relative w-full max-w-lg bg-[var(--ink-900)] border border-[var(--ink-700)]
                      rounded-2xl shadow-2xl animate-fade-up">

        <div className="flex items-center justify-between p-5 border-b border-[var(--ink-800)]">
          <div>
            <h2 className="text-base font-semibold text-[var(--ink-50)]">Send for Signing</h2>
            <p className="text-xs text-[var(--ink-400)] mt-0.5">Add signers to invite</p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-[var(--ink-800)]
                                               text-[var(--ink-400)] cursor-pointer">
            <XCircle size={16} />
          </button>
        </div>

        <form onSubmit={onSubmit} className="p-5 flex flex-col gap-4">
          {forms.map((s, i) => (
            <div key={i} className="bg-[var(--ink-850)] border border-[var(--ink-700)] rounded-xl p-4">
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs font-medium text-[var(--ink-400)] font-[var(--font-mono)]">
                  Signer #{i + 1}
                </span>
                {forms.length > 1 && (
                  <button type="button" onClick={() => removeSigner(i)}
                          className="text-[var(--ink-500)] hover:text-[var(--danger)] transition-colors cursor-pointer">
                    <Trash2 size={13} />
                  </button>
                )}
              </div>
              <div className="flex flex-col gap-2">
                <input value={s.name} onChange={e => update(i, 'name', e.target.value)}
                       placeholder="Full name" required
                       className="h-9 px-3 text-sm rounded-lg bg-[var(--ink-900)] text-[var(--ink-100)]
                                  border border-[var(--ink-700)] focus:border-[var(--accent)] outline-none
                                  placeholder:text-[var(--ink-500)] transition-colors" />
                <input value={s.email} onChange={e => update(i, 'email', e.target.value)}
                       placeholder="Email address" type="email" required
                       className="h-9 px-3 text-sm rounded-lg bg-[var(--ink-900)] text-[var(--ink-100)]
                                  border border-[var(--ink-700)] focus:border-[var(--accent)] outline-none
                                  placeholder:text-[var(--ink-500)] transition-colors" />
              </div>
            </div>
          ))}

          <button type="button" onClick={addSigner}
                  className="h-9 rounded-lg border border-dashed border-[var(--ink-600)]
                             text-xs text-[var(--ink-400)] hover:border-[var(--ink-400)]
                             hover:text-[var(--ink-200)] transition-all cursor-pointer">
            + Add another signer
          </button>

          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose} disabled={sending}
                    className="flex-1 h-10 rounded-lg border border-[var(--ink-700)] text-sm
                               text-[var(--ink-300)] hover:bg-[var(--ink-800)] transition-all
                               disabled:opacity-40 cursor-pointer">
              Cancel
            </button>
            <button type="submit" disabled={sending}
                    className="flex-1 h-10 rounded-lg bg-[var(--accent)] text-[var(--ink-950)]
                               font-semibold text-sm flex items-center justify-center gap-2
                               hover:bg-[var(--accent-hover)] disabled:opacity-40 transition-all cursor-pointer">
              {sending ? <><Loader2 size={14} className="animate-spin" /> Sending…</> : <><Send size={14} /> Send</>}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function LoadingState() {
  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="flex items-center gap-4 mb-6">
        <div className="skeleton w-8 h-8 rounded-lg" />
        <div className="skeleton h-6 w-64 rounded" />
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-6">
        <div className="skeleton rounded-xl" style={{ aspectRatio: '8.5/11' }} />
        <div className="flex flex-col gap-4">
          <div className="skeleton h-48 rounded-xl" />
          <div className="skeleton h-24 rounded-xl" />
        </div>
      </div>
    </div>
  )
}

function XCircle({ size, className }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <circle cx="12" cy="12" r="10"/><path d="m15 9-6 6M9 9l6 6"/>
  </svg>
}