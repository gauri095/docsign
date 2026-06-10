import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import App from './App.jsx'
import './styles/globals.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <App />
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 4000,
          style: {
            background: '#161D28',
            color: '#D4E2EE',
            border: '1px solid #263344',
            borderRadius: '8px',
            fontFamily: "'Sora', sans-serif",
            fontSize: '14px',
          },
          success: {
            iconTheme: { primary: '#00D4AA', secondary: '#161D28' },
          },
          error: {
            iconTheme: { primary: '#FF4D4D', secondary: '#161D28' },
          },
        }}
      />
    </BrowserRouter>
  </StrictMode>,
)