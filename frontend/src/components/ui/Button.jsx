import { clsx } from 'clsx'
import { Loader2 } from 'lucide-react'

const variants = {
  primary: [
    'bg-[var(--accent)] text-[var(--ink-950)]',
    'hover:bg-[var(--accent-hover)]',
    'font-semibold tracking-wide',
    'shadow-[0_0_16px_var(--accent-dim)]',
    'hover:shadow-[0_0_24px_var(--accent-dim)]',
  ].join(' '),

  secondary: [
    'bg-[var(--ink-800)] text-[var(--ink-100)]',
    'border border-[var(--ink-600)]',
    'hover:bg-[var(--ink-700)] hover:border-[var(--ink-500)]',
  ].join(' '),

  ghost: [
    'bg-transparent text-[var(--ink-300)]',
    'hover:bg-[var(--ink-800)] hover:text-[var(--ink-100)]',
  ].join(' '),

  danger: [
    'bg-transparent text-[var(--danger)]',
    'border border-[#FF4D4D33]',
    'hover:bg-[#FF4D4D11] hover:border-[#FF4D4D88]',
  ].join(' '),
}

const sizes = {
  sm: 'h-8 px-3 text-xs gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-12 px-6 text-base gap-2.5',
}

export function Button({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  fullWidth = false,
  className = '',
  ...props
}) {
  return (
    <button
      disabled={disabled || loading}
      className={clsx(
        'inline-flex items-center justify-center',
        'rounded-lg font-medium',
        'transition-all duration-(--dur)',
        'disabled:opacity-40 disabled:cursor-not-allowed',
        'cursor-pointer select-none',
        'font-(--font-display)',
        variants[variant],
        sizes[size],
        fullWidth && 'w-full',
        className
      )}
      {...props}
    >
      {loading && <Loader2 size={14} className="animate-spin shrink-0" />}
      {children}
    </button>
  )
}