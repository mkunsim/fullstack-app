import test from "node:test";
import assert from "node:assert/strict";
import { filterCatalog, paginate } from "../src/catalogUtils.mjs";

const items = [
  { id: 1, name: "Migration Trail", description: "Guided walking safari", destination: "Tanzania", price: 180 },
  { id: 2, name: "Coastal Escape", description: "Beach and wildlife", destination: "Kenya", price: 120 },
  { id: 3, name: "Highland Crossing", description: "Mountain trek", destination: "Tanzania", price: 240 },
];

test("catalog search and destination filter are case-insensitive", () => {
  assert.deepEqual(
    filterCatalog(items, "GUIDED", "Tanzania", "recommended", (item) => item.price).map((item) => item.id),
    [1],
  );
});

test("catalog sort orders results by price and name", () => {
  assert.deepEqual(
    filterCatalog(items, "", "", "price-asc", (item) => item.price).map((item) => item.id),
    [2, 1, 3],
  );
  assert.deepEqual(
    filterCatalog(items, "", "", "name", (item) => item.price).map((item) => item.name),
    ["Coastal Escape", "Highland Crossing", "Migration Trail"],
  );
});

test("pagination returns the requested page and clamps out-of-range pages", () => {
  assert.deepEqual(paginate(items, 2, 2).map((item) => item.id), [3]);
  assert.deepEqual(paginate(items, 10, 2).map((item) => item.id), [3]);
});

test("pagination rejects an invalid page size", () => {
  assert.throws(() => paginate(items, 1, 0), RangeError);
});
