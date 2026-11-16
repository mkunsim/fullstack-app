
import express from "express";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createProxyMiddleware } from "http-proxy-middleware";
import { createSitemap } from "./sitemap.mjs";

const app = express();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = process.env.PORT || 5173;
const BACKEND_URL = process.env.BACKEND_URL || "http://backend:8080";
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

app.get("/sitemap.xml", async (_req, res) => {
  try {
    const backendUrl = BACKEND_URL.replace(/\/+$/, "");
    const responses = await Promise.all([
      fetch(`${backendUrl}/api/tours`),
      fetch(`${backendUrl}/api/cars`)
    ]);
    if (responses.some((response) => !response.ok)) {
      return res.status(502).type("text/plain").send("Could not load catalog sitemap entries.");
    }

    const [tours, cars] = await Promise.all(responses.map((response) => response.json()));
    return res.type("application/xml").send(createSitemap(tours, cars, "https://safaritours.dpdns.org"));
  } catch (error) {
    console.error("Could not generate catalog sitemap:", error);
    return res.status(502).type("text/plain").send("Could not load catalog sitemap entries.");
  }
});

// Serve React production files
app.use(express.static(distPath));

// Support React Router
app.get(/.*/, (req, res) => {
  res.sendFile(path.join(distPath, "index.html"));
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Safari Tours running on port ${PORT}`);
  console.log(`Backend proxy: ${BACKEND_URL}`);
});
