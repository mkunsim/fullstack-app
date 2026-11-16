import { useState } from "react";
import * as api from "./api";

interface PayNowButtonProps {
  bookingId: number;
  bookingStatus: string;
  paymentStatus: string;
  currency: string;
  bookingType?: "tour" | "car";
}

type CompleteCheckoutQuote = api.CheckoutResponse & {
  checkoutUrl: string;
  paymentAmount: number;
  paymentCurrency: "KES";
  exchangeRate: number;
  quoteExpiresAt: string;
};

function isCompleteQuote(result: api.CheckoutResponse): result is CompleteCheckoutQuote {
  return typeof result.checkoutUrl === "string"
    && result.checkoutUrl.length > 0
    && typeof result.paymentAmount === "number"
    && result.paymentCurrency === "KES"
    && typeof result.exchangeRate === "number"
    && typeof result.quoteExpiresAt === "string"
    && result.quoteExpiresAt.length > 0
    && Number.isFinite(Date.parse(result.quoteExpiresAt));
}

function formatKes(amount: number): string {
  return new Intl.NumberFormat("en-KE", {
    style: "currency",
    currency: "KES",
    minimumFractionDigits: 2,
  }).format(amount);
}

export default function PayNowButton({
  bookingId,
  bookingStatus,
  paymentStatus,
  currency,
  bookingType = "tour",
}: PayNowButtonProps) {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [quote, setQuote] = useState<CompleteCheckoutQuote | null>(null);

  const canPay =
    bookingStatus.toUpperCase() === "CONFIRMED" &&
    paymentStatus.toUpperCase() !== "PAID" &&
    paymentStatus.toUpperCase() !== "REFUNDED" &&
    currency.toUpperCase() === "USD";

  async function payNow() {
    if (!canPay || loading) return;
    setLoading(true);
    setMessage("");
    try {
      const result = bookingType === "car"
        ? await api.createCarCheckout(bookingId)
        : await api.createCheckout(bookingId);
      if (result.status.toUpperCase() === "PAID") {
        setMessage("Payment received. Your booking has already been paid.");
      } else if (!isCompleteQuote(result)) {
        throw new Error("The payment provider did not return a complete KES quote.");
      } else {
        setQuote(result);
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Unable to prepare payment. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  if (paymentStatus.toUpperCase() === "PAID") {
    return <p>Payment received. Thank you!</p>;
  }

  return (
    <div>
      {!quote ? (
        <button type="button" onClick={() => void payNow()} disabled={!canPay || loading}>
          {loading ? "Preparing payment…" : !canPay ? "Not Available" : "Pay in KES"}
        </button>
      ) : (
        <div className="payment-quote" role="status">
          <strong>{formatKes(quote.paymentAmount)}</strong>
          <small>
            Exchange rate: 1 USD = {quote.exchangeRate.toFixed(2)} KES
            {quote.exchangeRateSource ? ` (${quote.exchangeRateSource})` : ""}
          </small>
          <small>Quote expires {new Date(quote.quoteExpiresAt).toLocaleString()}.</small>
          <button type="button" onClick={() => window.location.assign(quote.checkoutUrl)}>
            Continue to Paystack (KES)
          </button>
        </div>
      )}
      {message && <p role="alert">{message}</p>}
    </div>
  );
}
