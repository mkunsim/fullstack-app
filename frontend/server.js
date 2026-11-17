
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


// --------------------------------------------------
// 1. Basic security
// --------------------------------------------------

app.disable("x-powered-by");

// Security headers.
// CSP is disabled here initially to avoid breaking
// existing React scripts and external resources.
// Configure a restrictive CSP separately.
app.use(
  helmet({
    contentSecurityPolicy: false
  })
);

// --------------------------------------------------
// 2. Block sensitive file requests
// --------------------------------------------------

app.use((req, res, next) => {
  const pathname = req.path.toLowerCase();

  const blockedPatterns = [
    /(?:^|\/)server\.js(?:\.map)?$/,
    /(?:^|\/)\.env(?:\.[^/]*)?$/,
    /(?:^|\/)\.git(?:\/|$)/,
    /(?:^|\/)package(?:-lock)?\.json$/,
    /(?:^|\/)config\.js$/,
    /(?:^|\/)\.htaccess$/,
    /(?:^|\/)\.htpasswd$/
  ];

  if (blockedPatterns.some(pattern => pattern.test(pathname))) {
    console.warn(
      `[SECURITY] Blocked request: ${req.method} ${req.path} from ${req.ip}`
    );

    return res.status(404).end();
  }

  next();
});


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
