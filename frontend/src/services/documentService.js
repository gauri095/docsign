import api from './api'

const documentService = {

  // ── Upload ─────────────────────────────────────────────────

  async upload(file, { title, description = '', tags = [] }, onProgress) {
    const form = new FormData()
    form.append('file', file)
    form.append('title', title)
    if (description) form.append('description', description)
    tags.forEach((t) => form.append('tags', t))

    const { data } = await api.post('/documents/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (evt) => {
        if (onProgress && evt.total) {
          onProgress(Math.round((evt.loaded * 100) / evt.total))
        }
      },
    })
    return data
  },

  async uploadVersion(documentId, file, changeNote, onProgress) {
    const form = new FormData()
    form.append('file', file)
    if (changeNote) form.append('changeNote', changeNote)

    const { data } = await api.post(`/documents/${documentId}/versions`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (evt) => {
        if (onProgress && evt.total) {
          onProgress(Math.round((evt.loaded * 100) / evt.total))
        }
      },
    })
    return data
  },

  // ── Read ───────────────────────────────────────────────────

  async list({ status, search, page = 0, size = 20 } = {}) {
    const params = new URLSearchParams()
    if (status) params.set('status', status)
    if (search) params.set('search', search)
    params.set('page', page)
    params.set('size', size)
    const { data } = await api.get(`/documents?${params}`)
    return data
  },

  async get(documentId) {
    const { data } = await api.get(`/documents/${documentId}`)
    return data
  },

  async getVersions(documentId) {
    const { data } = await api.get(`/documents/${documentId}/versions`)
    return data
  },

  // ── Update ─────────────────────────────────────────────────

  async update(documentId, { title, description, tags }) {
    const { data } = await api.patch(`/documents/${documentId}`, { title, description, tags })
    return data
  },

  async transitionStatus(documentId, targetStatus) {
    const { data } = await api.post(`/documents/${documentId}/status`, { targetStatus })
    return data
  },

  // ── Delete ─────────────────────────────────────────────────

  async delete(documentId) {
    await api.delete(`/documents/${documentId}`)
  },

  // ── Download ───────────────────────────────────────────────

  async getDownloadUrl(documentId) {
    const { data } = await api.get(`/documents/${documentId}/download-url`)
    return data.url
  },

  async downloadBlob(documentId, filename = 'document.pdf') {
    const response = await api.get(`/documents/${documentId}/download`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    window.URL.revokeObjectURL(url)
  },

  // ── Verify ─────────────────────────────────────────────────

  async verifyIntegrity(documentId) {
    const { data } = await api.post(`/documents/${documentId}/verify`)
    return data
  },

  // ── Stats ──────────────────────────────────────────────────

  async getStats() {
    const { data } = await api.get('/documents/stats')
    return data
  },

  // ── Helpers ────────────────────────────────────────────────

  formatFileSize(bytes) {
    if (!bytes) return '—'
    if (bytes < 1024)        return `${bytes} B`
    if (bytes < 1024 ** 2)   return `${(bytes / 1024).toFixed(1)} KB`
    if (bytes < 1024 ** 3)   return `${(bytes / 1024 ** 2).toFixed(1)} MB`
    return `${(bytes / 1024 ** 3).toFixed(2)} GB`
  },

  statusLabel(status) {
    const map = {
      DRAFT:            'Draft',
      SENT:             'Awaiting Signatures',
      PARTIALLY_SIGNED: 'Partially Signed',
      COMPLETED:        'Completed',
      EXPIRED:          'Expired',
      CANCELLED:        'Cancelled',
    }
    return map[status] ?? status
  },

  statusClass(status) {
    const map = {
      DRAFT:            'badge-draft',
      SENT:             'badge-sent',
      PARTIALLY_SIGNED: 'badge-partial',
      COMPLETED:        'badge-completed',
      EXPIRED:          'badge-expired',
      CANCELLED:        'badge-cancelled',
    }
    return map[status] ?? 'badge-draft'
  },
}

export default documentService