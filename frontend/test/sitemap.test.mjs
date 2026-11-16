import test from "node:test";
import assert from "node:assert/strict";
import { createSitemap, loadSitemap } from "../sitemap.mjs";

test("sitemap contains public catalog pages and dynamic detail URLs", () => {
  const result = createSitemap([{ id: 8 }, { id: 0 }], [{ id: 21 }, { id: "invalid" }], "https://example.com");

  assert.match(result, /https:\/\/example\.com\/safari-tours\/8/);
  assert.match(result, /https:\/\/example\.com\/safari-cars\/21/);
  assert.match(result, /https:\/\/example\.com\/safari-tours<\/loc>/);
  assert.doesNotMatch(result, /safari-tours\/0|safari-cars\/invalid/);
});

test("sitemap rejects malformed catalogs instead of silently omitting URLs", () => {
  assert.throws(() => createSitemap(null, [], "https://example.com"), TypeError);
});

test("sitemap loads current catalog IDs on each request", async () => {
  let tourId = 8;
  const fetchCatalog = async (url) => ({
    ok: true,
    json: async () => url.endsWith("/tours") ? [{ id: tourId }] : [{ id: 21 }],
  });

  const first = await loadSitemap("https://api.example.com/", "https://example.com", fetchCatalog);
  tourId = 9;
  const second = await loadSitemap("https://api.example.com/", "https://example.com", fetchCatalog);

  assert.match(first, /safari-tours\/8/);
  assert.match(second, /safari-tours\/9/);
  assert.doesNotMatch(second, /safari-tours\/8/);
});

test("sitemap fails if either catalog endpoint fails", async () => {
  const fetchCatalog = async (url) => ({
    ok: url.endsWith("/tours"),
    json: async () => [],
  });

  await assert.rejects(
    loadSitemap("https://api.example.com", "https://example.com", fetchCatalog),
    /Could not load catalog sitemap entries/,
  );
});
