import { defineConfig } from 'vite';

const apiTarget = process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8081';

export default defineConfig({
  server: {
    proxy: {
      '/graphql': { target: apiTarget, changeOrigin: true },
      '/api': { target: apiTarget, changeOrigin: true },
      '/actuator': { target: apiTarget, changeOrigin: true },
    },
  },
});
