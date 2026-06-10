import { clsx } from 'clsx'

export function Logo({ size = 'md', className = '' }) {
  const sizes = {
    sm: { text: 'text-base', icon: 20 },
    md: { text: 'text-xl',   icon: 24 },
    lg: { text: 'text-3xl',  icon: 32 },
  }
  const s = sizes[size]

  return (
    <div className={clsx('flex items-center gap-2.5 select-none', className)}>
      {/* Glyph — stylised pen-nib / signature icon */}
      <svg
        width={s.icon}
        height={s.icon}
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <rect width="24" height="24" rx="6" fill="var(--accent)" opacity="0.12" />
        <path
          d="M6 17L10 7L14 14L17 10L19 13"
          stroke="var(--accent)"
          strokeWidth="1.75"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="19" cy="13" r="1.25" fill="var(--accent)" />
        <line
          x1="5"
          y1="19"
          x2="14"
          y2="19"
          stroke="var(--accent)"
          strokeWidth="1.5"
          strokeLinecap="round"
          opacity="0.5"
        />
      </svg>

      <span className={clsx('font-semibold tracking-tight font-(--font-display)]', s.text)}>
        <span className="text-(--ink-50)">Doc</span>
        <span className="text-(--accent)">Sign</span>
      </span>
    </div>
  )
}