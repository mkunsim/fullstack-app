import { ArrowRight, Clock3, Heart, MapPin, ShieldCheck, Sparkles, Users } from "lucide-react";
import { Link } from "react-router-dom";
import type { Tour } from "../types";

type HomePageProps = {
  tours: Tour[];
  loading: boolean;
};

const safariImages = [
  "https://images.unsplash.com/photo-1516426122078-c23e76319801?auto=format&fit=crop&w=900&q=80",
  "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=900&q=80",
  "https://images.unsplash.com/photo-1547036967-23d11aacaee0?auto=format&fit=crop&w=900&q=80",
];

const promisePoints = [
  { icon: Sparkles, title: "Expert Local Guides", subtitle: "Trusted and experienced" },
  { icon: ShieldCheck, title: "Safe & Responsible", subtitle: "Sustainable travel" },
  { icon: Heart, title: "Unforgettable Experiences", subtitle: "Real connections" },
  { icon: Users, title: "24/7 Support", subtitle: "Always here to help" },
];

const heroImage = {
  src: "https://images.unsplash.com/photo-1547036967-23d11aacaee0?auto=format&fit=crop&w=2200&q=80",
  alt: "Elephants in the savanna during golden hour",
};

export default function HomePage({ tours, loading }: HomePageProps) {
  const featuredTours = tours.slice(0, 3);
  const destinations = new Map<string, { tour: Tour; count: number }>();
  for (const tour of tours) {
    const destination = tour.destination.trim();
    if (!destination) continue;
    const current = destinations.get(destination);
    if (current) current.count += 1;
    else destinations.set(destination, { tour, count: 1 });
  }
  const destinationHighlights = [...destinations.entries()]
    .sort((left, right) => right[1].count - left[1].count)
    .slice(0, 3);

  return (
    <main className="homepage-shell">
      <section className="hero">
        <img
          src={heroImage.src}
          alt={heroImage.alt}
        />
        <div className="hero-overlay" />

        <div className="hero-content">
          <span className="eyebrow">Curated East African safari tours</span>
          <h1>
            Choose your
            <em>adventure.</em>
          </h1>
          <p>Unforgettable safari experiences across Kenya, Tanzania and Uganda.</p>
          <div className="hero-actions">
            <Link className="primary-btn" to="/safari-tours">
              Explore Safari Tours <ArrowRight size={16} />
            </Link>
            <Link className="secondary-btn hero-secondary-btn" to="/safari-cars">
              Browse Safari Vehicles <ArrowRight size={16} />
            </Link>
          </div>
        </div>
      </section>

      <section className="promise-grid" id="promise" aria-label="Our promise">
        {promisePoints.map(({ icon: Icon, title, subtitle }) => (
          <div key={title} className="promise-item">
            <span className="promise-icon">
              <Icon size={18} />
            </span>
            <div>
              <strong>{title}</strong>
              <small>{subtitle}</small>
            </div>
          </div>
        ))}
      </section>

      {destinationHighlights.length > 0 && (
        <section className="destination-highlights">
          <div className="section-head">
            <div>
              <span className="eyebrow dark">EXPLORE EAST AFRICA</span>
              <h2>Choose your <em>destination.</em></h2>
            </div>
            <p>Start with a place that inspires you. Find a safari that fits your journey.</p>
          </div>
          <div className="destination-grid">
            {destinationHighlights.map(([destination, highlight], index) => (
              <Link
                key={destination}
                className="destination-card"
                to={`/safari-tours?destination=${encodeURIComponent(destination)}`}
                aria-label={`Explore ${highlight.count} safari tours in ${destination}`}
              >
                <img
                  src={highlight.tour.imageUrl || safariImages[index % safariImages.length]}
                  alt=""
                />
                <div className="destination-card-content">
                  <span>{highlight.count} safari {highlight.count === 1 ? "tour" : "tours"}</span>
                  <h3>{destination}</h3>
                  <span className="destination-card-cta">Explore journeys <ArrowRight size={15} /></span>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section className="featured-safaris">
        <div className="featured-copy">
          <span className="eyebrow eyebrow-dark">Featured safaris</span>
          <h2>
            Extraordinary
            <em>Journeys Await</em>
          </h2>
          <p>Discover our handpicked safari tours, designed for unforgettable experiences in the heart of Africa.</p>
          <Link className="secondary-btn" to="/safari-tours">
            View All Tours <ArrowRight size={16} />
          </Link>
        </div>

        <div className="safari-card-grid">
          {loading ? (
            <p className="featured-message" role="status">Loading featured safaris…</p>
          ) : featuredTours.length > 0 ? (
            featuredTours.map((tour, index) => (
              <article key={tour.id} className="safari-card">
                <Link className="safari-card-link" to={`/safari-tours/${tour.id}`} aria-label={`View ${tour.name}`}>
                  <div className="safari-image">
                    <img src={tour.imageUrl || safariImages[index % safariImages.length]} alt={tour.name} />
                    <span className="safari-location">
                      <MapPin size={12} />
                      {tour.destination}
                    </span>
                    <span className="safari-favorite" aria-hidden="true">
                      <Heart size={15} />
                    </span>
                  </div>

                  <div className="safari-body">
                    <h3>{tour.name}</h3>
                    <p>{tour.description}</p>

                    <div className="safari-meta">
                      <span><Clock3 size={12} /> {tour.durationDays} days</span>
                      <span className="price-block">
                        <strong>${tour.pricePerPerson.toLocaleString()}</strong>
                        <small>per person</small>
                      </span>
                    </div>
                  </div>
                </Link>
              </article>
            ))
          ) : (
            <p className="featured-message">No safaris are available yet. Check back soon or browse all tours.</p>
          )}
        </div>
      </section>
    </main>
  );
}
