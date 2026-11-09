import { defineConfig } from 'vite';
import { deliveryImportBoundaries } from './import-boundaries.js';

export default defineConfig({
  plugins: [deliveryImportBoundaries()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
});

