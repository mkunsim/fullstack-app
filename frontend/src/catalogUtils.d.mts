export type CatalogSort = "recommended" | "price-asc" | "price-desc" | "name";

export function filterCatalog<T extends {
  id: number;
  name: string;
  description: string;
  destination: string;
}>(
  items: readonly T[],
  query: string,
  destination: string,
  sort: CatalogSort,
  getPrice: (item: T) => number,
): T[];

export function paginate<T>(items: readonly T[], page: number, pageSize: number): T[];
