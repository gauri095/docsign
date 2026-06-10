import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, FileText, Bell, Webhook,
  ShieldCheck, LogOut, ChevronLeft, ChevronRight,
  User, Settings
} from 'lucide-react'
import { clsx } from 'clsx'
import { useAuth } from '../../hooks/useAuth'
import { useNotificationCount } from '../../hooks/useNotificationCount'
import { Logo } from '../ui/Logo'

const NAV = [
  { to: '/dashboard',  icon: LayoutDashboard, label: 'Dashboard'     },
  { to: '/documents',  icon: FileText,         label: 'Documents'     },
  { to: '/notifications', icon: Bell,           label: 'Notifications' },
  { to: '/webhooks',   icon: Webhook,           label: 'Webhooks'      },
]

const ADMIN_NAV = [
  { to: '/admin/audit', icon: ShieldCheck, label: 'Audit Admin' },
]

export function AppShell({ children }) {
  const { user, logout } = useAuth()
  const [collapsed, setCollapsed] = useState(false)
  const unread = useNotificationCount()
  const isAdmin = user?.role === 'ADMIN'

  return (
    <div className="flex min-h-screen bg-[var(--ink-950)]">

      {/* ── Sidebar ─────────────────────────────────────────── */}
      <aside className={clsx(
        'flex flex-col border-r border-[var(--ink-800)] bg-[var(--ink-900)]',
        'transition-all duration-300 shrink-0',
        collapsed ? 'w-16' : 'w-56'
      )}>

        {/* Logo */}
        <div className={clsx(
          'flex items-center border-b border-[var(--ink-800)] h-14',
          collapsed ? 'justify-center px-0' : 'px-4 gap-2'
        )}>
          <Logo size={collapsed ? 'sm' : 'md'} showText={!collapsed} />
        </div>

        {/* Nav */}
        <nav className="flex-1 py-3 flex flex-col gap-0.5 px-2">
          {NAV.map(({ to, icon: Icon, label }) => (
            <NavLink key={to} to={to} className={({ isActive }) => clsx(
              'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium',
              'transition-all duration-150 relative',
              isActive
                ? 'bg-[var(--ink-800)] text-[var(--ink-50)]'
                : 'text-[var(--ink-400)] hover:bg-[var(--ink-850)] hover:text-[var(--ink-200)]',
              collapsed && 'justify-center px-2'
            )}>
              <Icon size={16} className="shrink-0" />
              {!collapsed && <span>{label}</span>}
              {/* Unread badge on notifications */}
              {label === 'Notifications' && unread > 0 && (
                <span className={clsx(
                  'ml-auto text-[10px] font-bold bg-[var(--accent)] text-[var(--ink-950)]',
                  'rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1',
                  collapsed && 'absolute -top-1 -right-1'
                )}>
                  {unread > 99 ? '99+' : unread}
                </span>
              )}
            </NavLink>
          ))}

          {isAdmin && (
            <>
              <div className={clsx(
                'text-[10px] uppercase tracking-widest text-[var(--ink-600)] font-[var(--font-mono)]',
                'px-3 pt-4 pb-1', collapsed && 'text-center px-1 truncate'
              )}>
                {!collapsed && 'Admin'}
              </div>
              {ADMIN_NAV.map(({ to, icon: Icon, label }) => (
                <NavLink key={to} to={to} className={({ isActive }) => clsx(
                  'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all',
                  isActive
                    ? 'bg-[var(--ink-800)] text-[var(--ink-50)]'
                    : 'text-[var(--ink-400)] hover:bg-[var(--ink-850)] hover:text-[var(--ink-200)]',
                  collapsed && 'justify-center px-2'
                )}>
                  <Icon size={16} className="shrink-0" />
                  {!collapsed && <span>{label}</span>}
                </NavLink>
              ))}
            </>
          )}
        </nav>

        {/* User footer */}
        <div className="border-t border-[var(--ink-800)] p-2">
          {!collapsed && (
            <div className="flex items-center gap-2.5 px-2 py-2 rounded-lg bg-[var(--ink-850)] mb-1.5">
              <div className="w-7 h-7 rounded-full bg-[var(--ink-700)] flex items-center justify-center shrink-0">
                <User size={13} className="text-[var(--ink-300)]" />
              </div>
              <div className="min-w-0">
                <p className="text-xs font-medium text-[var(--ink-100)] truncate">{user?.name}</p>
                <p className="text-[10px] text-[var(--ink-500)] font-[var(--font-mono)] truncate">{user?.role}</p>
              </div>
            </div>
          )}
          <button onClick={logout} className={clsx(
            'w-full flex items-center gap-2 rounded-lg px-3 py-2 text-xs text-[var(--ink-400)]',
            'hover:bg-[var(--ink-800)] hover:text-[var(--danger)] transition-all cursor-pointer',
            collapsed && 'justify-center'
          )}>
            <LogOut size={14} />
            {!collapsed && 'Sign out'}
          </button>
        </div>

        {/* Collapse toggle */}
        <button
          onClick={() => setCollapsed(c => !c)}
          className="absolute bottom-24 -right-3 w-6 h-6 rounded-full bg-[var(--ink-800)]
                     border border-[var(--ink-700)] flex items-center justify-center
                     text-[var(--ink-400)] hover:text-[var(--ink-100)] transition-all
                     cursor-pointer shadow-sm"
        >
          {collapsed ? <ChevronRight size={11} /> : <ChevronLeft size={11} />}
        </button>
      </aside>

      {/* ── Main content ─────────────────────────────────────── */}
      <main className="flex-1 min-w-0 overflow-auto">
        {children}
      </main>
    </div>
  )
}