import { useState, useEffect } from 'react'
import api from '../services/api'
import authService from '../services/authService'

export function useNotificationCount() {
  const [count, setCount] = useState(0)

  useEffect(() => {
    if (!authService.isAuthenticated()) return

    const fetch = async () => {
      try {
        const { data } = await api.get('/notifications/unread-count')
        setCount(data.count ?? 0)
      } catch {
        // silently ignore — not critical
      }
    }

    fetch()
    const id = setInterval(fetch, 30_000)
    return () => clearInterval(id)
  }, [])

  return count
}