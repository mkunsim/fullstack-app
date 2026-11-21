import { Link } from "react-router-dom";
import PayNowButton from "./PayNowButton";
import type { Booking, CarBooking, User } from "../types";

interface AccountPageProps {
  user: User | null;
  bookings: Booking[];
  carBookings: CarBooking[];
  notice: string;
  onSignIn: () => void;
  onCancelBooking: (id: number) => void;
  onCancelCarBooking: (id: number) => void;
}

export default function AccountPage({
  user,
  bookings,
  carBookings,
  notice,
  onSignIn,
  onCancelBooking,
  onCancelCarBooking,
}: AccountPageProps) {
  return (
    <main className={`portal account-page${user ? "" : " account-page--signed-out"}`}>
      <div className="section-head">
        <div><span className="eyebrow dark">YOUR WILDPATH</span><h2>My <em>account.</em></h2></div>
        {user && <span className="role-tag">{user.role === "ADMIN" ? "ADMINISTRATOR" : "CUSTOMER"}</span>}
      </div>
      {notice && <div className="notice" role="status">{notice}</div>}
      {!user ? (
        <section className="panel account-signin">
          <h3>Sign in to view your bookings</h3>
          <p>Use your email verification code to access your safari and vehicle reservations.</p>
          <button className="primary-btn" onClick={onSignIn}>Sign in</button>
        </section>
      ) : user.role === "ADMIN" ? (
        <section className="panel account-signin">
          <h3>Administrator account</h3>
          <p>Manage safari tours, vehicles, departures, and booking requests.</p>
          <Link className="text-link" to="/admin">Open admin dashboard</Link>
        </section>
      ) : (
        <div className="account-bookings">
          <section className="panel table-panel">
            <h3>Safari tour bookings</h3>
            {bookings.length ? <div className="table-scroll"><table>
              <thead><tr><th>Safari tour</th><th>Departure</th><th>Guests</th><th>Total</th><th>Booking</th><th>Payment</th></tr></thead>
              <tbody>{bookings.map((booking) => <tr key={booking.id}>
                <td>{booking.tourName}<small>{booking.destination}</small>
                  {booking.addons?.map((addon) => <small key={addon.addonId}>Extra: {addon.name} (USD {addon.price.toFixed(2)} per booking)</small>)}
                </td>
                <td>{new Date(`${booking.startDate}T00:00:00`).toLocaleDateString()}</td>
                <td>{booking.guests}</td>
                <td>{booking.currency} {booking.totalAmount.toLocaleString()}</td>
                <td>{booking.status}{booking.status === "PENDING" && <button className="text-link" onClick={() => onCancelBooking(booking.id)}>Cancel request</button>}</td>
                <td><PayNowButton bookingId={booking.id} bookingStatus={booking.status} paymentStatus={booking.paymentStatus} currency={booking.currency} /><small>{booking.paymentStatus}</small></td>
              </tr>)}</tbody>
            </table></div> : <p>You do not have any safari bookings yet. Browse <Link to="/safari-tours">safari tours</Link> to get started.</p>}
          </section>
          <section className="panel table-panel">
            <h3>Safari vehicles</h3>
            {carBookings.length ? <div className="table-scroll"><table>
              <thead><tr><th>Vehicle</th><th>Dates</th><th>Route</th><th>Passengers</th><th>Total</th><th>Status</th><th>Payment</th></tr></thead>
              <tbody>{carBookings.map((booking) => <tr key={booking.id}>
                <td>{booking.carName}<small>{booking.destination}</small></td>
                <td>{new Date(`${booking.startDate}T00:00:00`).toLocaleDateString()} – {new Date(`${booking.endDate}T00:00:00`).toLocaleDateString()}</td>
                <td>{booking.pickupLocation}<small>to {booking.dropoffLocation}</small></td>
                <td>{booking.passengers}</td>
                <td>{booking.currency} {booking.totalAmount.toLocaleString()}</td>
                <td>{booking.status}{booking.status === "PENDING" && <button className="text-link" onClick={() => onCancelCarBooking(booking.id)}>Cancel request</button>}</td>
                <td><PayNowButton bookingId={booking.id} bookingType="car" bookingStatus={booking.status} paymentStatus={booking.paymentStatus} currency={booking.currency} /><small>{booking.paymentStatus}</small></td>
              </tr>)}</tbody>
            </table></div> : <p>You do not have any vehicle bookings yet. Browse <Link to="/safari-cars">safari cars</Link> to get started.</p>}
          </section>
        </div>
      )}
    </main>
  );
}
