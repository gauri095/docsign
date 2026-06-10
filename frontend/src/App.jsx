import { Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import LoginPage    from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DocumentsPage from './pages/DocumentsPage'
import SigningPage  from './pages/SigningPage'

function DashboardPage() {
  return (
    <div className="min-h-screen bg-[var(--ink-950)] flex items-center justify-center">
      <div className="text-center">
        <div className="text-4xl mb-4">🚧</div>
        <p className="text-[var(--ink-300)]">Dashboard — coming in Phase 6</p>
        <a href="/documents" className="text-[var(--accent)] text-sm mt-2 block hover:underline">
          Go to Documents →
        </a>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login"       element={<LoginPage />}    />
      <Route path="/register"    element={<RegisterPage />} />
      <Route path="/sign/:token" element={<SigningPage />}  />

      {/* Protected */}
      <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
      <Route path="/documents" element={<ProtectedRoute><DocumentsPage /></ProtectedRoute>} />

      {/* Fallback */}
      <Route path="/"  element={<Navigate to="/documents" replace />} />
      <Route path="*"  element={<Navigate to="/documents" replace />} />
    </Routes>
  )
}