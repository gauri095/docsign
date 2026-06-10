import { useState, useCallback, useRef } from 'react'
import {
  Upload, X, FileText, File, CheckCircle2,
  AlertCircle, Tag, Plus
} from 'lucide-react'
import { clsx } from 'clsx'
import toast from 'react-hot-toast'
import documentService from '../../services/documentService'

const ACCEPTED = {
  'application/pdf': ['.pdf'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
}
const MAX_MB = 50

export function UploadModal({ onClose, onSuccess }) {
  const [dragging, setDragging]   = useState(false)
  const [file, setFile]           = useState(null)
  const [title, setTitle]         = useState('')
  const [description, setDesc]    = useState('')
  const [tagInput, setTagInput]   = useState('')
  const [tags, setTags]           = useState([])
  const [progress, setProgress]   = useState(0)
  const [uploading, setUploading] = useState(false)
  const [done, setDone]           = useState(false)
  const inputRef = useRef()

  // ── File selection ─────────────────────────────────────────

  const acceptFile = useCallback((f) => {
    if (!f) return
    const ok = Object.keys(ACCEPTED).includes(f.type)
    if (!ok) { toast.error('Only PDF and DOCX files are accepted'); return }
    if (f.size > MAX_MB * 1024 * 1024) { toast.error(`File must be under ${MAX_MB} MB`); return }
    setFile(f)
    if (!title) setTitle(f.name.replace(/\.[^/.]+$/, ''))
  }, [title])

  const onDrop = useCallback((e) => {
    e.preventDefault(); setDragging(false)
    acceptFile(e.dataTransfer.files[0])
  }, [acceptFile])

  const onDragOver = (e) => { e.preventDefault(); setDragging(true) }
  const onDragLeave = () => setDragging(false)

  // ── Tags ───────────────────────────────────────────────────

  const addTag = () => {
    const t = tagInput.trim().toLowerCase().replace(/\s+/g, '-')
    if (t && !tags.includes(t) && tags.length < 8) {
      setTags([...tags, t])
      setTagInput('')
    }
  }

  // ── Submit ─────────────────────────────────────────────────

  async function handleSubmit(e) {
    e.preventDefault()
    if (!file)         { toast.error('Please select a file'); return }
    if (!title.trim()) { toast.error('Title is required');    return }

    setUploading(true)
    try {
      const doc = await documentService.upload(
        file,
        { title: title.trim(), description, tags },
        setProgress
      )
      setDone(true)
      toast.success('Document uploaded successfully')
      setTimeout(() => { onSuccess?.(doc); onClose() }, 1200)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Upload failed')
      setUploading(false)
      setProgress(0)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4"
         onClick={(e) => e.target === e.currentTarget && onClose()}>

      {/* Backdrop */}
      <div className="absolute inset-0 bg-(--ink-950)/80 backdrop-blur-sm" />

      {/* Modal */}
      <div className="relative w-full max-w-lg bg-(--ink-900) border border-(--ink-700)
                      rounded-2xl shadow-2xl animate-fade-up">

        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-(--ink-800)">
          <div>
            <h2 className="text-base font-semibold text-(--ink-50)">Upload Document</h2>
            <p className="text-xs text-(--ink-400) mt-0.5">PDF or DOCX · max {MAX_MB} MB</p>
          </div>
          <button onClick={onClose}
                  className="p-1.5 rounded-lg hover:bg-(--ink-800) text-(--ink-400)
                             hover:text-(--ink-100) transition-colors cursor-pointer">
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 flex flex-col gap-4">

          {/* Drop zone */}
          <div
            onClick={() => !file && inputRef.current?.click()}
            onDrop={onDrop}
            onDragOver={onDragOver}
            onDragLeave={onDragLeave}
            className={clsx(
              'rounded-xl border-2 border-dashed transition-all duration-200 cursor-pointer',
              'flex flex-col items-center justify-center text-center',
              file ? 'py-5' : 'py-8',
              dragging
                ? 'border-(--accent) bg-(--accent-dim)'
                : 'border-(--ink-700) hover:border-(--ink-500) bg-(--ink-850)'
            )}
          >
            <input ref={inputRef} type="file"
                   accept=".pdf,.docx" className="hidden"
                   onChange={(e) => acceptFile(e.target.files[0])} />

            {done ? (
              <CheckCircle2 size={32} className="text-(--accent) mb-2" />
            ) : file ? (
              <>
                <div className="flex items-center gap-3 mb-1">
                  {file.type === 'application/pdf'
                    ? <FileText size={22} className="text-(--accent)" />
                    : <File    size={22} className="text-blue-400" />}
                  <span className="text-sm font-medium text-(--ink-100) truncate max-w-55">
                    {file.name}
                  </span>
                </div>
                <span className="text-xs text-(--ink-400)">
                  {documentService.formatFileSize(file.size)}
                </span>
                {!uploading && (
                  <button type="button" onClick={(e) => { e.stopPropagation(); setFile(null); setProgress(0) }}
                          className="mt-2 text-xs text-(--ink-400) hover:text-(--danger) transition-colors">
                    Remove
                  </button>
                )}
              </>
            ) : (
              <>
                <Upload size={28} className="text-(--ink-500) mb-2" />
                <p className="text-sm text-(--ink-300)">
                  Drop file here or <span className="text-(--accent)">browse</span>
                </p>
                <p className="text-xs text-(--ink-500) mt-1">PDF, DOCX</p>
              </>
            )}
          </div>

          {/* Upload progress */}
          {uploading && (
            <div>
              <div className="flex justify-between text-xs text-(--ink-400) mb-1">
                <span>{done ? 'Complete' : 'Uploading…'}</span>
                <span>{progress}%</span>
              </div>
              <div className="h-1.5 bg-(--ink-800) rounded-full overflow-hidden">
                <div
                  className="h-full bg-(--accent) rounded-full transition-all duration-300"
                  style={{ width: `${progress}%` }}
                />
              </div>
            </div>
          )}

          {/* Title */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs text-(--ink-300) uppercase tracking-widest font-(--font-mono)">
              Title *
            </label>
            <input
              value={title} onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. NDA with Acme Corp"
              disabled={uploading}
              className="h-10 rounded-lg px-3 text-sm bg-(--ink-900) text-(--ink-100)
                         border border-(--ink-700) focus:border-(--accent) outline-none
                         placeholder:text-(--ink-500) transition-colors disabled:opacity-50"
            />
          </div>

          {/* Description */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs text-(--ink-300) uppercase tracking-widest font-(--font-mono)">
              Description
            </label>
            <textarea
              value={description} onChange={(e) => setDesc(e.target.value)}
              rows={2} placeholder="Optional context for signers…"
              disabled={uploading}
              className="rounded-lg px-3 py-2 text-sm bg-(--ink-900) text-(--ink-100)
                         border border-(--ink-700) focus:border-(--accent) outline-none
                         placeholder:text-(--ink-500) resize-none transition-colors
                         disabled:opacity-50"
            />
          </div>

          {/* Tags */}
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-(--font-mono) text-(--ink-300) uppercase tracking-widest">
              Tags
            </label>
            <div className="flex gap-2">
              <input
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addTag())}
                placeholder="Add a tag…"
                disabled={uploading}
                className="flex-1 h-9 rounded-lg px-3 text-sm bg-(--ink-900) text-(--ink-100)
                           border border-(--ink-700) focus:border-(--accent) outline-none
                           placeholder:text-(--ink-500) transition-colors disabled:opacity-50"
              />
              <button type="button" onClick={addTag} disabled={!tagInput.trim() || uploading}
                      className="h-9 w-9 flex items-center justify-center rounded-lg
                                 bg-(--ink-800) border border-(--ink-700)
                                 hover:border-(--ink-500) text-(--ink-300)
                                 disabled:opacity-40 transition-colors cursor-pointer">
                <Plus size={14} />
              </button>
            </div>
            {tags.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {tags.map((t) => (
                  <span key={t} className="flex items-center gap-1 px-2 py-0.5 rounded-full
                                           bg-(--ink-800) border border-(--ink-700)
                                           text-xs text-(--ink-300) font-(--font-mono)">
                    <Tag size={9} />
                    {t}
                    <button type="button" onClick={() => setTags(tags.filter((x) => x !== t))}
                            className="hover:text-(--danger) transition-colors ml-0.5 cursor-pointer">
                      <X size={9} />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Actions */}
          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose} disabled={uploading}
                    className="flex-1 h-10 rounded-lg text-sm font-medium
                               bg-(--ink-800) border border-(--ink-700)
                               text-(--ink-300) hover:text-(--ink-100)
                               hover:border-(--ink-500) transition-all
                               disabled:opacity-40 cursor-pointer">
              Cancel
            </button>
            <button type="submit" disabled={uploading || done || !file || !title.trim()}
                    className="flex-1 h-10 rounded-lg text-sm font-semibold
                               bg-(--accent) text-(--ink-950)
                               hover:bg-(--accent-hover)
                               shadow-[0_0_16px_var(--accent-dim)]
                               disabled:opacity-40 transition-all cursor-pointer
                               flex items-center justify-center gap-2">
              {uploading ? (done ? '✓ Done' : 'Uploading…') : 'Upload Document'}
            </button>
          </div>

        </form>
      </div>
    </div>
  )
}