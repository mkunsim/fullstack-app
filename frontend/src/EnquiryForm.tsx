import { useState, type FormEvent } from "react";
import * as api from "./api";

interface EnquiryFormProps {
  itemType: "TOUR" | "VEHICLE";
  itemName: string;
}

export default function EnquiryForm({ itemType, itemName }: EnquiryFormProps) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const result = await api.submitEnquiry({ name, email, itemType, itemName, message });
      setSent(true);
      setSuccessMessage(result.message);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not send your enquiry. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={open ? "enquiry-widget enquiry-open" : "enquiry-widget"}>
      {!open ? (
        <p className="enquiry-prompt">
          Bookings require operator confirmation. Sign in later to check your booking status, or{" "}
          <button type="button" className="text-link" onClick={() => setOpen(true)} aria-expanded={false}>
            Enquire first
          </button>.
        </p>
      ) : sent ? (
        <div className="notice" role="status">
          <span>{successMessage}</span>
          <button type="button" className="text-link" onClick={() => { setOpen(false); setSent(false); setSuccessMessage(""); }}>
            Close
          </button>
        </div>
      ) : (
        <>
          <p>Ask us before booking or enquire without creating a reservation.</p>
          <form className="stack-form" onSubmit={(event) => void submit(event)}>
            <label>Your name<input required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} /></label>
            <label>Your email<input required type="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            <label>Your question<textarea required maxLength={3000} rows={4} value={message} onChange={(event) => setMessage(event.target.value)} /></label>
            {error && <p role="alert">{error}</p>}
            <div className="enquiry-actions">
              <button type="submit" className="primary-btn" disabled={busy}>
                {busy ? "Sending enquiry…" : "Send enquiry"}
              </button>
              <button type="button" className="text-link" onClick={() => setOpen(false)} disabled={busy}>
                Cancel
              </button>
            </div>
          </form>
        </>
      )}
    </section>
  );
}
