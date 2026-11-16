
import { useEffect, useState } from "react";

interface BookingDetails {
  bookingId: number;
  tourName: string;
  destination: string;
  departureDate: string;
  durationDays: number;
  guests: number;
  totalAmount: number;
  currency: string;
  status: string;
  paymentStatus: string;
  customerName: string;
  customerEmail: string;
  requestedAt: string;
}

export default function Operator() {
  const params = new URLSearchParams(window.location.search);
  const token = params.get("token");
  const action = params.get("action");

  const [details, setDetails] = useState<BookingDetails | null>(null);
  const [message, setMessage] = useState("");
  const [loadingDetails, setLoadingDetails] = useState(true);
  const [loading, setLoading] = useState(false);

  const validLink =
    Boolean(token) &&
    (action === "confirm" || action === "decline");

  useEffect(() => {
    if (!validLink || !token) {
      setLoadingDetails(false);
      return;
    }

    let cancelled = false;

    async function loadDetails() {
      try {
        const response = await fetch(
          `/api/operator/booking-action/details?token=${encodeURIComponent(token!)}`
        );

        if (!response.ok) {
          throw new Error("Could not load booking details.");
        }

        const data: BookingDetails = await response.json();

        if (!cancelled) {
          setDetails(data);
        }
      } catch {
        if (!cancelled) {
          setMessage(
            "Could not load booking details. The link may have expired, or the server may be unavailable."
          );
        }
      } finally {
        if (!cancelled) {
          setLoadingDetails(false);
        }
      }
    }

    loadDetails();

    return () => {
      cancelled = true;
    };
  }, [token, validLink]);

  async function submitAction() {
    if (!token || !validLink || !details) {
      setMessage("The booking details could not be verified.");
      return;
    }

    setLoading(true);
    setMessage("");

    try {
      const response = await fetch(
        `/api/operator/booking-action?token=${encodeURIComponent(token)}&action=${encodeURIComponent(action!)}`,
        { method: "POST" }
      );

      const result = await response.text();

      if (!response.ok) {
        throw new Error(result || "The booking action failed.");
      }

      setMessage(result);
      setDetails((previous) =>
        previous
          ? {
              ...previous,
              status: action === "confirm" ? "CONFIRMED" : "DECLINED",
            }
          : previous
      );
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Could not contact the server. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }

  if (!validLink) {
    return (
      <main style={styles.container}>
        <h1>Wildpath Safaris</h1>
        <p>This booking action link is invalid.</p>
      </main>
    );
  }

  return (
    <main style={styles.container}>
      <h1>Wildpath Safaris</h1>
      <h2>Booking Request</h2>

      {loadingDetails ? (
        <p>Loading booking details...</p>
      ) : details ? (
        <>
          <section style={styles.card}>
            <h3>Tour Details</h3>
            <p><strong>Booking ID:</strong> {details.bookingId}</p>
            <p><strong>Tour:</strong> {details.tourName}</p>
            <p><strong>Destination:</strong> {details.destination}</p>
            <p>
              <strong>Departure date:</strong>{" "}
              {details.departureDate
                ? new Date(
                    `${details.departureDate.slice(0, 10)}T00:00:00`
                  ).toLocaleDateString()
                : "Not specified"}
            </p>
            <p>
              <strong>Duration:</strong>{" "}
              {details.durationDays} day(s)
            </p>
            <p><strong>Guests:</strong> {details.guests}</p>
            <p>
              <strong>Total:</strong>{" "}
              {details.currency}{" "}
              {Number(details.totalAmount).toLocaleString()}
            </p>
            <p><strong>Booking status:</strong> {details.status}</p>
            <p><strong>Payment status:</strong> {details.paymentStatus}</p>
          </section>

          <section style={styles.card}>
            <h3>Customer Details</h3>
            <p><strong>Name:</strong> {details.customerName}</p>
            <p><strong>Email:</strong> {details.customerEmail}</p>
          </section>

          {details.status === "PENDING" ? (
            <>
              <p>
                {action === "confirm"
                  ? "Review the details above and confirm that the tour is available."
                  : "Review the details above before declining this booking request."}
              </p>

              <button
                onClick={submitAction}
                disabled={loading || loadingDetails}
                style={{
                  ...styles.button,
                  backgroundColor:
                    action === "confirm" ? "#276749" : "#b83232",
                }}
              >
                {loading
                  ? "Processing..."
                  : action === "confirm"
                    ? "Confirm Availability"
                    : "Decline Request"}
              </button>
            </>
          ) : (
            <p>This booking has already been processed or is no longer pending.</p>
          )}
        </>
      ) : (
        <p>
          {message || "Booking details are unavailable."}
        </p>
      )}

      {message && details && (
        <p role="status" style={styles.message}>
          {message}
        </p>
      )}
    </main>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 600,
    margin: "40px auto",
    padding: 24,
    fontFamily: "Arial, sans-serif",
    color: "#243024",
  },
  card: {
    border: "1px solid #d9e2d9",
    borderRadius: 10,
    padding: 20,
    marginBottom: 20,
    backgroundColor: "#f8faf7",
  },
  button: {
    border: "none",
    borderRadius: 6,
    color: "#fff",
    padding: "12px 20px",
    fontSize: 16,
    cursor: "pointer",
  },
  message: {
    marginTop: 20,
    padding: 12,
    backgroundColor: "#edf5ed",
    borderRadius: 6,
  },
};
