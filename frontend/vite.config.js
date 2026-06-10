import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Strip /api prefix before forwarding — Spring Boot's context-path
        // is already /api, so the backend receives the path without duplication.
        // Frontend: POST /api/auth/login
        // Backend receives: POST /auth/login  (under context /api)
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})