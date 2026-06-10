import api from './api'

const authService = {
  async register(name, email, password) {
    const { data } = await api.post('/auth/register', { name, email, password })
    persistTokens(data)
    return data
  },

  async login(email, password) {
    const { data } = await api.post('/auth/login', { email, password })
    persistTokens(data)
    return data
  },

  async logout() {
    try {
      await api.post('/auth/logout')
    } finally {
      clearTokens()
    }
  },

  async getMe() {
    const { data } = await api.get('/auth/me')
    return data
  },

  getStoredUser() {
    const raw = localStorage.getItem('user')
    return raw ? JSON.parse(raw) : null
  },

  isAuthenticated() {
    return !!localStorage.getItem('accessToken')
  },
}

function persistTokens(data) {
  localStorage.setItem('accessToken', data.accessToken)
  localStorage.setItem('refreshToken', data.refreshToken)
  localStorage.setItem('user', JSON.stringify(data.user))
}

function clearTokens() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
}

export default authService