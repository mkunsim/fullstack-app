import { useEffect, useRef, useState, type FormEvent } from "react";
import * as api from "../api";

type ScenicFooterProps = {
  totalHits: number | null;
  hitCountUnavailable: boolean;
};

export default function ScenicFooter({ totalHits, hitCountUnavailable }: ScenicFooterProps) {
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewSummary, setReviewSummary] = useState<api.ReviewSummary | null>(null);
  const [reviewSummaryLoading, setReviewSummaryLoading] = useState(true);
  const [rating, setRating] = useState<number | null>(null);
  const [reviewText, setReviewText] = useState("");
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewSent, setReviewSent] = useState(false);
  const [reviewError, setReviewError] = useState("");
  const reviewButtonRef = useRef<HTMLButtonElement>(null);
  const firstStarRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let mounted = true;
    api.getReviewSummary()
      .then((summary) => {
        if (mounted) setReviewSummary(summary);
      })
      .catch(() => {})
      .finally(() => {
        if (mounted) setReviewSummaryLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!reviewOpen) return;
    firstStarRef.current?.focus();
  }, [reviewOpen]);

  useEffect(() => {
    if (!reviewOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !reviewBusy) closeReview();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [reviewOpen, reviewBusy]);

  function closeReview() {
    setReviewOpen(false);
    setRating(null);
    setReviewText("");
    setReviewSent(false);
    setReviewError("");
    reviewButtonRef.current?.focus();
  }

  async function submitReview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (reviewBusy || rating === null) return;
    setReviewBusy(true);
    setReviewError("");
    try {
      await api.submitReview({ rating, text: reviewText });
      setReviewSent(true);
      void api.getReviewSummary().then(setReviewSummary).catch(() => {});
    } catch (reason) {
      setReviewError(reason instanceof Error ? reason.message : "Could not send your review. Please try again.");
    } finally {
      setReviewBusy(false);
    }
  }

  const averageRating = reviewSummary?.averageRating ?? 0;
  const starFill = `${Math.min(5, Math.max(0, averageRating)) * 20}%`;
  const ratingDescription = reviewSummary
    ? reviewSummary.reviewCount === 0
      ? "No reviews yet"
      : `${averageRating.toFixed(1)} out of 5 from ${reviewSummary.reviewCount} ${reviewSummary.reviewCount === 1 ? "review" : "reviews"}`
    : "Rating unavailable";

  return (
    <>
      <footer className="scenic-banner" aria-label="Wildpath Safaris footer">
        <div className="scenic-footer-content">
          <div>
            <strong>WILDPATH SAFARIS</strong>
            <span>KENYA · TANZANIA · UGANDA</span>
          </div>
          <button
            ref={reviewButtonRef}
            className="rating-badge"
            type="button"
            onClick={() => setReviewOpen(true)}
            aria-haspopup="dialog"
            aria-label={`${ratingDescription}. Write a review for Safari Online.`}
          >
            <span className="rating-badge-stars" aria-hidden="true">
              <span className="rating-stars-track">★★★★★</span>
              <span className="rating-stars-fill" style={{ width: starFill }}>★★★★★</span>
            </span>
            <span className="rating-badge-score">
              {reviewSummary?.reviewCount
                ? `${averageRating.toFixed(1)} #${reviewSummary.reviewCount}`
                : reviewSummary ? "No reviews" : reviewSummaryLoading ? "Loading" : "Unavailable"}
            </span>
            <span className="rating-badge-reviews">WRITE A REVIEW</span>
          </button>
          <p className="visit-count" aria-live="polite">
            {totalHits === null ? (hitCountUnavailable ? "Visit count unavailable" : "Loading visits…") : (
              <>
                <span className="visit-count-icon" aria-hidden="true">👣</span>
                <strong>{totalHits.toLocaleString()}</strong>
                <span>total visits</span>
              </>
            )}
          </p>
        </div>
      </footer>
      {reviewOpen && (
        <div
          className="backdrop review-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !reviewBusy) closeReview();
          }}
        >
          <section className="review-modal" role="dialog" aria-modal="true" aria-labelledby="review-title">
            <button className="close" type="button" onClick={closeReview} disabled={reviewBusy} aria-label="Close review form">
              <span aria-hidden="true">×</span>
            </button>
            <h2 id="review-title">Safari Online</h2>
            {reviewSent ? (
              <div className="review-success" role="status">
                <p>Thank you for sharing your experience.</p>
                <button className="primary-btn" type="button" onClick={closeReview}>Close</button>
              </div>
            ) : (
              <form className="review-form" onSubmit={(event) => void submitReview(event)}>
                <p className="review-prompt">How was your experience?</p>
                <div className="review-stars" role="group" aria-label="Your rating">
                  {[1, 2, 3, 4, 5].map((value) => (
                    <button
                      key={value}
                      ref={value === 1 ? firstStarRef : undefined}
                      type="button"
                      className={rating !== null && value <= rating ? "selected" : ""}
                      onClick={() => setRating(value)}
                      aria-label={`${value} star${value === 1 ? "" : "s"}`}
                      aria-pressed={rating === value}
                    >
                      ★
                    </button>
                  ))}
                </div>
                <label className="review-label" htmlFor="review-text">Tell us about your experience</label>
                <textarea
                  id="review-text"
                  required
                  maxLength={3000}
                  rows={4}
                  value={reviewText}
                  onChange={(event) => setReviewText(event.target.value)}
                  placeholder="Share what you liked about your safari"
                />
                {reviewError && <p className="review-error" role="alert">{reviewError}</p>}
                <div className="review-actions">
                  <button className="outline-btn" type="button" onClick={closeReview} disabled={reviewBusy}>Cancel</button>
                  <button className="primary-btn" type="submit" disabled={reviewBusy || rating === null}>
                    {reviewBusy ? "Sending…" : "Send"}
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      )}
    </>
  );
}
