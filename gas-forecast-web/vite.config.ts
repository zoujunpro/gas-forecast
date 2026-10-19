import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// 固定使用IPv4，避免Node在localhost的::1/127.0.0.1多地址连接中出现ECONNREFUSED。
const localApiTarget = 'http://127.0.0.1:18083'

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
      '^/agent-api/.*': {
        target: 'http://127.0.0.1:8091',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/agent-api/, '')
      },
      '^/auth/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/dashboard/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/system/options$': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/system/(users|roles|departments|permissions)/.+': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/base-(region|customer|industry)/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(data-file-info|data-daily-sales|data-monthly-sales|data-daily-weather)/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(model-config|model-feature-definition|model-train-config|model-train-feature-data|model-train-execution|agent-strategy-config)/.*':
        {
        target: localApiTarget,
          changeOrigin: true,
          rewrite: (path) => `/gas-forecast${path}`
        },
      '^/auto-train/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/ai/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/model-platform/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(model-forecast-config|model-forecast-execution|model-forecast-record|model-forecast-result|model-forecast-analysis)/.*': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(monthly-results|short-term-results|winter-supply-results)(/.*)?$': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/agents/config(\\?.*)?$': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/(predict|chat)$': {
        target: localApiTarget,
        changeOrigin: true,
        rewrite: (path) => `/gas-forecast${path}`
      },
      '^/forecast/.*': {
        target: localApiTarget,
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
