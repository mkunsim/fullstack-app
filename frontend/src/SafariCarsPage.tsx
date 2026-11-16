import { ArrowRight, CheckCircle2, Compass, Users } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import CardItem from "./CardItem";
import { filterCatalog, paginate, type CatalogSort } from "./catalogUtils.mjs";
import type { SafariCar } from "./types";

const money = (amount: number) => new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  maximumFractionDigits: 0,
}).format(amount);

interface SafariCarsPageProps {
  cars: SafariCar[];
  loading: boolean;
}

export default function SafariCarsPage({ cars, loading }: SafariCarsPageProps) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [destination, setDestination] = useState("");
  const [sort, setSort] = useState<CatalogSort>("recommended");
  const [page, setPage] = useState(1);
  const pageSize = 6;
  const destinations = [...new Set(cars.map((car) => car.destination))].sort();
  const filteredCars = useMemo(() => filterCatalog(cars, query, destination, sort, (car) => car.dailyRateUsd),
  [cars, destination, query, sort]);
  const pageCount = Math.max(1, Math.ceil(filteredCars.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visibleCars = paginate(filteredCars, currentPage, pageSize);

  return (
    <main className="catalog car-catalog page-catalog">
      <div className="section-head">
        <div><span className="eyebrow dark">PRIVATE SAFARI TRANSPORT</span><h2>Explore by <em>safari car.</em></h2></div>
        <p>Professional driver included.<br />Optional guide priced separately.</p>
      </div>
      {!loading && cars.length > 0 && <div className="catalog-controls">
        <label>Search vehicles<input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="Name, destination, or feature" /></label>
        <label>Destination<select value={destination} onChange={(event) => { setDestination(event.target.value); setPage(1); }}><option value="">All destinations</option>{destinations.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
        <label>Sort by<select value={sort} onChange={(event) => { setSort(event.target.value as CatalogSort); setPage(1); }}><option value="recommended">Recommended</option><option value="price-asc">Price: low to high</option><option value="price-desc">Price: high to low</option><option value="name">Name</option></select></label>
        <span className="catalog-count" role="status">{filteredCars.length} vehicle{filteredCars.length === 1 ? "" : "s"}</span>
      </div>}
      {loading ? <p className="loading">Loading safari cars…</p> : visibleCars.length ? (
        <div className="tour-grid">
          {visibleCars.map((car, index) => (
            <CardItem key={car.id} ariaLabel={`View safari car ${car.name}`} onActivate={() => navigate(`/safari-cars/${car.id}`)}>
              <div className="tour-photo"><img src={car.imageUrl} alt={car.name} /><span>CAR {String((currentPage - 1) * pageSize + index + 1).padStart(2, "0")}</span></div>
              <div className="tour-body">
                <div className="location"><Compass size={13} />{car.destination}</div>
                <h3>{car.name}</h3>
                <p>{car.description}</p>
                <div className="tour-meta"><span><Users size={14} />{car.passengerCapacity} passengers</span><strong>{money(car.dailyRateUsd)} <small>/ day</small></strong></div>
                <p className="car-inclusion"><CheckCircle2 size={14} /> Professional driver included</p>
                <Link className="text-link" to={`/safari-cars/${car.id}`}>View vehicle <ArrowRight size={15} /></Link>
              </div>
            </CardItem>
          ))}
        </div>
      ) : <p className="loading">{cars.length ? "No vehicles match your search." : "No safari cars are available yet."}</p>}
      {!loading && filteredCars.length > pageSize && <nav className="catalog-pagination" aria-label="Safari vehicle pages">
        <button type="button" className="outline-btn" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>Previous</button>
        <span>Page {currentPage} of {pageCount}</span>
        <button type="button" className="outline-btn" disabled={currentPage === pageCount} onClick={() => setPage(currentPage + 1)}>Next</button>
      </nav>}
    </main>
  );
}
