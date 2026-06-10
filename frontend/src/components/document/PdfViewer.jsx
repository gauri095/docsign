import { useEffect, useRef, useState, useCallback } from 'react'
import * as pdfjsLib from 'pdfjs-dist'
import { ChevronLeft, ChevronRight, Loader2, AlertTriangle, ZoomIn, ZoomOut } from 'lucide-react'
import { clsx } from 'clsx'

// PDF.js v6 worker — use CDN to avoid bundling the large worker file
pdfjsLib.GlobalWorkerOptions.workerSrc =
  `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version}/pdf.worker.min.mjs`

/**
 * Renders a PDF from a URL using PDF.js v6.
 *
 * Props:
 *   pdfUrl        — presigned MinIO URL or any public PDF URL
 *   onPageCount   — called once with total page count
 *   currentPage   — controlled page number (1-based)
 *   onPageChange  — called when user navigates pages
 *   overlayFields — array of { xPosition, yPosition, width, height, pageNumber, id, label }
 *                   rendered as interactive overlays on the matching page
 *   onFieldClick  — called with field when an overlay is clicked
 */
export function PdfViewer({
  pdfUrl,
  onPageCount,
  currentPage = 1,
  onPageChange,
  overlayFields = [],
  onFieldClick,
  className = ''
}) {
  const canvasRef    = useRef(null)
  const containerRef = useRef(null)
  const pdfDocRef    = useRef(null)
  const renderTaskRef = useRef(null)

  const [totalPages, setTotalPages]   = useState(0)
  const [scale, setScale]             = useState(1.2)
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState(null)

  // ── Load PDF document ────────────────────────────────────
  useEffect(() => {
    if (!pdfUrl) return
    let cancelled = false

    setLoading(true)
    setError(null)

    pdfjsLib.getDocument({ url: pdfUrl, cMapPacked: true }).promise
      .then(pdf => {
        if (cancelled) return
        pdfDocRef.current = pdf
        setTotalPages(pdf.numPages)
        onPageCount?.(pdf.numPages)
      })
      .catch(err => {
        if (cancelled) return
        console.error('PDF load error:', err)
        setError('Failed to load PDF. The link may have expired.')
      })
      .finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }
  }, [pdfUrl])

  // ── Render current page ──────────────────────────────────
  const renderPage = useCallback(async () => {
    const pdf    = pdfDocRef.current
    const canvas = canvasRef.current
    if (!pdf || !canvas) return

    // Cancel any in-progress render
    if (renderTaskRef.current) {
      renderTaskRef.current.cancel()
    }

    try {
      const page     = await pdf.getPage(currentPage)
      const viewport = page.getViewport({ scale })
      const ctx      = canvas.getContext('2d')

      canvas.width  = viewport.width
      canvas.height = viewport.height

      const task = page.render({ canvasContext: ctx, viewport })
      renderTaskRef.current = task
      await task.promise
    } catch (err) {
      if (err?.name !== 'RenderingCancelledException') {
        console.error('PDF render error:', err)
      }
    }
  }, [currentPage, scale])

  useEffect(() => {
    if (!loading && !error && pdfDocRef.current) {
      renderPage()
    }
  }, [renderPage, loading, error])

  // ── Page navigation ──────────────────────────────────────
  const goTo = (n) => {
    if (n < 1 || n > totalPages) return
    onPageChange?.(n)
  }

  return (
    <div className={clsx('flex flex-col gap-3', className)}>

      {/* Toolbar */}
      <div className="flex items-center justify-between px-3 py-2
                      bg-[var(--ink-900)] border border-[var(--ink-700)] rounded-xl">
        <div className="flex items-center gap-1">
          <NavBtn onClick={() => goTo(currentPage - 1)} disabled={currentPage <= 1 || loading}>
            <ChevronLeft size={14} />
          </NavBtn>
          <span className="text-xs text-[var(--ink-400)] font-[var(--font-mono)] px-2 min-w-[70px] text-center">
            {loading ? '—' : `${currentPage} / ${totalPages}`}
          </span>
          <NavBtn onClick={() => goTo(currentPage + 1)} disabled={currentPage >= totalPages || loading}>
            <ChevronRight size={14} />
          </NavBtn>
        </div>

        <div className="flex items-center gap-1">
          <NavBtn onClick={() => setScale(s => Math.max(0.5, +(s - 0.2).toFixed(1)))}>
            <ZoomOut size={13} />
          </NavBtn>
          <span className="text-xs text-[var(--ink-500)] font-[var(--font-mono)] w-12 text-center">
            {Math.round(scale * 100)}%
          </span>
          <NavBtn onClick={() => setScale(s => Math.min(3.0, +(s + 0.2).toFixed(1)))}>
            <ZoomIn size={13} />
          </NavBtn>
        </div>
      </div>

      {/* Canvas container */}
      <div ref={containerRef}
           className="relative overflow-auto bg-[var(--ink-850)] border border-[var(--ink-700)]
                      rounded-xl flex items-start justify-center"
           style={{ maxHeight: '70vh' }}>

        {loading && (
          <div className="absolute inset-0 flex items-center justify-center z-10
                          bg-[var(--ink-900)]/80 rounded-xl">
            <div className="flex flex-col items-center gap-2 text-[var(--ink-400)]">
              <Loader2 size={28} className="animate-spin text-[var(--accent)]" />
              <p className="text-xs">Loading PDF…</p>
            </div>
          </div>
        )}

        {error && (
          <div className="flex flex-col items-center gap-2 py-16 text-[var(--ink-500)]">
            <AlertTriangle size={32} strokeWidth={1.5} className="text-[var(--warning)]" />
            <p className="text-sm text-[var(--ink-400)]">{error}</p>
          </div>
        )}

        {/* PDF canvas */}
        <div className="relative">
          <canvas ref={canvasRef} className="block shadow-2xl" />

          {/* Signature field overlays — shown only on matching page */}
          {overlayFields
            .filter(f => f.pageNumber === currentPage)
            .map(field => {
              const canvas = canvasRef.current
              if (!canvas) return null
              const cw = canvas.width / scale
              const ch = canvas.height / scale

              return (
                <button
                  key={field.id}
                  onClick={() => onFieldClick?.(field)}
                  style={{
                    left:   `${field.xPosition * 100}%`,
                    top:    `${(1 - parseFloat(field.yPosition) - parseFloat(field.height)) * 100}%`,
                    width:  `${field.width * 100}%`,
                    height: `${field.height * 100}%`,
                  }}
                  className="absolute border-2 border-dashed border-[var(--accent)]
                             bg-[var(--accent-dim)] rounded flex items-center justify-center
                             text-[10px] text-[var(--accent)] font-medium
                             hover:bg-[#00D4AA33] transition-all cursor-pointer"
                >
                  {field.label || 'Sign here'}
                </button>
              )
            })}
        </div>
      </div>
    </div>
  )
}

function NavBtn({ children, onClick, disabled }) {
  return (
    <button onClick={onClick} disabled={disabled}
            className="w-7 h-7 rounded-lg flex items-center justify-center
                       text-[var(--ink-400)] hover:text-[var(--ink-100)]
                       hover:bg-[var(--ink-800)] disabled:opacity-30
                       transition-all cursor-pointer">
      {children}
    </button>
  )
}