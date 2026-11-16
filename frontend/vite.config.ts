import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { createSitemapMiddleware } from './sitemap.mjs'

const backendUrl = process.env.BACKEND_PROXY_TARGET ?? 'http://localhost:8080'
const sitemapBaseUrl = process.env.SITEMAP_BASE_URL ?? 'https://safaritours.dpdns.org'
const sitemapMiddleware = createSitemapMiddleware(backendUrl, sitemapBaseUrl)

function dynamicSitemapPlugin(): Plugin {
  return {
    name: 'dynamic-sitemap',
    configureServer(server) {
      server.middlewares.use('/sitemap.xml', sitemapMiddleware)
    },
    configurePreviewServer(server) {
      server.middlewares.use('/sitemap.xml', sitemapMiddleware)
    },
  }
}

export default defineConfig({
  plugins: [react(), dynamicSitemapPlugin()],
  server: {
    port: 4173,
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
    },
  },
})
