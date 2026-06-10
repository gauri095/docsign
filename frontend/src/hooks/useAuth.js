import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import authService from '../services/authService'

export function useAuth() {
  const [user, setUser]       = useState(authService.getStoredUser)
  const [loading, setLoading] = useState(false)
  const navigate              = useNavigate()

  // Sync user state if localStorage changes (multi-tab)
  useEffect(() => {
    const handler = () => setUser(authService.getStoredUser())
    window.addEventListener('storage', handler)
    return () => window.removeEventListener('storage', handler)
  }, [])

  const login = useCallback(async (email, password) => {
    setLoading(true)
    try {
      const data = await authService.login(email, password)
      setUser(data.user)
      toast.success(`Welcome back, ${data.user.name.split(' ')[0]}`)
      navigate('/dashboard')
    } catch (err) {
      const msg = err.response?.data?.message || 'Login failed'
      toast.error(msg)
      throw err
    } finally {
      setLoading(false)
    }
  }, [navigate])

  const register = useCallback(async (name, email, password) => {
    setLoading(true)
    try {
      const data = await authService.register(name, email, password)
      setUser(data.user)
      toast.success('Account created — welcome to DocSign!')
      navigate('/dashboard')
    } catch (err) {
      const msg = err.response?.data?.message || 'Registration failed'
      toast.error(msg)
      throw err
    } finally {
      setLoading(false)
    }
  }, [navigate])

  const logout = useCallback(async () => {
    setLoading(true)
    try {
      await authService.logout()
      setUser(null)
      navigate('/login')
    } catch {
      // Always clear local state even if API call fails
      setUser(null)
      navigate('/login')
    } finally {
      setLoading(false)
    }
  }, [navigate])

  return {
    user,
    loading,
    isAuthenticated: !!user,
    login,
    register,
    logout,
  }
}