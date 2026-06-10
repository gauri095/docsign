import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import {
  PenLine, Type, Upload, CheckCircle2, XCircle,
  Loader2, AlertTriangle, FileText, ChevronLeft, ChevronRight,
  RotateCcw, Download
} from 'lucide-react'
import { clsx } from 'clsx'
import toast from 'react-hot-toast'
import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

// ── Signature mode tabs ────────────────────────────────────────
const MODES = [
  { id: 'draw',   label: 'Draw',   icon: PenLine },
  { id: 'type',   label: 'Type',   icon: Type    },
  { id: 'upload', label: 'Upload', icon: Upload  },
]

// ── Signature fonts ────────────────────────────────────────────
const SIG_FONTS = [
  { name: 'Dancing Script',  css: "'Dancing Script', cursive" },
  { name: 'Great Vibes',     css: "'Great Vibes', cursive"    },
  { name: 'Pacifico',        css: "'Pacifico', cursive"       },
  { name: 'Sacramento',      css: "'Sacramento', cursive"     },
]

export default function SigningPage() {
  const { token } = useParams()

  // ── Page state ─────────────────────────────────────────────
  const [ctx,          setCtx]          = useState(null)   // PublicSigningContext
  const [loading,      setLoading]      = useState(true)
  const [error,        setError]        = useState(null)
  const [submitted,    setSubmitted]    = useState(false)
  const [completed,    setCompleted]    = useState(false)
  const [submitting,   setSubmitting]   = useState(false)

  // ── Active field ────────────────────────────────────────────
  const [activeField,  setActiveField]  = useState(null)

  // ── Signature capture ───────────────────────────────────────
  const [mode,         setMode]         = useState('draw')
  const [typedName,    setTypedName]    = useState('')
  const [selectedFont, setSelectedFont] = useState(0)
  const [uploadedImg,  setUploadedImg]  = useState(null)
  const [drawing,      setDrawing]      = useState(false)

  const canvasRef   = useRef(null)
  const uploadRef   = useRef(null)
  const hasDrawn    = useRef(false)

  // ── Load signing context ────────────────────────────────────
  useEffect(() => {
    async function load() {
      try {
        const { data } = await api.get(`/signing/public/${token}`)
        setCtx(data)
        // Auto-select first unset field
        if (data.fields?.length > 0) setActiveField(data.fields[0])
        // Pre-fill typed name
        setTypedName(data.signerName ?? '')
      } catch (err) {
        const msg = err.response?.data?.message ?? 'Invalid or expired signing link'
        setError(msg)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [token])

  // ── Canvas drawing ──────────────────────────────────────────
  const getCtx2d = () => {
    const canvas = canvasRef.current
    if (!canvas) return null
    const ctx2d = canvas.getContext('2d')
    ctx2d.strokeStyle = '#FFFFFF'
    ctx2d.lineWidth   = 2.5
    ctx2d.lineCap     = 'round'
    ctx2d.lineJoin    = 'round'
    return { canvas, ctx2d }
  }

  const startDraw = useCallback((e) => {
    const { canvas, ctx2d } = getCtx2d() ?? {}
    if (!canvas) return
    setDrawing(true)
    hasDrawn.current = true
    const { x, y } = getPos(e, canvas)
    ctx2d.beginPath()
    ctx2d.moveTo(x, y)
  }, [])

  const draw = useCallback((e) => {
    if (!drawing) return
    const { canvas, ctx2d } = getCtx2d() ?? {}
    if (!canvas) return
    const { x, y } = getPos(e, canvas)
    ctx2d.lineTo(x, y)
    ctx2d.stroke()
  }, [drawing])

  const stopDraw = useCallback(() => setDrawing(false), [])

  const clearCanvas = () => {
    const { canvas, ctx2d } = getCtx2d() ?? {}
    if (!canvas) return
    ctx2d.clearRect(0, 0, canvas.width, canvas.height)
    hasDrawn.current = false
  }

  function getPos(e, canvas) {
    const rect = canvas.getBoundingClientRect()
    const touch = e.touches?.[0] ?? e
    return {
      x: (touch.clientX - rect.left) * (canvas.width  / rect.width),
      y: (touch.clientY - rect.top)  * (canvas.height / rect.height),
    }
  }

  // ── Render typed signature to canvas ───────────────────────
  function renderTyped() {
    const { canvas, ctx2d } = getCtx2d() ?? {}
    if (!canvas || !typedName.trim()) return
    ctx2d.clearRect(0, 0, canvas.width, canvas.height)
    ctx2d.fillStyle    = '#FFFFFF'
    ctx2d.font         = `38px ${SIG_FONTS[selectedFont].css}`
    ctx2d.textBaseline = 'middle'
    ctx2d.textAlign    = 'center'
    ctx2d.fillText(typedName, canvas.width / 2, canvas.height / 2)
    hasDrawn.current = true
  }

  useEffect(() => {
    if (mode === 'type') renderTyped()
  }, [typedName, selectedFont, mode])

  // ── Export canvas to base64 PNG ─────────────────────────────
  function getSignatureBase64() {
    if (mode === 'upload') return uploadedImg
    const canvas = canvasRef.current
    if (!canvas) return null
    return canvas.toDataURL('image/png').split(',')[1]
  }

  // ── Submit ──────────────────────────────────────────────────
  async function handleSubmit() {
    const base64 = getSignatureBase64()
    if (!base64) {
      toast.error('Please provide a signature first')
      return
    }
    if (mode === 'draw' && !hasDrawn.current) {
      toast.error('Please draw your signature on the canvas')
      return
    }

    const field = activeField ?? ctx?.fields?.[0]

    setSubmitting(true)
    try {
      const payload = {
        signatureImageBase64: base64,
        signatureType: mode === 'draw' ? 'DRAWN' : mode === 'type' ? 'TYPED' : 'UPLOADED',
        fieldId:     field?.id    ?? null,
        pageNumber:  field?.pageNumber  ?? 1,
        xPosition:   field?.xPosition  ?? 0.1,
        yPosition:   field?.yPosition  ?? 0.1,
        width:       field?.width       ?? 0.25,
        height:      field?.height      ?? 0.08,
      }

      const { data } = await api.post(`/signing/public/${token}/sign`, payload)
      setSubmitted(true)
      setCompleted(data.documentCompleted)
      toast.success(data.documentCompleted ? '🎉 All done! Document is fully signed.' : 'Signature submitted!')
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Submission failed')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDecline() {
    if (!window.confirm('Are you sure you want to decline to sign this document?')) return
    try {
      await api.post(`/signing/public/${token}/decline`, { reason: 'Declined by signer' })
      setError('You have declined to sign this document.')
    } catch {
      toast.error('Could not record your decision. Please try again.')
    }
  }

  // ─────────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────────

  if (loading) return <LoadingScreen />
  if (error)   return <ErrorScreen message={error} />
  if (submitted) return <SuccessScreen completed={completed} signerName={ctx?.signerName} />

  return (
    <div className="min-h-screen bg-[var(--ink-950)] flex flex-col">

      {/* ── Header ──────────────────────────────────────────── */}
      <header className="border-b border-[var(--ink-800)] bg-[var(--ink-900)] px-6 py-4">
        <div className="max-w-5xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            {/* Logo */}
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
              <rect width="24" height="24" rx="6" fill="#00D4AA" opacity="0.12"/>
              <path d="M6 17L10 7L14 14L17 10L19 13" stroke="#00D4AA" strokeWidth="1.75"
                    strokeLinecap="round" strokeLinejoin="round"/>
              <circle cx="19" cy="13" r="1.25" fill="#00D4AA"/>
            </svg>
            <span className="font-semibold text-[var(--ink-50)]">
              Doc<span className="text-[var(--accent)]">Sign</span>
            </span>
          </div>
          <div className="text-right">
            <p className="text-sm font-medium text-[var(--ink-100)] truncate max-w-[240px]">
              {ctx?.documentTitle}
            </p>
            <p className="text-xs text-[var(--ink-400)]">
              Signing as <span className="text-[var(--ink-200)]">{ctx?.signerEmail}</span>
            </p>
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-5xl mx-auto w-full px-4 py-8 grid grid-cols-1 lg:grid-cols-[1fr_400px] gap-8">

        {/* ── Left: Document preview ───────────────────────── */}
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold text-[var(--ink-50)]">Document</h2>
            <span className="text-xs text-[var(--ink-400)] font-[var(--font-mono)]">
              {ctx?.totalPages} page{ctx?.totalPages !== 1 ? 's' : ''}
            </span>
          </div>

          {/* PDF iframe viewer */}
          <div className="bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl
                          overflow-hidden aspect-[8.5/11] relative">
            {ctx?.documentDownloadUrl ? (
              <iframe
                src={`https://docs.google.com/gview?url=${encodeURIComponent(ctx.documentDownloadUrl)}&embedded=true`}
                className="w-full h-full border-0"
                title="Document preview"
              />
            ) : (
              <div className="flex flex-col items-center justify-center h-full gap-3 text-[var(--ink-500)]">
                <FileText size={48} strokeWidth={1} />
                <p className="text-sm">Document preview unavailable</p>
              </div>
            )}

            {/* Signature field overlays */}
            {ctx?.fields?.map((field) => (
              <button
                key={field.id}
                onClick={() => setActiveField(field)}
                style={{
                  left:   `${field.xPosition * 100}%`,
                  top:    `${(1 - field.yPosition - field.height) * 100}%`,
                  width:  `${field.width  * 100}%`,
                  height: `${field.height * 100}%`,
                }}
                className={clsx(
                  'absolute border-2 border-dashed rounded transition-all cursor-pointer',
                  'flex items-center justify-center text-xs font-medium',
                  activeField?.id === field.id
                    ? 'border-[var(--accent)] bg-[var(--accent-dim)] text-[var(--accent)]'
                    : 'border-[var(--ink-500)] bg-[var(--ink-800)]/60 text-[var(--ink-400)]'
                )}
              >
                <PenLine size={12} className="mr-1" />
                Sign here
              </button>
            ))}
          </div>

          {/* Fields list */}
          {ctx?.fields?.length > 0 && (
            <div className="flex flex-col gap-1.5">
              <p className="text-xs text-[var(--ink-400)] font-[var(--font-mono)] uppercase tracking-widest">
                Signature Fields
              </p>
              {ctx.fields.map((f) => (
                <button
                  key={f.id}
                  onClick={() => setActiveField(f)}
                  className={clsx(
                    'flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-left transition-all cursor-pointer',
                    activeField?.id === f.id
                      ? 'bg-[var(--accent-dim)] border border-[var(--accent-border)] text-[var(--accent)]'
                      : 'bg-[var(--ink-900)] border border-[var(--ink-700)] text-[var(--ink-300)]'
                  )}
                >
                  <PenLine size={13} />
                  <span>{f.label ?? `Page ${f.pageNumber} — ${f.fieldType}`}</span>
                  {f.required && (
                    <span className="ml-auto text-[10px] text-[var(--danger)] font-[var(--font-mono)]">Required</span>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* ── Right: Signature panel ────────────────────────── */}
        <div className="flex flex-col gap-5">

          <div>
            <h2 className="text-base font-semibold text-[var(--ink-50)] mb-1">Your Signature</h2>
            <p className="text-xs text-[var(--ink-400)]">
              Choose how you'd like to sign this document
            </p>
          </div>

          {/* Mode tabs */}
          <div className="flex gap-1 bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-lg p-1">
            {MODES.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => { setMode(id); if (id !== 'type') clearCanvas() }}
                className={clsx(
                  'flex-1 flex items-center justify-center gap-1.5 py-2 rounded-md text-sm font-medium',
                  'transition-all cursor-pointer',
                  mode === id
                    ? 'bg-[var(--ink-700)] text-[var(--ink-50)]'
                    : 'text-[var(--ink-400)] hover:text-[var(--ink-200)]'
                )}
              >
                <Icon size={14} />{label}
              </button>
            ))}
          </div>

          {/* ── Draw mode ──────────────────────────────────── */}
          {mode === 'draw' && (
            <div className="flex flex-col gap-2">
              <canvas
                ref={canvasRef}
                width={560}
                height={180}
                onMouseDown={startDraw}
                onMouseMove={draw}
                onMouseUp={stopDraw}
                onMouseLeave={stopDraw}
                onTouchStart={(e) => { e.preventDefault(); startDraw(e) }}
                onTouchMove={(e)  => { e.preventDefault(); draw(e) }}
                onTouchEnd={stopDraw}
                className="w-full h-44 rounded-xl border border-[var(--ink-700)] bg-[var(--ink-900)]
                           cursor-crosshair touch-none"
                style={{ background: 'linear-gradient(135deg, #0D1117 0%, #111820 100%)' }}
              />
              <div className="flex justify-between items-center">
                <p className="text-xs text-[var(--ink-500)]">Draw inside the box above</p>
                <button onClick={clearCanvas}
                        className="flex items-center gap-1 text-xs text-[var(--ink-400)]
                                   hover:text-[var(--ink-200)] transition-colors cursor-pointer">
                  <RotateCcw size={11} /> Clear
                </button>
              </div>
            </div>
          )}

          {/* ── Type mode ──────────────────────────────────── */}
          {mode === 'type' && (
            <div className="flex flex-col gap-3">
              <input
                value={typedName}
                onChange={(e) => setTypedName(e.target.value)}
                placeholder="Type your full name"
                className="h-10 rounded-lg px-3 text-sm bg-[var(--ink-900)] text-[var(--ink-100)]
                           border border-[var(--ink-700)] focus:border-[var(--accent)] outline-none
                           placeholder:text-[var(--ink-500)] transition-colors"
              />
              {/* Font picker */}
              <div className="grid grid-cols-2 gap-2">
                {SIG_FONTS.map((font, i) => (
                  <button
                    key={font.name}
                    onClick={() => setSelectedFont(i)}
                    className={clsx(
                      'py-3 rounded-lg border text-xl transition-all cursor-pointer',
                      selectedFont === i
                        ? 'border-[var(--accent)] bg-[var(--accent-dim)]'
                        : 'border-[var(--ink-700)] bg-[var(--ink-900)] hover:border-[var(--ink-500)]'
                    )}
                    style={{ fontFamily: font.css }}
                  >
                    <span className="text-[var(--ink-100)]">{typedName || 'Your Name'}</span>
                  </button>
                ))}
              </div>
              {/* Hidden canvas for rendering */}
              <canvas ref={canvasRef} width={560} height={180} className="hidden" />
            </div>
          )}

          {/* ── Upload mode ─────────────────────────────────── */}
          {mode === 'upload' && (
            <div className="flex flex-col gap-2">
              <input
                ref={uploadRef}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (!file) return
                  const reader = new FileReader()
                  reader.onload = (ev) => {
                    const base64 = ev.target.result.split(',')[1]
                    setUploadedImg(base64)
                  }
                  reader.readAsDataURL(file)
                }}
              />
              <button
                onClick={() => uploadRef.current?.click()}
                className="h-40 rounded-xl border-2 border-dashed border-[var(--ink-700)]
                           bg-[var(--ink-900)] hover:border-[var(--ink-500)]
                           flex flex-col items-center justify-center gap-2
                           text-[var(--ink-400)] transition-all cursor-pointer"
              >
                {uploadedImg ? (
                  <img
                    src={`data:image/png;base64,${uploadedImg}`}
                    alt="Uploaded signature"
                    className="max-h-32 max-w-full object-contain"
                  />
                ) : (
                  <>
                    <Upload size={28} strokeWidth={1.5} />
                    <p className="text-sm">Click to upload signature image</p>
                    <p className="text-xs text-[var(--ink-500)]">PNG, JPG, WebP</p>
                  </>
                )}
              </button>
              {uploadedImg && (
                <button onClick={() => setUploadedImg(null)}
                        className="text-xs text-[var(--ink-400)] hover:text-[var(--danger)]
                                   transition-colors self-end cursor-pointer">
                  Remove
                </button>
              )}
              <canvas ref={canvasRef} width={560} height={180} className="hidden" />
            </div>
          )}

          {/* ── Legal notice ────────────────────────────────── */}
          <div className="bg-[var(--ink-850)] border border-[var(--ink-800)] rounded-lg p-3">
            <p className="text-xs text-[var(--ink-400)] leading-relaxed">
              By clicking <strong className="text-[var(--ink-200)]">Sign Document</strong>,
              you agree that your electronic signature is legally binding and equivalent
              to a handwritten signature. Your IP address, timestamp, and device information
              will be recorded in an immutable audit log.
            </p>
          </div>

          {/* ── Actions ─────────────────────────────────────── */}
          <div className="flex flex-col gap-2">
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="w-full h-12 rounded-lg bg-[var(--accent)] text-[var(--ink-950)]
                         font-semibold text-sm flex items-center justify-center gap-2
                         hover:bg-[var(--accent-hover)] shadow-[0_0_20px_var(--accent-dim)]
                         disabled:opacity-50 transition-all cursor-pointer"
            >
              {submitting
                ? <><Loader2 size={16} className="animate-spin" /> Submitting…</>
                : <><PenLine size={16} /> Sign Document</>}
            </button>

            <button
              onClick={handleDecline}
              disabled={submitting}
              className="w-full h-10 rounded-lg border border-[#FF4D4D33] text-[var(--danger)]
                         text-sm hover:bg-[#FF4D4D0A] transition-all disabled:opacity-40 cursor-pointer"
            >
              Decline to Sign
            </button>
          </div>
        </div>
      </main>

      {/* ── Footer ──────────────────────────────────────────── */}
      <footer className="border-t border-[var(--ink-800)] py-4 px-6">
        <p className="text-center text-xs text-[var(--ink-600)] font-[var(--font-mono)]">
          Secured by DocSign · AES-256 encrypted · SHA-256 verified
        </p>
      </footer>
    </div>
  )
}

// ── Supporting screens ─────────────────────────────────────────

function LoadingScreen() {
  return (
    <div className="min-h-screen bg-[var(--ink-950)] flex items-center justify-center">
      <div className="flex flex-col items-center gap-4 text-[var(--ink-400)]">
        <Loader2 size={36} className="animate-spin text-[var(--accent)]" />
        <p className="text-sm">Loading signing session…</p>
      </div>
    </div>
  )
}

function ErrorScreen({ message }) {
  return (
    <div className="min-h-screen bg-[var(--ink-950)] flex items-center justify-center p-6">
      <div className="max-w-md w-full bg-[var(--ink-900)] border border-[var(--ink-700)]
                      rounded-2xl p-8 text-center animate-fade-up">
        <AlertTriangle size={40} className="text-[var(--warning)] mx-auto mb-4" />
        <h1 className="text-lg font-semibold text-[var(--ink-50)] mb-2">Cannot Open Signing Page</h1>
        <p className="text-sm text-[var(--ink-400)]">{message}</p>
      </div>
    </div>
  )
}

function SuccessScreen({ completed, signerName }) {
  return (
    <div className="min-h-screen bg-[var(--ink-950)] flex items-center justify-center p-6">
      <div className="max-w-md w-full bg-[var(--ink-900)] border border-[var(--ink-700)]
                      rounded-2xl p-8 text-center animate-fade-up">
        <CheckCircle2 size={48} className="text-[var(--accent)] mx-auto mb-4" />
        <h1 className="text-xl font-semibold text-[var(--ink-50)] mb-2">
          {completed ? 'Document Fully Signed!' : 'Signature Submitted!'}
        </h1>
        <p className="text-sm text-[var(--ink-400)] leading-relaxed">
          {completed
            ? 'All parties have signed. The completed, sealed PDF has been generated and will be shared with everyone involved.'
            : `Thank you, ${signerName?.split(' ')[0] ?? 'there'}. Your signature has been recorded. Other signers are still pending.`}
        </p>
        {completed && (
          <div className="mt-6 p-3 bg-[var(--accent-dim)] border border-[var(--accent-border)] rounded-lg">
            <p className="text-xs text-[var(--accent)]">
              ✓ Cryptographically sealed · Audit trail generated · All parties notified
            </p>
          </div>
        )}
      </div>
    </div>
  )
}