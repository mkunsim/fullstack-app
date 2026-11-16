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
          <Link className="primary-btn" to="/safari-tours">
            Explore Tours <ArrowRight size={16} />
          </Link>
        </div>

      </section>

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

    </main>
  );
}
