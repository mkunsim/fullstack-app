import { useState, type FormEvent, type Dispatch, type SetStateAction } from "react";
import { Link } from "react-router-dom";
import type { OptionalExperience, AdminBooking, CarBooking, SafariCar, Tour, User } from "../types";

export interface AdminTourForm {
  name: string;
  description: string;
  destination: string;
  durationDays: number;
  pricePerPerson: number;
  imageUrl: string;
}

export interface AdminDepartureForm {
  tourId: number;
  startDate: string;
  capacity: number;
}

export interface AdminCarForm {
  name: string;
  description: string;
  destination: string;
  passengerCapacity: number;
  dailyRateUsd: number;
  guideDailyRateUsd: number;
  imageUrl: string;
}

export interface AdminOptionalExperienceForm {
  name: string;
  description: string;
  preference: string;
  price: number;
  available: boolean;
}

interface AdminPageProps {
  user: User | null;
  tours: Tour[];
  optionalExperiences: OptionalExperience[];
  cars: SafariCar[];
  tourBookings: AdminBooking[];
  carBookings: CarBooking[];
  tourForm: AdminTourForm;
  setTourForm: Dispatch<SetStateAction<AdminTourForm>>;
  departureForm: AdminDepartureForm;
  setDepartureForm: Dispatch<SetStateAction<AdminDepartureForm>>;
  carForm: AdminCarForm;
  setCarForm: Dispatch<SetStateAction<AdminCarForm>>;
  onCreateTour: (event: FormEvent<HTMLFormElement>) => void;
  onUpdateTour: (id: number, active: boolean, event: FormEvent<HTMLFormElement>) => void;
  onCreateDeparture: (event: FormEvent<HTMLFormElement>) => void;
  onCreateCar: (event: FormEvent<HTMLFormElement>) => void;
  onUpdateCar: (id: number, active: boolean, event: FormEvent<HTMLFormElement>) => void;
  onUpdateBookingStatus: (id: number, status: "CONFIRMED" | "CANCELLED" | "DECLINED") => void;
  onUpdateCarBookingStatus: (id: number, status: "CONFIRMED" | "CANCELLED" | "DECLINED") => void;
  optionalExperienceForm: AdminOptionalExperienceForm;
  setOptionalExperienceForm: Dispatch<SetStateAction<AdminOptionalExperienceForm>>;
  editingOptionalExperienceId: number | null;
  setEditingOptionalExperienceId: Dispatch<SetStateAction<number | null>>;
  onCreateOptionalExperience: (input: AdminOptionalExperienceForm) => void;
  onUpdateOptionalExperience: (id: number, input: AdminOptionalExperienceForm) => void;
  notice: string;
}

export default function AdminPage(props: AdminPageProps) {
  const {
    user, tours, optionalExperiences, cars, tourBookings, carBookings,
    tourForm, setTourForm, departureForm, setDepartureForm,
    carForm, setCarForm, onCreateTour, onUpdateTour, onCreateDeparture, onCreateCar,
    onUpdateCar, onUpdateBookingStatus, onUpdateCarBookingStatus, notice,
    optionalExperienceForm, setOptionalExperienceForm, editingOptionalExperienceId, setEditingOptionalExperienceId, onCreateOptionalExperience, onUpdateOptionalExperience,
  } = props;
  const [editingTourId, setEditingTourId] = useState<number | null>(null);
  const [editingTourActive, setEditingTourActive] = useState(true);
  const [editingCarId, setEditingCarId] = useState<number | null>(null);
  const [editingCarActive, setEditingCarActive] = useState(true);
  function selectTour(id: number | null) {
    setEditingTourId(id);
    const tour = tours.find((row) => row.id === id);
    if (!tour) {
      setEditingTourActive(true);
      setTourForm({ name: "", description: "", destination: "", durationDays: 3, pricePerPerson: 990, imageUrl: "" });
      return;
    }
    setEditingTourActive(tour.active !== false);
    setTourForm({
      name: tour.name,
      description: tour.description,
      destination: tour.destination,
      durationDays: tour.durationDays,
      pricePerPerson: tour.pricePerPerson,
      imageUrl: tour.imageUrl,
    });
  }

  function selectCar(id: number | null) {
    setEditingCarId(id);
    const car = cars.find((row) => row.id === id);
    if (!car) {
      setEditingCarActive(true);
      setCarForm({ name: "", description: "", destination: "Tanzania", passengerCapacity: 6, dailyRateUsd: 220, guideDailyRateUsd: 75, imageUrl: "" });
      return;
    }
    setEditingCarActive(car.active !== false);
    setCarForm({
      name: car.name,
      description: car.description,
      destination: car.destination,
      passengerCapacity: car.passengerCapacity,
      dailyRateUsd: car.dailyRateUsd,
      guideDailyRateUsd: car.guideDailyRateUsd,
      imageUrl: car.imageUrl,
    });
  }

  function selectOptionalExperience(id: number | null) {
    setEditingOptionalExperienceId(id);
    const optionalExperience = optionalExperiences.find((row) => row.id === id);
    setOptionalExperienceForm(optionalExperience ? {
      name: optionalExperience.name,
      description: optionalExperience.description,
      preference: optionalExperience.preference,
      price: optionalExperience.price,
      available: optionalExperience.available,
    } : { name: "", description: "", preference: "", price: 0, available: true });
  }

  function submitTourForm(event: FormEvent<HTMLFormElement>) {
    if (editingTourId === null) onCreateTour(event);
    else onUpdateTour(editingTourId, editingTourActive, event);
  }

  function submitCarForm(event: FormEvent<HTMLFormElement>) {
    if (editingCarId === null) onCreateCar(event);
    else onUpdateCar(editingCarId, editingCarActive, event);
  }

  function submitOptionalExperienceForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (editingOptionalExperienceId === null) onCreateOptionalExperience(optionalExperienceForm);
    else onUpdateOptionalExperience(editingOptionalExperienceId, optionalExperienceForm);
  }

  if (!user || user.role !== "ADMIN") {
    return <main className="portal account-page"><section className="panel account-signin">
      <h1>Administrator access required</h1>
      <p>Sign in with an administrator account to manage the safari catalog.</p>
      <Link className="text-link" to="/account">Go to account</Link>
    </section></main>;
  }

  return (
    <main className="portal admin-page">
      <div className="section-head"><div><span className="eyebrow dark">WILDPATH MANAGEMENT</span><h2>Admin <em>dashboard.</em></h2></div><span className="role-tag">ADMINISTRATOR</span></div>
      {notice && <div className="notice" role="status">{notice}</div>}
      <div className="admin-grid">
        <section className="panel">
          <h3>{editingTourId === null ? "Create experience" : "Edit experience"}</h3>
          <form className="stack-form" onSubmit={submitTourForm}>
            <label>Edit existing experience<select value={editingTourId ?? ""} onChange={(event) => selectTour(event.target.value ? Number(event.target.value) : null)}>
              <option value="">Create a new experience</option>
              {tours.map((tour) => <option key={tour.id} value={tour.id}>{tour.name}</option>)}
            </select></label>
            <label>Name<input required maxLength={120} value={tourForm.name} onChange={(event) => setTourForm({ ...tourForm, name: event.target.value })} /></label>
            <label>Description<textarea required maxLength={2000} value={tourForm.description} onChange={(event) => setTourForm({ ...tourForm, description: event.target.value })} /></label>
            <label>Destination<input required maxLength={120} value={tourForm.destination} onChange={(event) => setTourForm({ ...tourForm, destination: event.target.value })} /></label>
            <div className="form-row">
              <label>Duration (days)<input required type="number" min={1} value={tourForm.durationDays} onChange={(event) => setTourForm({ ...tourForm, durationDays: Number(event.target.value) })} /></label>
              <label>Price per person (USD)<input required type="number" min={1} step="0.01" value={tourForm.pricePerPerson} onChange={(event) => setTourForm({ ...tourForm, pricePerPerson: Number(event.target.value) })} /></label>
            </div>
            <label>Image URL<input required type="url" value={tourForm.imageUrl} onChange={(event) => setTourForm({ ...tourForm, imageUrl: event.target.value })} /></label>
            {editingTourId !== null && <label className="checkbox-label"><input type="checkbox" checked={editingTourActive} onChange={(event) => setEditingTourActive(event.target.checked)} /> Published in catalog</label>}
            <button className="primary-btn" type="submit">{editingTourId === null ? "Create experience" : "Save experience"}</button>
          </form>
        </section>
        <section className="panel">
          <h3>{editingOptionalExperienceId === null ? "Create optional experience" : "Edit optional experience"}</h3>
          <form className="stack-form" onSubmit={submitOptionalExperienceForm}>
            <label>Edit existing experience<select value={editingOptionalExperienceId ?? ""} onChange={(event) => selectOptionalExperience(event.target.value ? Number(event.target.value) : null)}>
              <option value="">Create a new experience</option>
              {optionalExperiences.map((optionalExperience) => <option key={optionalExperience.id} value={optionalExperience.id}>{optionalExperience.name}</option>)}
            </select></label>
            <label>Name<input required maxLength={150} value={optionalExperienceForm.name} onChange={(event) => setOptionalExperienceForm({ ...optionalExperienceForm, name: event.target.value })} /></label>
            <label>Description<textarea required maxLength={2000} value={optionalExperienceForm.description} onChange={(event) => setOptionalExperienceForm({ ...optionalExperienceForm, description: event.target.value })} /></label>
            <label>Preference or service requirements<textarea required maxLength={1000} value={optionalExperienceForm.preference} onChange={(event) => setOptionalExperienceForm({ ...optionalExperienceForm, preference: event.target.value })} /></label>
            <label>Price per booking (USD)<input required type="number" min={0} step="0.01" value={optionalExperienceForm.price} onChange={(event) => setOptionalExperienceForm({ ...optionalExperienceForm, price: Number(event.target.value) })} /></label>
            {editingOptionalExperienceId !== null && <label className="checkbox-label"><input type="checkbox" checked={optionalExperienceForm.available} onChange={(event) => setOptionalExperienceForm({ ...optionalExperienceForm, available: event.target.checked })} /> Available to customers</label>}
            <button className="primary-btn" type="submit">{editingOptionalExperienceId === null ? "Create experience" : "Save experience"}</button>
          </form>
          {optionalExperiences.length > 0 && <div className="table-scroll">
            <table><thead><tr><th>Experience</th><th>Price</th><th>Availability</th></tr></thead>
              <tbody>{optionalExperiences.map((optionalExperience) => <tr key={optionalExperience.id}>
                <td>{optionalExperience.name}<small>{optionalExperience.description}</small></td>
                <td>USD {optionalExperience.price.toFixed(2)}</td>
                <td>{optionalExperience.available ? "Available" : "Hidden"} <button type="button" className="text-link" onClick={() => selectOptionalExperience(optionalExperience.id)}>Edit</button></td>
              </tr>)}</tbody>
            </table>
          </div>}
        </section>
        <section className="panel">
          <h3>Add departure</h3>
          <form className="stack-form" onSubmit={onCreateDeparture}>
            <label>Experience<select required value={departureForm.tourId || ""} onChange={(event) => setDepartureForm({ ...departureForm, tourId: Number(event.target.value) })}>
              <option value="" disabled>Select an experience</option>
              {tours.map((tour) => <option key={tour.id} value={tour.id}>{tour.name}</option>)}
            </select></label>
            <label>Departure date<input required type="date" min={new Date().toISOString().slice(0, 10)} value={departureForm.startDate} onChange={(event) => setDepartureForm({ ...departureForm, startDate: event.target.value })} /></label>
            <label>Guest capacity<input required type="number" min={1} value={departureForm.capacity} onChange={(event) => setDepartureForm({ ...departureForm, capacity: Number(event.target.value) })} /></label>
            <button className="primary-btn" type="submit">Add departure</button>
          </form>
        </section>
        <section className="panel">
          <h3>{editingCarId === null ? "Add safari vehicle" : "Edit safari vehicle"}</h3>
          <form className="stack-form" onSubmit={submitCarForm}>
            <label>Edit existing vehicle<select value={editingCarId ?? ""} onChange={(event) => selectCar(event.target.value ? Number(event.target.value) : null)}>
              <option value="">Add a new vehicle</option>
              {cars.map((car) => <option key={car.id} value={car.id}>{car.name}</option>)}
            </select></label>
            <label>Name<input required maxLength={140} value={carForm.name} onChange={(event) => setCarForm({ ...carForm, name: event.target.value })} /></label>
            <label>Description<textarea required maxLength={1000} value={carForm.description} onChange={(event) => setCarForm({ ...carForm, description: event.target.value })} /></label>
            <label>Destination<input required maxLength={180} value={carForm.destination} onChange={(event) => setCarForm({ ...carForm, destination: event.target.value })} /></label>
            <div className="form-row">
              <label>Passenger capacity<input required type="number" min={1} max={30} value={carForm.passengerCapacity} onChange={(event) => setCarForm({ ...carForm, passengerCapacity: Number(event.target.value) })} /></label>
              <label>Daily rate (USD)<input required type="number" min={1} step="0.01" value={carForm.dailyRateUsd} onChange={(event) => setCarForm({ ...carForm, dailyRateUsd: Number(event.target.value) })} /></label>
            </div>
            <label>Guide daily rate (USD)<input required type="number" min={0} step="0.01" value={carForm.guideDailyRateUsd} onChange={(event) => setCarForm({ ...carForm, guideDailyRateUsd: Number(event.target.value) })} /></label>
            <label>Image URL<input required type="url" value={carForm.imageUrl} onChange={(event) => setCarForm({ ...carForm, imageUrl: event.target.value })} /></label>
            {editingCarId !== null && <label className="checkbox-label"><input type="checkbox" checked={editingCarActive} onChange={(event) => setEditingCarActive(event.target.checked)} /> Available in catalog</label>}
            <button className="primary-btn" type="submit">{editingCarId === null ? "Add safari vehicle" : "Save vehicle"}</button>
          </form>
        </section>
        <section className="panel table-panel">
          <h3>Safari vehicles ({cars.length})</h3>
          <div className="table-scroll"><table><thead><tr><th>Vehicle</th><th>Destination</th><th>Passengers</th><th>Daily rate</th></tr></thead>
            <tbody>{cars.map((car) => <tr key={car.id}><td>{car.name}</td><td>{car.destination}</td><td>{car.passengerCapacity}</td><td>USD {car.dailyRateUsd.toLocaleString()}</td></tr>)}</tbody>
          </table></div>
        </section>
        <section className="panel table-panel">
          <h3>Experience bookings</h3>
          {tourBookings.length ? <div className="table-scroll"><table><thead><tr><th>Experience</th><th>Customer</th><th>Departure</th><th>Guests</th><th>Total</th><th>Status</th></tr></thead>
            <tbody>{tourBookings.map((booking) => <tr key={booking.id}>            <td>{booking.tourName}<small>{booking.destination}</small>{booking.optionalExperiences?.map((optionalExperience) => <small key={optionalExperience.optionalExperienceId}>Optional experience: {optionalExperience.name} (USD {optionalExperience.price.toFixed(2)} per booking)</small>)}</td><td>{booking.customerName}<small>{booking.customerEmail}</small></td><td>{new Date(`${booking.startDate}T00:00:00`).toLocaleDateString()}</td><td>{booking.guests}</td><td>{booking.currency} {booking.totalAmount.toLocaleString()}</td><td>{booking.status}{booking.status === "PENDING" && <div className="admin-booking-actions"><button className="text-link" onClick={() => onUpdateBookingStatus(booking.id, "CONFIRMED")}>Confirm</button><button className="text-link" onClick={() => onUpdateBookingStatus(booking.id, "DECLINED")}>Decline</button><button className="text-link" onClick={() => onUpdateBookingStatus(booking.id, "CANCELLED")}>Cancel</button></div>}</td></tr>)}</tbody>
          </table></div> : <p>No experience bookings yet.</p>}
        </section>
        <section className="panel table-panel">
          <h3>Vehicle bookings</h3>
          {carBookings.length ? <div className="table-scroll"><table><thead><tr><th>Vehicle</th><th>Customer</th><th>Dates</th><th>Total</th><th>Status</th></tr></thead>
            <tbody>{carBookings.map((booking) => <tr key={booking.id}><td>{booking.carName}<small>{booking.destination}</small></td><td>{booking.customerName}<small>{booking.customerEmail}</small></td><td>{new Date(`${booking.startDate}T00:00:00`).toLocaleDateString()} – {new Date(`${booking.endDate}T00:00:00`).toLocaleDateString()}</td><td>{booking.currency} {booking.totalAmount.toLocaleString()}</td><td>{booking.status}{(booking.status === "PENDING" || booking.status === "CONFIRMED") && <div className="admin-booking-actions">{booking.status === "PENDING" && <><button className="text-link" onClick={() => onUpdateCarBookingStatus(booking.id, "CONFIRMED")}>Confirm</button><button className="text-link" onClick={() => onUpdateCarBookingStatus(booking.id, "DECLINED")}>Decline</button></>}<button className="text-link" onClick={() => onUpdateCarBookingStatus(booking.id, "CANCELLED")}>Cancel</button></div>}</td></tr>)}</tbody>
          </table></div> : <p>No vehicle bookings yet.</p>}
        </section>
      </div>
    </main>
  );
}
