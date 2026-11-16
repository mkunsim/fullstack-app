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
