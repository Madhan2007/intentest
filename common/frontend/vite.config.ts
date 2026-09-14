/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

// Kernel dev config (doc 03 §2, doc 17 §6). `@modules` resolves module
// frontend folders that live outside this Vite project root — allowed
// explicitly via server.fs.allow (doc 02 §11: modules are siblings, never
// copied into common/).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@kernel": path.resolve(__dirname, "src"),
      "@modules": path.resolve(__dirname, "../../modules"),
      "react": path.resolve(__dirname, "node_modules/react"),
      "react-dom": path.resolve(__dirname, "node_modules/react-dom"),
      "react-router-dom": path.resolve(__dirname, "node_modules/react-router-dom"),
    },
    dedupe: ["react", "react-dom", "react-router-dom"],
  },
  server: {
    port: 5173,
    fs: {
      allow: [path.resolve(__dirname, "."), path.resolve(__dirname, "../../modules")],
    },
    proxy: {
      // Same-origin from the browser's point of view — avoids CORS in dev,
      // matching the single-Nginx-origin model in prod (doc 09 §4).
      "/api/v1/opzhub": { target: "http://localhost:8114", changeOrigin: true },
      "/api/v1/ai": { target: "http://localhost:8117", changeOrigin: true },
      "/ws/opzhub": { target: "ws://localhost:8114", ws: true },
    },
  },
});
