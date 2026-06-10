import { clsx } from 'clsx'
import { forwardRef } from 'react'

export const Input = forwardRef(function Input(
  { label, error, icon: Icon, rightIcon, className = '', ...props },
  ref
) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label className="text-xs font-medium text-(--ink-300) uppercase tracking-widest font-(--font-mono)]">
          {label}
        </label>
      )}

      <div className="relative">
        {Icon && (
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-(--ink-400) pointer-events-none">
            <Icon size={15} />
          </div>
        )}

        <input
          ref={ref}
          className={clsx(
            'w-full h-10 rounded-lg text-sm',
            'bg-(--ink-900) text-(--ink-100)',
            'border transition-all duration-(--dur)',
            'placeholder:text-(--ink-500)',
            'font-(--font-display)',
            error
              ? 'border-(--danger) focus:border-(--danger) focus:ring-1 focus:ring-[#FF4D4D33]'
              : 'border-(--ink-700) focus:border-(--accent) focus:ring-1 focus:ring-(--accent-border)',
            'outline-none',
            Icon ? 'pl-9' : 'pl-3',
            rightIcon ? 'pr-9' : 'pr-3',
            className
          )}
          {...props}
        />

        {rightIcon && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2 text-(--ink-400)">
            {rightIcon}
          </div>
        )}
      </div>

      {error && (
        <p className="text-xs text-(--danger) font-(--font-mono)]">{error}</p>
      )}
    </div>
  )
})