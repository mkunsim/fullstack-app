import test from "node:test";
import assert from "node:assert/strict";
import { createSitemap } from "../sitemap.mjs";

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
