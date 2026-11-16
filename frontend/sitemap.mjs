export function createSitemap(tours, cars, baseUrl) {
  if (!Array.isArray(tours) || !Array.isArray(cars)) {
    throw new TypeError("Sitemap catalogs must be arrays.");
  }
  const paths = [
    "/",
    "/safari-tours",
    "/safari-cars",
    ...tours.filter((item) => Number.isSafeInteger(item.id) && item.id > 0)
      .map((item) => `/safari-tours/${item.id}`),
    ...cars.filter((item) => Number.isSafeInteger(item.id) && item.id > 0)
      .map((item) => `/safari-cars/${item.id}`)
  ];
  const entries = paths.map((item) => `  <url><loc>${baseUrl}${item}</loc></url>`).join("\n");
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries}\n</urlset>`;
}

export async function loadSitemap(backendUrl, baseUrl, fetchImpl = fetch) {
  const backend = backendUrl.replace(/\/+$/, "");
  const responses = await Promise.all([
    fetchImpl(`${backend}/api/tours`),
    fetchImpl(`${backend}/api/cars`),
  ]);
  if (responses.some((response) => !response.ok)) {
    throw new Error("Could not load catalog sitemap entries.");
  }

  const [tours, cars] = await Promise.all(responses.map((response) => response.json()));
  return createSitemap(tours, cars, baseUrl);
}

export function createSitemapMiddleware(backendUrl, baseUrl) {
  return (_request, response) => {
    loadSitemap(backendUrl, baseUrl)
      .then((sitemap) => {
        response.statusCode = 200;
        response.setHeader("Content-Type", "application/xml; charset=utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.end(sitemap);
      })
      .catch((error) => {
        console.error("Could not generate catalog sitemap:", error);
        response.statusCode = 502;
        response.setHeader("Content-Type", "text/plain; charset=utf-8");
        response.end("Could not load catalog sitemap entries.");
      });
  };
}
