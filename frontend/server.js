
import express from "express";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createProxyMiddleware } from "http-proxy-middleware";
import { createSitemapMiddleware } from "./sitemap.mjs";

const app = express();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = process.env.PORT || 5173;
const BACKEND_URL = process.env.BACKEND_URL || "http://backend:8080";
const SITEMAP_BASE_URL =
  process.env.SITEMAP_BASE_URL || "https://safaritours.dpdns.org";
const distPath = path.join(__dirname, "dist");

// Forward API requests to Spring Boot
app.use(
  "/api",
  createProxyMiddleware({
    target: BACKEND_URL,
    changeOrigin: true,
    ws: true
  })
);

app.get(
  "/sitemap.xml",
  createSitemapMiddleware( BACKEND_URL, SITEMAP_BASE_URL )
);

// Serve React production files
app.use(express.static(distPath));

// Support React Router
app.get(/.*/, (req, res) => {
  res.sendFile(path.join(distPath, "index.html"));
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Safari Tours running on port ${PORT}`);
  console.log(`Backend proxy: ${BACKEND_URL}`);
  console.log(`Sitemap URL: ${SITEMAP_BASE_URL}/sitemap.xml`);
});
