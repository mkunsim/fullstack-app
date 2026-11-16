import { ArrowLeft, CheckCircle2, Compass, Users } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import EnquiryForm from "./EnquiryForm";
import type { SafariCar } from "./types";

export interface CarReservation {
  carId: number;
  startDate: string;
  endDate: string;
  pickupLocation: string;
  dropoffLocation: string;
  passengers: number;
  guideIncluded: boolean;
  fullName: string;
  email: string;
}

interface CarDetailsPageProps {
  cars: SafariCar[];
  loading: boolean;
  onBook: (reservation: CarReservation) => Promise<void>;
}

export default function CarDetailsPage({ cars, loading, onBook }: CarDetailsPageProps) {
  const { carId } = useParams();
  const car = cars.find((item) => item.id === Number(carId));
  const [reservation, setReservation] = useState<CarReservation>({
    carId: Number(carId),
    startDate: "",
    endDate: "",
    pickupLocation: "",
    dropoffLocation: "",
    passengers: 1,
    guideIncluded: false,
    fullName: "",
    email: "",
  });
  const [busy, setBusy] = useState(false);

  if (loading) return <main className="detail-page"><p className="loading">Loading vehicle…</p></main>;
  if (!car) return <main className="detail-page"><h1>Vehicle not found</h1><Link to="/safari-cars">Back to safari cars</Link></main>;
  const selectedCar = car;

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    try {
      await onBook({ ...reservation, carId: selectedCar.id });
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="detail-page">
      <Link className="back-link" to="/safari-cars"><ArrowLeft size={16} /> All safari cars</Link>
      <div className="detail-layout">
        <div className="detail-content">
          <img className="detail-image" src={car.imageUrl} alt={car.name} />
          <div className="location"><Compass size={14} />{car.destination}</div>
          <h1>{car.name}</h1>
          <p>{car.description}</p>
          <div className="detail-facts"><span><Users size={16} />Up to {car.passengerCapacity} passengers</span><strong>${car.dailyRateUsd} <small>per day</small></strong></div>
          <p className="car-inclusion"><CheckCircle2 size={14} /> Professional driver included</p>
        </div>
        <section className="panel detail-booking">
          <h2>Request a vehicle</h2>
          <form className="stack-form" onSubmit={(event) => void submit(event)}>
            <div className="form-row">
              <label>Pick-up date<input required type="date" min={new Date().toISOString().slice(0, 10)} value={reservation.startDate} onChange={(event) => setReservation({ ...reservation, startDate: event.target.value })} /></label>
              <label>Drop-off date<input required type="date" min={reservation.startDate || new Date().toISOString().slice(0, 10)} value={reservation.endDate} onChange={(event) => setReservation({ ...reservation, endDate: event.target.value })} /></label>
            </div>
            <div className="form-row">
              <label>Pick-up location<input required maxLength={180} value={reservation.pickupLocation} onChange={(event) => setReservation({ ...reservation, pickupLocation: event.target.value })} /></label>
              <label>Drop-off location<input required maxLength={180} value={reservation.dropoffLocation} onChange={(event) => setReservation({ ...reservation, dropoffLocation: event.target.value })} /></label>
            </div>
            <label>Passengers<input required type="number" min={1} max={car.passengerCapacity} value={reservation.passengers} onChange={(event) => setReservation({ ...reservation, passengers: Number(event.target.value) })} /></label>
            <label className="checkbox-label"><input type="checkbox" checked={reservation.guideIncluded} onChange={(event) => setReservation({ ...reservation, guideIncluded: event.target.checked })} /> Add a safari guide (${car.guideDailyRateUsd}/day)</label>
            <label>Full name<input required maxLength={120} value={reservation.fullName} onChange={(event) => setReservation({ ...reservation, fullName: event.target.value })} /></label>
            <label>Email<input required type="email" maxLength={254} value={reservation.email} onChange={(event) => setReservation({ ...reservation, email: event.target.value })} /></label>
            <button className="primary-btn" type="submit" disabled={busy}>{busy ? "Sending request…" : "Request vehicle"}</button>
          </form>
          <EnquiryForm itemType="VEHICLE" itemName={car.name} />
        </section>
      </div>
    </main>
  );
}
