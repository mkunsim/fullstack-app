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
  onBook: (tour: Tour, departureId: number, guests: number, customerName: string, customerEmail: string, optionalExperienceIds: number[]) => Promise<void>;
}

export default function TourDetailsPage({ tours, loading, onBook }: TourDetailsPageProps) {
  const { tourId } = useParams();
  const tour = tours.find((item) => item.id === Number(tourId));
  const [departureId, setDepartureId] = useState<number>(0);
  const [guests, setGuests] = useState(2);
  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [selectedOptionalExperienceIds, setSelectedOptionalExperienceIds] = useState<number[]>([]);
  const [optionalExperiencesDialogOpen, setOptionalExperiencesDialogOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const optionalItemsRef = useRef<HTMLElement>(null);

  useEffect(() => {
    setSelectedOptionalExperienceIds([]);
  }, [tourId]);

  useEffect(() => {
    if (!optionalExperiencesDialogOpen) return;
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setOptionalExperiencesDialogOpen(false);
    }
    function handleOutsideClick(event: PointerEvent) {
      if (event.target instanceof Node && !optionalItemsRef.current?.contains(event.target)) {
        setOptionalExperiencesDialogOpen(false);
      }
    }
    document.addEventListener("keydown", handleEscape);
    document.addEventListener("pointerdown", handleOutsideClick);
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.removeEventListener("pointerdown", handleOutsideClick);
    };
  }, [optionalExperiencesDialogOpen]);

  if (loading) return <main className="detail-page"><p className="loading">Loading experience…</p></main>;
  if (!tour) return <main className="detail-page"><h1>Safari tour not found</h1><Link to="/safari-tours">Back to safari tours</Link></main>;
  const selectedTour = tour;

  const availableDepartures = selectedTour.departures?.filter((item) => item.availableGuests > 0) ?? [];
  const availableOptionalExperiences = selectedTour.optionalExperiences?.filter((item) => item.available) ?? [];
  const selectedDeparture = availableDepartures.find((item) => item.id === departureId);
  const selectedOptionalExperiences = availableOptionalExperiences.filter((item) => selectedOptionalExperienceIds.includes(item.id));
  const optionalExperienceTotal = selectedOptionalExperiences.reduce((total, item) => total + item.price, 0);
  const totalPrice = selectedTour.pricePerPerson * guests + optionalExperienceTotal;
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDeparture) return;
    setBusy(true);
    try {
      await onBook(selectedTour, selectedDeparture.id, guests, customerName, customerEmail, selectedOptionalExperienceIds);
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
              {availableOptionalExperiences.length > 0 && (
                <section className="optional-items" ref={optionalItemsRef}>
                  <button
                    type="button"
                    className="optional-trigger"
                    onClick={() => setOptionalExperiencesDialogOpen((open) => !open)}
                    aria-expanded={optionalExperiencesDialogOpen}
                    aria-haspopup="dialog"
                    aria-controls="optional-experiences-dialog"
                  >
                    <span className="optional-count">
                      <span className="optional-count-label">
                        <svg className="check-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                        {selectedOptionalExperiences.length} {selectedOptionalExperiences.length === 1 ? "experience" : "experiences"} selected
                      </span>
                      <span className="optional-count-total">Additional cost: {formatUsd(optionalExperienceTotal)}</span>
                    </span>
                    <ChevronDown className="trigger-arrow" size={16} />
                  </button>

                  {optionalExperiencesDialogOpen && (
                    <section
                      className="optional-dropdown"
                      id="optional-experiences-dialog"
                      role="dialog"
                      aria-modal="false"
                      aria-labelledby="optional-experiences-title"
                    >
                      <h2 id="optional-experiences-title">Optional experiences</h2>
                      <p className="optional-intro">Choose experiences to personalize your safari.</p>
                      <div className="optional-search">
                        <input
                          type="search"
                          placeholder="Search options…"
                          value={searchQuery}
                          onChange={(event) => setSearchQuery(event.target.value)}
                          autoFocus
                        />
                      </div>
                      <ul className="optional-list">
                        {availableOptionalExperiences
                          .filter((optionalExperience) =>
                            optionalExperience.name
                              .toLowerCase()
                              .includes(searchQuery.toLowerCase())
                          )
                          .map((optionalExperience) => {
                            const isSelected = selectedOptionalExperienceIds.includes(optionalExperience.id);
                            return (
                              <li key={optionalExperience.id} className="optional-item">
                                <label className="optional-row">
                                  <input
                                    type="checkbox"
                                    checked={isSelected}
                                    onChange={() =>
                                      setSelectedOptionalExperienceIds((current) =>
                                        isSelected
                                          ? current.filter((id) => id !== optionalExperience.id)
                                          : [...current, optionalExperience.id]
                                      )
                                    }
                                  />
                                  <span
                                    className="optional-name"
                                    title={`Description: ${optionalExperience.description}\nPreference: ${optionalExperience.preference}\nUSD ${optionalExperience.price.toFixed(2)}`}
                                  >
                                    {optionalExperience.name}
                                  </span>
                                  <span className="optional-price">
                                    {optionalExperience.price === 0 ? "No extra charge" : `+${formatUsd(optionalExperience.price)}`}
                                  </span>
                                </label>
                              </li>
                            );
                          })}
                      </ul>
                      <div className="optional-footer">
                        <div className="optional-footer-total" aria-live="polite">
                          <span>Additional cost</span>
                          <strong>{formatUsd(optionalExperienceTotal)}</strong>
                        </div>
                        <div className="optional-footer-actions">
                          <button type="button" className="clear-all" onClick={() => setSelectedOptionalExperienceIds([])}>
                            Clear all
                          </button>
                          <button type="button" className="done-btn" onClick={() => setOptionalExperiencesDialogOpen(false)}>
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
