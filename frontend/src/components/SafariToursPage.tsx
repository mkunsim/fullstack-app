import { ArrowRight, CalendarDays, Compass } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import CardItem from "./CardItem";
import { filterCatalog, paginate, type CatalogSort } from "../catalogUtils.mjs";
import type { Tour } from "../types";

const money = (amount: number) => new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  maximumFractionDigits: 0,
}).format(amount);

interface SafariToursPageProps {
  tours: Tour[];
  loading: boolean;
  error: string;
  notice: string;
  dismissNotice: () => void;
}

export default function SafariToursPage({
  tours,
  loading,
  error,
  notice,
  dismissNotice,
}: SafariToursPageProps) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState("");
  const [destination, setDestination] = useState(() => searchParams.get("destination") ?? "");
  const [sort, setSort] = useState<CatalogSort>("recommended");
  const [page, setPage] = useState(1);
  const pageSize = 6;
  const destinations = [...new Set(tours.map((tour) => tour.destination))].sort();
  const filteredTours = useMemo(() => filterCatalog(tours, query, destination, sort, (tour) => tour.pricePerPerson),
  [tours, destination, query, sort]);
  const pageCount = Math.max(1, Math.ceil(filteredTours.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visibleTours = paginate(filteredTours, currentPage, pageSize);

  function selectDestination(value: string) {
    setDestination(value);
    setPage(1);
    const nextSearchParams = new URLSearchParams(searchParams);
    if (value) nextSearchParams.set("destination", value);
    else nextSearchParams.delete("destination");
    setSearchParams(nextSearchParams, { replace: true });
  }

  useEffect(() => {
    setDestination(searchParams.get("destination") ?? "");
    setPage(1);
  }, [searchParams]);

  return (
    <main className="catalog page-catalog">
      <div className="section-head">
        <div><span className="eyebrow dark">CURATED EAST AFRICAN SAFARI TOURS</span><h2>Choose your <em>adventure.</em></h2></div>
        <p>Prices shown in USD.<br />Departures subject to availability.</p>
      </div>
      {notice && <div className="notice" role="status"><span>{notice}</span><button onClick={dismissNotice} aria-label="Dismiss">×</button></div>}
      {error && <div className="notice error" role="alert">{error}. Check that the backend and PostgreSQL are running.</div>}
      {!loading && tours.length > 0 && <div className="catalog-controls">
        <label>Search experiences<input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="Name, destination, or detail" /></label>
        <label>Destination<select value={destination} onChange={(event) => selectDestination(event.target.value)}><option value="">All destinations</option>{destinations.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
        <label>Sort by<select value={sort} onChange={(event) => { setSort(event.target.value as CatalogSort); setPage(1); }}><option value="recommended">Recommended</option><option value="price-asc">Price: low to high</option><option value="price-desc">Price: high to low</option><option value="name">Name</option></select></label>
        <span className="catalog-count" role="status">{filteredTours.length} experience{filteredTours.length === 1 ? "" : "s"}</span>
      </div>}
      {loading ? <p className="loading">Loading journeys…</p> : visibleTours.length ? (
        <div className="tour-grid">
          {visibleTours.map((tour, index) => (
            <CardItem key={tour.id} ariaLabel={`View safari tour ${tour.name}`} onActivate={() => navigate(`/safari-tours/${tour.id}`)}>
              <div className="tour-photo"><img src={tour.imageUrl} alt={tour.name} /><span>0{(currentPage - 1) * pageSize + index + 1}</span></div>
              <div className="tour-body">
                <div className="location"><Compass size={13} />{tour.destination}</div>
                <h3>{tour.name}</h3>
                <p>{tour.description}</p>
                <div className="tour-meta"><span><CalendarDays size={14} />{tour.durationDays} days</span><strong>{money(tour.pricePerPerson)} <small>/ person</small></strong></div>
                <div className="departure-list">
                  <b>UPCOMING DEPARTURES</b>
                  {tour.departures?.length ? tour.departures.slice(0, 3).map((departure) => (
                    <div className="departure" key={departure.id}>
                      <span>{new Date(`${departure.startDate}T12:00:00`).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}<small>{departure.availableGuests} places left</small></span>
                      <Link to={`/safari-tours/${tour.id}`}>Details <ArrowRight size={14} /></Link>
                    </div>
                  )) : <p className="no-departure">No dates scheduled yet.</p>}
                </div>
              </div>
            </CardItem>
          ))}
        </div>
      ) : <p className="loading">{tours.length ? "No experiences match your search." : "No safari tours are available yet."}</p>}
      {!loading && filteredTours.length > pageSize && <nav className="catalog-pagination" aria-label="Safari tour pages">
        <button type="button" className="outline-btn" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>Previous</button>
        <span>Page {currentPage} of {pageCount}</span>
        <button type="button" className="outline-btn" disabled={currentPage === pageCount} onClick={() => setPage(currentPage + 1)}>Next</button>
      </nav>}
    </main>
  );
}
