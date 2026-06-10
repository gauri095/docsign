import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail, Lock, Eye, EyeOff, ArrowRight } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Input }  from '../components/ui/Input'
import { Logo }   from '../components/ui/Logo'
import { useAuth } from '../hooks/useAuth'

export default function LoginPage() {
  const { login, loading } = useAuth()

  const [form, setForm]     = useState({ email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [showPass, setShowPass] = useState(false)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  function validate() {
    const errs = {}
    if (!form.email)    errs.email    = 'Email is required'
    else if (!/\S+@\S+\.\S+/.test(form.email)) errs.email = 'Enter a valid email'
    if (!form.password) errs.password = 'Password is required'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return
    try {
      await login(form.email, form.password)
    } catch {
      /* hook handles toast */
    }
  }

  return (
    <div className="min-h-screen flex `bg-(--ink-950)`">

      {/* ── Left panel — branding ──────────────────────── */}
      <div className="hidden lg:flex lg:w-[52%] flex-col justify-between p-12 relative overflow-hidden">
        {/* Grid background */}
        <div
          className="absolute inset-0 opacity-[0.035]"
          style={{
            backgroundImage: `
              linear-gradient(var(--ink-400) 1px, transparent 1px),
              linear-gradient(90deg, var(--ink-400) 1px, transparent 1px)
            `,
            backgroundSize: '40px 40px',
          }}
        />
        {/* Accent glow */}
        <div
          className="absolute -bottom-25 -left-25 w-125 h-125 rounded-full opacity-[0.06]"
          style={{ background: 'radial-gradient(circle, var(--accent), transparent 70%)' }}
        />

        <Logo size="lg" />

        <div className="relative z-10 animate-fade-up">
          <div className="text-(--ink-400) text-xs font-(--font-mono) uppercase tracking-widest mb-6">
            Enterprise Document Platform
          </div>
          <h1 className="text-5xl font-bold leading-[1.1] mb-6 text-(--ink-50)">
            Sign documents<br />
            <span className="text-(--accent)">with legal confidence</span>
          </h1>
          <p className="text-(--ink-300) text-lg leading-relaxed max-w-sm">
            Immutable audit trails. Cryptographic integrity.
            Compliant digital signatures for modern teams.
          </p>

          {/* Feature list */}
          <div className="mt-10 flex flex-col gap-3">
            {[
              'SHA-256 document fingerprinting',
              'AES-256 encrypted signatures',
              'Legally traceable audit logs',
            ].map((f) => (
              <div key={f} className="flex items-center gap-3 text-sm text-(--ink-300)">
                <div className="w-1.5 h-1.5 rounded-full bg-(--accent) shrink-0" />
                {f}
              </div>
            ))}
          </div>
        </div>

        {/* Version tag */}
        <div className="text-(--ink-600) text-xs font-(--font-mono)">
          v1.0.0-beta · com.labmentix.docsign
        </div>
      </div>

      {/* ── Right panel — form ─────────────────────────── */}
      <div className="flex-1 flex flex-col items-center justify-center px-6 py-12">
        <div className="w-full max-w-95 animate-fade-up">

          {/* Mobile logo */}
          <div className="lg:hidden mb-10">
            <Logo size="md" />
          </div>

          <div className="mb-8">
            <h2 className="text-2xl font-semibold `text-(--ink-50) mb-1">
              Welcome back
            </h2>
            <p className="text-sm text-(--ink-400)">
              Sign in to your account
            </p>
          </div>

          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
            <Input
              label="Email"
              type="email"
              placeholder="you@company.com"
              icon={Mail}
              value={form.email}
              onChange={set('email')}
              error={errors.email}
              autoComplete="email"
              autoFocus
            />

            <Input
              label="Password"
              type={showPass ? 'text' : 'password'}
              placeholder="••••••••"
              icon={Lock}
              value={form.password}
              onChange={set('password')}
              error={errors.password}
              autoComplete="current-password"
              rightIcon={
                <button
                  type="button"
                  onClick={() => setShowPass((v) => !v)}
                  className="text-(--ink-400) hover:text-(--ink-200) transition-colors cursor-pointer"
                  aria-label={showPass ? 'Hide password' : 'Show password'}
                >
                  {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              }
            />

            <Button
              type="submit"
              loading={loading}
              fullWidth
              size="lg"
              className="mt-2"
            >
              Sign in
              <ArrowRight size={16} />
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-(--ink-400)">
            Don't have an account?{' '}
            <Link
              to="/register"
              className="text-(--accent) hover:text-(--accent-hover) transition-colors font-medium"
            >
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}