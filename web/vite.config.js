import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    plugins: [vue()],
    optimizeDeps: {
      include: [
        'vue',
        'vue-router',
        'element-plus',
        '@element-plus/icons-vue',
        'element-plus/es/locale/lang/zh-cn',
      ],
    },
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      proxy: {
        '/api': { target: env.EXAM_API_TARGET || 'http://localhost:8080', changeOrigin: true },
      },
    },
  }
})
