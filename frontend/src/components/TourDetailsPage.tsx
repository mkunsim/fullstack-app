import { CalendarDays, Compass, ChevronDown } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import EnquiryForm from "./EnquiryForm";
import type { Tour } from "../types";

function formatUsd(amount: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: Number.isInteger(amount) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

interface TourDetailsPageProps {
  tours: Tour[];
  loading: boolean;
  onBook: (tour: Tour, departureId: number, guests: number, customerName: string, customerEmail: string, addonIds: number[]) => Promise<void>;
}

export default function TourDetailsPage({ tours, loading, onBook }: TourDetailsPageProps) {
  const { tourId } = useParams();
  const tour = tours.find((item) => item.id === Number(tourId));
  const [departureId, setDepartureId] = useState<number>(0);
  const [guests, setGuests] = useState(2);
  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [selectedAddonIds, setSelectedAddonIds] = useState<number[]>([]);
  const [addonsDialogOpen, setAddonsDialogOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const addonsRef = useRef<HTMLElement>(null);

  useEffect(() => {
    setSelectedAddonIds([]);
  }, [tourId]);

  useEffect(() => {
    if (!addonsDialogOpen) return;
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setAddonsDialogOpen(false);
    }
    function handleOutsideClick(event: PointerEvent) {
      if (event.target instanceof Node && !addonsRef.current?.contains(event.target)) {
        setAddonsDialogOpen(false);
      }
    }
    document.addEventListener("keydown", handleEscape);
    document.addEventListener("pointerdown", handleOutsideClick);
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.removeEventListener("pointerdown", handleOutsideClick);
    };
  }, [addonsDialogOpen]);

  if (loading) return <main className="detail-page"><p className="loading">Loading experience…</p></main>;
  if (!tour) return <main className="detail-page"><h1>Safari tour not found</h1><Link to="/safari-tours">Back to safari tours</Link></main>;
  const selectedTour = tour;

  const availableDepartures = selectedTour.departures?.filter((item) => item.availableGuests > 0) ?? [];
  const availableAddons = selectedTour.addons?.filter((item) => item.available) ?? [];
  const selectedDeparture = availableDepartures.find((item) => item.id === departureId);
  const selectedAddons = availableAddons.filter((item) => selectedAddonIds.includes(item.id));
  const addonTotal = selectedAddons.reduce((total, item) => total + item.price, 0);
  const totalPrice = selectedTour.pricePerPerson * guests + addonTotal;
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDeparture) return;
    setBusy(true);
    try {
      await onBook(selectedTour, selectedDeparture.id, guests, customerName, customerEmail, selectedAddonIds);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="detail-page">
      <div className="detail-layout">
        <div className="detail-content">
          <img className="detail-image" src={tour.imageUrl} alt={tour.name} />
          <div className="location"><Compass size={14} />{tour.destination}</div>
          <h1>{tour.name}</h1>
          <p>{tour.description}</p>
          <div className="detail-facts"><span><CalendarDays size={16} />{tour.durationDays} days</span><strong>${tour.pricePerPerson} <small>per person</small></strong></div>
        </div>
        <section className="panel detail-booking">
          <h2>Request {tour.name}</h2>
          {availableDepartures.length ? (
            <form className="stack-form" onSubmit={(event) => void submit(event)}>
              <label>Departure date<select required value={departureId || ""} onChange={(event) => { const selected = availableDepartures.find((item) => item.id === Number(event.target.value)); setDepartureId(Number(event.target.value)); if (selected) setGuests(Math.min(2, selected.availableGuests)); }}>
                <option value="" disabled>Select a departure</option>
                {availableDepartures.map((item) => <option key={item.id} value={item.id}>{new Date(`${item.startDate}T12:00:00`).toLocaleDateString()} · {item.availableGuests} places</option>)}
              </select></label>
              <label>Guests<input required type="number" min={1} max={selectedDeparture?.availableGuests ?? 1} value={guests} onChange={(event) => setGuests(Number(event.target.value))} /></label>
              <div className="booking-price-summary" aria-live="polite">
                <span>Safari tour ({guests} {guests === 1 ? "guest" : "guests"})</span>
                <strong>USD {totalPrice.toFixed(2)}</strong>
              </div>
              <label>Full name<input required maxLength={120} value={customerName} onChange={(event) => setCustomerName(event.target.value)} /></label>
              <label>Email<input required type="email" maxLength={254} value={customerEmail} onChange={(event) => setCustomerEmail(event.target.value)} /></label>
              {availableAddons.length > 0 && (
                <section className="addon-items" ref={addonsRef}>
                  <button
                    type="button"
                    className="addon-trigger"
                    onClick={() => setAddonsDialogOpen((open) => !open)}
                    aria-expanded={addonsDialogOpen}
                    aria-haspopup="dialog"
                    aria-controls="addons-dialog"
                  >
                    <span className="addon-count">
                      <span className="addon-count-label">
                        <svg className="check-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                        {selectedAddons.length} {selectedAddons.length === 1 ? "extra" : "extras"} selected
                      </span>
                      <span className="addon-count-total">Additional cost: {formatUsd(addonTotal)}</span>
                    </span>
                    <ChevronDown className="trigger-arrow" size={16} />
                  </button>

                  {addonsDialogOpen && (
                    <section
                      className="addon-dropdown"
                      id="addons-dialog"
                      role="dialog"
                      aria-modal="false"
                      aria-labelledby="addons-title"
                    >
                      <h2 id="addons-title">Extras</h2>
                      <p className="addon-intro">Choose extras to personalize your safari.</p>
                      <div className="addon-search">
                        <input
                          type="search"
                          placeholder="Search options…"
                          value={searchQuery}
                          onChange={(event) => setSearchQuery(event.target.value)}
                          autoFocus
                        />
                      </div>
                      <ul className="addon-list">
                        {availableAddons
                          .filter((addon) =>
                            addon.name
                              .toLowerCase()
                              .includes(searchQuery.toLowerCase())
                          )
                          .map((addon) => {
                            const isSelected = selectedAddonIds.includes(addon.id);
                            return (
                              <li key={addon.id} className="addon-item">
                                <label className="addon-row">
                                  <input
                                    type="checkbox"
                                    checked={isSelected}
                                    onChange={() =>
                                      setSelectedAddonIds((current) =>
                                        isSelected
                                          ? current.filter((id) => id !== addon.id)
                                          : [...current, addon.id]
                                      )
                                    }
                                  />
                                  <span
                                    className="addon-name"
                                    title={`Description: ${addon.description}\nPreference: ${addon.preference}\nUSD ${addon.price.toFixed(2)}`}
                                  >
                                    {addon.name}
                                  </span>
                                  <span className="addon-price">
                                    {addon.price === 0 ? "No extra charge" : `+${formatUsd(addon.price)}`}
                                  </span>
                                </label>
                              </li>
                            );
                          })}
                      </ul>
                      <div className="addon-footer">
                        <div className="addon-footer-total" aria-live="polite">
                          <span>Additional cost</span>
                          <strong>{formatUsd(addonTotal)}</strong>
                        </div>
                        <div className="addon-footer-actions">
                          <button type="button" className="clear-all" onClick={() => setSelectedAddonIds([])}>
                            Clear all
                          </button>
                          <button type="button" className="done-btn" onClick={() => setAddonsDialogOpen(false)}>
                            Done
                          </button>
                        </div>
                      </div>
                    </section>
                  )}
                </section>
              )}
              <button className="primary-btn" type="submit" disabled={busy || !selectedDeparture}>{busy ? "Sending request…" : "Request booking"}</button>
            </form>
          ) : <p>No departure dates are currently available for this experience.</p>}
          <EnquiryForm itemType="TOUR" itemName={tour.name} />
        </section>
      </div>
    </main>
  );
}
