
import express from "express";
import helmet from "helmet";
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
    // Repository metadata and environment files.
    /(?:^|\/)server\.js(?:\.map)?$/,
    /(?:^|\/)\.env[^/]*$/,
    /(?:^|\/)(?:\.git|\.svn|\.hg|\.github|\.semaphore|\.buildkite)(?:\/|$)/,
    /(?:^|\/)(?:\.gitignore|\.gitattributes|\.gitmodules|\.hgignore)$/,
    /(?:^|\/)env(?:[-_.][^/]*)?\.js$/,

    // Credentials and filenames that indicate secret material.
    /(?:^|\/)(?:google-services|google-api-private-key|oauth-credentials|keycloak|secret|secrets|credentials?|service[-_]?account(?:[-_]?credentials?|[-_]?key)?|serviceaccountkey|firebase-adminsdk|gcloud-service-key|gcp-(?:vertex|credentials|service-account)|google-(?:credentials|vertex|service-account)|vertex(?:-ai)?|token|auth|public\/auth|ws-config)\.(?:json|ya?ml|toml|ini|properties|xml|env)$/,
    /(?:^|\/)[^/]*(?:secret|credential|token|private[-_]?key)[^/]*\.(?:json|ya?ml|toml|ini|properties|xml|php|rb)(?:\.(?:bak|old|orig|save|dist|txt))?$/,
    /(?:^|\/)(?:\.composer-auth|sftp|sftp-config|sendgrid)\.(?:json|env)$/,

    // Common configuration and deployment filenames.
    /(?:^|\/)(?:config|settings)\.(?:js|json|ya?ml|toml|ini|xml|php|py)(?:\.(?:bak|old|orig|save|dist|txt))?$/,
    /(?:^|\/)(?:settings(?:\.local)?\.php|parameters\.ya?ml(?:\.dist)?|storage\.ya?ml|host\.json|launchsettings\.json|ovh-backups\.json|packages\.config|nuget\.config|web\.config|s3cmd\.ini|local\.xml|serverless\.ya?ml|docker-compose(?:\.[^/]*)?\.ya?ml|compose\.ya?ml)$/,
    /(?:^|\/)(?:\.gitlab-ci\.yml|gitlab-ci\.yml|\.travis\.yml|appveyor\.yml|circle\.yml|jenkins\.ya?ml|jenkinsfile|wercker\.yml|buildkite\.yml|codeship-(?:steps|services)\.yml|bitrise\.yml|bamboo\.yml)$/,

    // Package inventories, infrastructure state, database dumps, and histories.
    /(?:^|\/)(?:package(?:-lock)?\.json|pnpm-lock\.ya?ml|yarn\.lock)$/,
    /(?:^|\/)(?:terraform\.tfstate(?:\.backup)?|[^/]+\.tfvars)$/,
    /(?:^|\/)(?:backup|dump|database|database_backup|db|db_dump|data|mysql|mysql_dump|site)\.sql(?:\.bak)?$/,
    /(?:^|\/)(?:postman(?:_collection)?|docs\/postman)\.json$/,
    /(?:^|\/)(?:node_repl|psql|bash|zsh)_history$/,
    /(?:^|\/)wgetrc$/,

    // Server controls and unrelated framework probes.
    /(?:^|\/)actuator(?:\/|$)/,
    /(?:^|\/)[^/]*(?:phpinfo|php-info|phpversion|server-info|server-status)[^/]*(?:\.php)?$/,
    /(?:^|\/)(?:phpinfo(?:\.php)?|php-info\.php|phpversion\.php|server-info\.php|server-status\.php|php\.php|info(?:\.php)?|i\.php|pi\.php|pinfo\.php|p\.php|test\.php|debug\.php|_phpinfo\.php)$/,
    /(?:^|\/)_environment$/,
    /(?:^|\/)(?:settings|config)\.(?:php|py)\.(?:bak|old|orig|save|dist|txt)$/,
    /(?:^|\/)\.htaccess$/,
    /(?:^|\/)\.htpasswd$/,
    /(?:^|\/)(?:wordpress|wp(?:-json|-admin|-content|-includes)?)(?:\/|$)/
  ];

  if (blockedPatterns.some(pattern => pattern.test(pathname))) {
    return res.status(404).end();
  }

  console.log(
    `[SECURITY] Allowed request: ${req.method} ${req.path} from ${req.ip}`
  );

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
