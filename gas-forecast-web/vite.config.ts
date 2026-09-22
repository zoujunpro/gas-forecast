import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5174,
    proxy: {
      '^/auth/.*': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/system/options$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/system/(users|roles|departments|permissions)/.+': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/base-(region|customer|industry)/.*': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(data-file-info|data-daily-sales|data-monthly-sales)/.*': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(model-config|model-feature-definition|model-train-config|model-train-feature-data|model-train-execution)/.*':
        {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => `/gas-forecast${path}`
        },
      '^/model-platform/.*': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(model-forecast-config|model-forecast-execution|model-forecast-record|model-forecast-result)/.*': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(monthly-results|short-term-results|winter-supply-results)(/.*)?$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/agents/config(\\?.*)?$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(predict|chat)$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/forecast/.*': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      }
    }
  }
})
