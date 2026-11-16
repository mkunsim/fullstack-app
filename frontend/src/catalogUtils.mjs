export function filterCatalog(items, query, destination, sort, getPrice) {
  const search = query.trim().toLowerCase();
  return items
    .filter((item) => (!destination || item.destination === destination)
      && `${item.name} ${item.description} ${item.destination}`.toLowerCase().includes(search))
    .sort((a, b) => sort === "price-asc" ? getPrice(a) - getPrice(b)
      : sort === "price-desc" ? getPrice(b) - getPrice(a)
        : sort === "name" ? a.name.localeCompare(b.name) : a.id - b.id);
}

export function paginate(items, page, pageSize) {
  if (!Number.isSafeInteger(pageSize) || pageSize < 1) {
    throw new RangeError("Page size must be a positive integer.");
  }
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize));
  const currentPage = Math.min(pageCount, Math.max(1, Math.floor(page)));
  return items.slice((currentPage - 1) * pageSize, currentPage * pageSize);
}
