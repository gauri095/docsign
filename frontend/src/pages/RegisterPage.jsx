import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail, Lock, Eye, EyeOff, User, ArrowRight, CheckCircle2 } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Input }  from '../components/ui/Input'
import { Logo }   from '../components/ui/Logo'
import { useAuth } from '../hooks/useAuth'

const PASSWORD_RULES = [
  { label: 'At least 8 characters', test: (p) => p.length >= 8 },
  { label: 'One uppercase letter',  test: (p) => /[A-Z]/.test(p) },
  { label: 'One number',            test: (p) => /[0-9]/.test(p) },
]

export default function RegisterPage() {
  const { register, loading } = useAuth()

  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [showPass,    setShowPass]    = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const set = (field) => (e) => {
    setForm((f) => ({ ...f, [field]: e.target.value }))
    // Clear field error on change
    if (errors[field]) setErrors((e) => ({ ...e, [field]: undefined }))
  }

  function validate() {
    const errs = {}
    if (!form.name.trim())
      errs.name = 'Name is required'
    else if (form.name.trim().length < 2)
      errs.name = 'Name must be at least 2 characters'

    if (!form.email)
      errs.email = 'Email is required'
    else if (!/\S+@\S+\.\S+/.test(form.email))
      errs.email = 'Enter a valid email address'

    if (!form.password)
      errs.password = 'Password is required'
    else if (form.password.length < 8)
      errs.password = 'Password must be at least 8 characters'

    if (!form.confirm)
      errs.confirm = 'Please confirm your password'
    else if (form.password !== form.confirm)
      errs.confirm = 'Passwords do not match'

    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return
    try {
      await register(form.name.trim(), form.email.toLowerCase().trim(), form.password)
    } catch {
      /* useAuth hook handles toast */
    }
  }

  const passwordStrength = PASSWORD_RULES.filter((r) => r.test(form.password)).length

  return (
    <div className="min-h-screen flex bg-[var(--ink-950)]">

      {/* ── Left panel — branding ──────────────────────────── */}
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

        {/* Accent glow — top-right for variety */}
        <div
          className="absolute top-[-80px] right-[-80px] w-[440px] h-[440px] rounded-full opacity-[0.06]"
          style={{ background: 'radial-gradient(circle, var(--accent), transparent 70%)' }}
        />

        <Logo size="lg" />

        <div className="relative z-10 animate-fade-up">
          <div className="text-[var(--ink-400)] text-xs font-[var(--font-mono)] uppercase tracking-widest mb-6">
            Join the platform
          </div>
          <h1 className="text-5xl font-bold leading-[1.1] mb-6 text-[var(--ink-50)]">
            Enterprise signatures,<br />
            <span className="text-[var(--accent)]">built for teams</span>
          </h1>
          <p className="text-[var(--ink-300)] text-lg leading-relaxed max-w-sm">
            Create your account and start sending
            documents for signature in minutes.
          </p>

          {/* Feature checklist */}
          <div className="mt-10 flex flex-col gap-3">
            {[
              'Free to get started — no credit card required',
              'AES-256 encrypted signature storage',
              'Immutable audit trail on every document',
            ].map((f) => (
              <div key={f} className="flex items-center gap-3 text-sm text-[var(--ink-300)]">
                <CheckCircle2 size={14} className="text-[var(--accent)] shrink-0" />
                {f}
              </div>
            ))}
          </div>
        </div>

        <div className="text-[var(--ink-600)] text-xs font-[var(--font-mono)]">
          v1.0.0-beta · com.labmentix.docsign
        </div>
      </div>

      {/* ── Right panel — form ─────────────────────────────── */}
      <div className="flex-1 flex flex-col items-center justify-center px-6 py-12 overflow-y-auto">
        <div className="w-full max-w-[380px] animate-fade-up">

          {/* Mobile logo */}
          <div className="lg:hidden mb-10">
            <Logo size="md" />
          </div>

          <div className="mb-7">
            <h2 className="text-2xl font-semibold text-[var(--ink-50)] mb-1">
              Create your account
            </h2>
            <p className="text-sm text-[var(--ink-400)]">
              Already have one?{' '}
              <Link
                to="/login"
                className="text-[var(--accent)] hover:text-[var(--accent-hover)] transition-colors font-medium"
              >
                Sign in
              </Link>
            </p>
          </div>

          <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">

            {/* Full name */}
            <Input
              label="Full name"
              type="text"
              placeholder="Name"
              icon={User}
              value={form.name}
              onChange={set('name')}
              error={errors.name}
              autoComplete="name"
              autoFocus
            />

            {/* Email */}
            <Input
              label="Email"
              type="email"
              placeholder="you@company.com"
              icon={Mail}
              value={form.email}
              onChange={set('email')}
              error={errors.email}
              autoComplete="email"
            />

            {/* Password */}
            <div className="flex flex-col gap-1.5">
              <Input
                label="Password"
                type={showPass ? 'text' : 'password'}
                placeholder="Min. 8 characters"
                icon={Lock}
                value={form.password}
                onChange={set('password')}
                error={errors.password}
                autoComplete="new-password"
                rightIcon={
                  <button
                    type="button"
                    onClick={() => setShowPass((v) => !v)}
                    className="text-[var(--ink-400)] hover:text-[var(--ink-200)] transition-colors cursor-pointer"
                    aria-label={showPass ? 'Hide password' : 'Show password'}
                  >
                    {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                }
              />

              {/* Password strength indicator */}
              {form.password.length > 0 && (
                <div className="flex flex-col gap-2 mt-1">
                  {/* Strength bar */}
                  <div className="flex gap-1">
                    {[0, 1, 2].map((i) => (
                      <div
                        key={i}
                        className="flex-1 h-1 rounded-full transition-all duration-300"
                        style={{
                          background: i < passwordStrength
                            ? passwordStrength === 1 ? '#FF4D4D'
                              : passwordStrength === 2 ? '#FFAA00'
                              : '#00D4AA'
                            : 'var(--ink-700)'
                        }}
                      />
                    ))}
                  </div>
                  {/* Rule checklist */}
                  <div className="flex flex-col gap-1">
                    {PASSWORD_RULES.map((rule) => {
                      const passed = rule.test(form.password)
                      return (
                        <div key={rule.label} className="flex items-center gap-1.5">
                          <div className={`w-1.5 h-1.5 rounded-full transition-colors ${
                            passed ? 'bg-[var(--accent)]' : 'bg-[var(--ink-700)]'
                          }`} />
                          <span className={`text-[11px] transition-colors font-[var(--font-mono)] ${
                            passed ? 'text-[var(--ink-300)]' : 'text-[var(--ink-500)]'
                          }`}>
                            {rule.label}
                          </span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>

            {/* Confirm password */}
            <Input
              label="Confirm password"
              type={showConfirm ? 'text' : 'password'}
              placeholder="Repeat your password"
              icon={Lock}
              value={form.confirm}
              onChange={set('confirm')}
              error={errors.confirm}
              autoComplete="new-password"
              rightIcon={
                <button
                  type="button"
                  onClick={() => setShowConfirm((v) => !v)}
                  className="text-[var(--ink-400)] hover:text-[var(--ink-200)] transition-colors cursor-pointer"
                  aria-label={showConfirm ? 'Hide password' : 'Show password'}
                >
                  {showConfirm ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              }
            />

            {/* Submit */}
            <Button
              type="submit"
              loading={loading}
              fullWidth
              size="lg"
              className="mt-1"
            >
              Create account
              <ArrowRight size={16} />
            </Button>
          </form>

          {/* Legal note */}
          <p className="mt-5 text-center text-[11px] text-[var(--ink-500)] leading-relaxed">
            By creating an account you agree that your electronic
            signatures are legally binding.
          </p>
        </div>
      </div>
    </div>
  )
}