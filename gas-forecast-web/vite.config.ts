import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
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
  },
  build: {
    // ECharts is isolated as a lazy, cacheable vendor chunk; its gzip size is about 200 kB.
    chunkSizeWarningLimit: 650,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          charts: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers']
        }
      }
    }
  }
})
