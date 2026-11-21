import { useEffect, useRef, useState } from "react";
import { ArrowLeft, Compass, LogIn, LogOut, Menu, MessageCircle, X } from "lucide-react";
import { Link, NavLink, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import * as api from "./api";
import AccountPage from "./components/AccountPage";
import AdminPage, { type AdminCarForm, type AdminDepartureForm, type AdminTourForm } from "./components/AdminPage";
import CarDetailsPage, { type CarReservation } from "./components/CarDetailsPage";
import HomePage from "./components/HomePage";
import Operator from "./components/Operator";
import SafariCarsPage from "./components/SafariCarsPage";
import SafariToursPage from "./components/SafariToursPage";
import ScenicFooter from "./components/ScenicFooter";
import TourDetailsPage from "./components/TourDetailsPage";
import type { AddOn, AdminBooking, Booking, CarBooking, SafariCar, Tour, User } from "./types";

const SITE_ORIGIN = "https://safaritours.dpdns.org";
const WHATSAPP_URL = `https://wa.me/254113098156?text=${encodeURIComponent("Hi, I'm interested in Wildpaths safaris.")}`;

function setMetaTag(attribute: "name" | "property", key: string, content: string) {
  const selector = `meta[${attribute}="${key}"]`;
  let meta = document.head.querySelector<HTMLMetaElement>(selector);
  if (!meta) {
    meta = document.createElement("meta");
    meta.setAttribute(attribute, key);
    document.head.appendChild(meta);
  }
  meta.content = content;
}

function summarize(description: string) {
  const normalized = description.replace(/\s+/g, " ").trim();
  return normalized.length > 157 ? `${normalized.slice(0, 154).trimEnd()}...` : normalized;
}

export default function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [tours, setTours] = useState<Tour[]>([]);
  const [cars, setCars] = useState<SafariCar[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [totalHits, setTotalHits] = useState<number | null>(null);
  const [hitCountUnavailable, setHitCountUnavailable] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [authMode, setAuthMode] = useState(false);
  const [authForm, setAuthForm] = useState({ email: "", code: "", step: "email" as "email" | "code" });
  const [authBusy, setAuthBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [myTrips, setMyTrips] = useState<Booking[]>([]);
  const [myCarTrips, setMyCarTrips] = useState<CarBooking[]>([]);
  const [adminRows, setAdminRows] = useState<AdminBooking[]>([]);
  const [adminTourRows, setAdminTourRows] = useState<Tour[]>([]);
  const [adminAddOnRows, setAdminAddOnRows] = useState<AddOn[]>([]);
  const [adminFleetRows, setAdminFleetRows] = useState<SafariCar[]>([]);
  const [adminCarRows, setAdminCarRows] = useState<CarBooking[]>([]);
  const [addonForm, setAddOnForm] = useState({ name: "", description: "", preference: "", price: 0, available: true });
  const [editingAddOnId, setEditingAddOnId] = useState<number | null>(null);
  const [tourForm, setTourForm] = useState<AdminTourForm>({ name: "", description: "", destination: "", durationDays: 3, pricePerPerson: 990, imageUrl: "" });
  const [departureForm, setDepartureForm] = useState<AdminDepartureForm>({ tourId: 0, startDate: "", capacity: 10 });
  const [adminCarForm, setAdminCarForm] = useState<AdminCarForm>({ name: "", description: "", destination: "Tanzania", passengerCapacity: 6, dailyRateUsd: 220, guideDailyRateUsd: 75, imageUrl: "" });
  const paymentVerificationStarted = useRef(false);
  const lastRecordedHitPath = useRef<string | null>(null);

  async function refresh() {
    setLoading(true);
    try {
      const [tourRows, carRows] = await Promise.all([api.getTours(), api.getCars()]);
      setTours(tourRows);
      setCars(carRows);
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not load tours or cars");
    } finally {
      setLoading(false);
    }
  }

  async function loadAccount(account: User) {
    try {
      if (account.role === "ADMIN") {
        const [bookingRows, tourRows, addonRows, carRows, carBookingRows] = await Promise.all([
          api.adminBookings(),
          api.adminTours(),
          api.adminAddons(),
          api.adminCars(),
          api.adminCarBookings(),
        ]);
        setAdminRows(bookingRows);
        setAdminTourRows(tourRows);
        setAdminAddOnRows(addonRows);
        setAdminFleetRows(carRows);
        setAdminCarRows(carBookingRows);
      } else {
        const [tourRows, carBookingRows] = await Promise.all([
          api.myBookings(),
          api.myCarBookings(),
        ]);
        setMyTrips(tourRows);
        setMyCarTrips(carBookingRows);
      }
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not load account");
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  useEffect(() => {
    const path = location.pathname.replace(/\/+$/, "") || "/";
    let title = "Wildpath Safaris | Find your wild";
    let description = "Discover safari tours and vehicle hire across East Africa with Wildpath Safaris.";
    let indexable = false;

    if (path === "/") {
      description = "Explore East African safari tours and vehicle hire. Discover destinations, compare trips, and plan your next adventure with Wildpath Safaris.";
      indexable = true;
    } else if (path === "/safari-tours") {
      title = "Safari Tours in East Africa | Wildpath Safaris";
      description = "Explore East African safari tours, destinations, trip lengths, and upcoming departures with Wildpath Safaris.";
      indexable = true;
    } else if (path === "/safari-cars") {
      title = "Safari Vehicle Hire in East Africa | Wildpath Safaris";
      description = "Compare safari vehicles for hire across East Africa, including passenger capacity, destinations, and daily rates.";
      indexable = true;
    } else {
      const tourMatch = path.match(/^\/safari-tours\/(\d+)$/);
      const carMatch = path.match(/^\/safari-cars\/(\d+)$/);
      const tour = tourMatch ? tours.find((item) => item.id === Number(tourMatch[1])) : undefined;
      const car = carMatch ? cars.find((item) => item.id === Number(carMatch[1])) : undefined;

      if (tourMatch) {
        if (tour) {
          title = `${tour.name} in ${tour.destination} | Wildpath Safaris`;
          description = summarize(tour.description);
          indexable = true;
        } else if (loading) {
          title = "Safari Tours in East Africa | Wildpath Safaris";
          description = "Discover East African safari destinations and find a tour that suits your trip.";
          indexable = true;
        }
      } else if (carMatch) {
        if (car) {
          title = `${car.name} Hire in ${car.destination} | Wildpath Safaris`;
          description = summarize(car.description);
          indexable = true;
        } else if (loading) {
          title = "Safari Vehicle Hire in East Africa | Wildpath Safaris";
          description = "Explore safari vehicles for hire across East Africa with Wildpath Safaris.";
          indexable = true;
        }
      } else if (path !== "/account" && path !== "/admin" &&
                 path !== "/payment/callback" && path !== "/operator/booking-action") {
        title = "Page not found | Wildpath Safaris";
      }
    }

    document.title = title;
    setMetaTag("name", "description", description);
    setMetaTag("name", "robots", indexable ? "index,follow" : "noindex,nofollow");
    setMetaTag("property", "og:title", title);
    setMetaTag("property", "og:description", description);
    setMetaTag("property", "og:url", `${SITE_ORIGIN}${path}`);

    let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (indexable) {
      if (!canonical) {
        canonical = document.createElement("link");
        canonical.rel = "canonical";
        document.head.appendChild(canonical);
      }
      canonical.href = `${SITE_ORIGIN}${path}`;
    } else {
      canonical?.remove();
    }
  }, [cars, location.pathname, tours]);

  useEffect(() => {
    void api.currentUser().then(setUser).catch((reason: unknown) => {
      if (!(reason instanceof Error) || !reason.message.includes("(401)")) {
        setNotice(reason instanceof Error ? reason.message : "Could not restore your sign-in session.");
      }
    });
  }, []);

  useEffect(() => {
    const pagePath = location.pathname;
    if (lastRecordedHitPath.current === pagePath) return;
    lastRecordedHitPath.current = pagePath;
    void api.recordSiteHit(pagePath)
      .then(({ totalCount }) => setTotalHits((current) => Math.max(current ?? 0, totalCount)))
      .catch(() => setHitCountUnavailable(true));
  }, [location.pathname]);

  useEffect(() => {
    if (user) void loadAccount(user);
  }, [user]);

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const reference = params.get("reference") || params.get("trxref");
    if (!reference || !user || paymentVerificationStarted.current) return;
    const account = user;
    const paymentReference = reference;
    paymentVerificationStarted.current = true;

    async function verifyReturnedPayment() {
      try {
        setNotice("Verifying your Paystack payment...");
        await api.verifyPayment(paymentReference);
        setNotice("Payment successful! Your booking is now paid.");
        await loadAccount(account);
        navigate(location.pathname, { replace: true });
      } catch (reason) {
        paymentVerificationStarted.current = false;
        setNotice(reason instanceof Error ? reason.message : "Could not verify payment.");
      }
    }

    void verifyReturnedPayment();
  }, [location.pathname, location.search, navigate, user]);

  function saveUser(account: User) {
    setUser(account);
    setAuthMode(false);
    setNotice(`Welcome, ${account.fullName}`);
    void loadAccount(account);
  }

  async function submitAuth(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthBusy(true);
    try {
      if (authForm.step === "email") {
        await api.sendCode(authForm.email);
        setAuthForm({ ...authForm, step: "code" });
        setNotice("A verification code has been sent if email delivery is configured.");
      } else {
        saveUser(await api.verifyCode(authForm.email, authForm.code));
      }
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Sign-in failed");
    } finally {
      setAuthBusy(false);
    }
  }

  async function submitBooking(_tour: Tour, departureId: number, guests: number, customerName: string, customerEmail: string, addonIds: number[]) {
    try {
      const booking = await api.createBooking(departureId, guests, customerName, customerEmail, addonIds);
      setNotice(`Booking request #${booking.id} saved. Sign in later with an email code to view bookings.`);
      await refresh();
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Booking failed");
    }
  }

  async function submitCarBooking(reservation: CarReservation) {
    try {
      const booking = await api.createCarBooking({
        carId: reservation.carId,
        startDate: reservation.startDate,
        endDate: reservation.endDate,
        pickupLocation: reservation.pickupLocation,
        dropoffLocation: reservation.dropoffLocation,
        passengers: reservation.passengers,
        guideIncluded: reservation.guideIncluded,
        customerName: reservation.fullName,
        customerEmail: reservation.email,
      });
      setNotice(`Car booking request #${booking.id} saved. Driver included; ${booking.guideIncluded ? "guide added" : "no guide added"}. You can verify your email later to view bookings.`);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Car booking failed");
    }
  }

  async function submitTour(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    try {
      await api.createTour(tourForm);
      setNotice("Tour created.");
      setTourForm({ name: "", description: "", destination: "", durationDays: 3, pricePerPerson: 990, imageUrl: "" });
      await refresh();
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not create tour");
    }
  }

  async function updateTour(id: number, active: boolean, event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    try {
      await api.updateTour(id, { ...tourForm, active });
      setNotice("Experience updated.");
      await refresh();
      await loadAccount(user);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not update experience");
    }
  }

  async function submitDeparture(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    try {
      await api.addDeparture(departureForm.tourId, departureForm.startDate, departureForm.capacity);
      setNotice("Departure created.");
      await refresh();
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not create departure");
    }
  }

  async function submitCar(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    try {
      await api.createCar(adminCarForm);
      setNotice("Safari car added to the fleet.");
      setAdminCarForm({ name: "", description: "", destination: "Tanzania", passengerCapacity: 6, dailyRateUsd: 220, guideDailyRateUsd: 75, imageUrl: "" });
      await refresh();
      await loadAccount(user);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not create safari car");
    }
  }

  async function updateCar(id: number, active: boolean, event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    try {
      await api.updateCar(id, { ...adminCarForm, active });
      setNotice("Safari vehicle updated.");
      await refresh();
      await loadAccount(user);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not update safari vehicle");
    }
  }

  async function updateTourBooking(id: number, status: "CONFIRMED" | "CANCELLED" | "DECLINED") {
    if (!user) return;
    try {
      await api.updateBookingStatus(id, status);
      setNotice(`Tour booking ${status.toLowerCase()}.`);
      await loadAccount(user);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not update tour booking");
    }
  }

  async function updateVehicleBooking(id: number, status: "CONFIRMED" | "CANCELLED" | "DECLINED") {
    if (!user) return;
    try {
      await api.updateCarBookingStatus(id, status);
      setNotice(`Vehicle booking ${status.toLowerCase()}.`);
      await loadAccount(user);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not update vehicle booking");
    }
  }

  async function cancelTourBooking(id: number) {
    if (!user || !window.confirm("Cancel this pending safari booking request?")) return;
    try {
      await api.cancelMyBooking(id);
      setNotice("Safari booking request cancelled.");
      await loadAccount(user);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not cancel safari booking");
    }
  }

  async function cancelVehicleBooking(id: number) {
    if (!user || !window.confirm("Cancel this pending vehicle booking request?")) return;
    try {
      await api.cancelMyCarBooking(id);
      setNotice("Vehicle booking request cancelled.");
      await loadAccount(user);
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not cancel vehicle booking");
    }
  }

  async function signOut() {
    try {
      await api.logout();
      setNotice("You have signed out.");
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "Could not invalidate your sign-in session.");
    } finally {
      setUser(null);
      setMyTrips([]);
      setMyCarTrips([]);
      setAdminRows([]);
      setAdminCarRows([]);
      setAdminAddOnRows([]);
      navigate("/");
    }
  }

  function openSignIn() {
    setAuthForm({ email: "", code: "", step: "email" });
    setAuthMode(true);
  }

  const operatorAction = location.pathname === "/operator/booking-action";
  const showBackButton = !operatorAction;
  const historyIndex = window.history.state?.idx;
  const canGoBack = typeof historyIndex === "number" && historyIndex > 0;

  function goBack() {
    if (canGoBack) navigate(-1);
  }

  return (
    <div className="app">
      {operatorAction ? <Operator /> : <>
        <header className={showBackButton ? "header header-with-back" : "header"}>
          <div className="nav">
            <div className="header-brand-group">
              {showBackButton && (
                <button className="header-back-button" type="button" onClick={goBack} disabled={!canGoBack} aria-label="Back" title={canGoBack ? "Back" : "No previous page"}>
                  <ArrowLeft size={18} />
                  <span>Back</span>
                </button>
              )}
              <Link className="brand brand-mark" to="/"><span className="brand-icon"><Compass /></span><span>WILDPATH <small>SAFARIS</small></span></Link>
            </div>
            <button
              className="menu-toggle"
              type="button"
              aria-label={menuOpen ? "Close navigation menu" : "Open navigation menu"}
              aria-expanded={menuOpen}
              aria-controls="main-navigation"
              onClick={() => setMenuOpen((isOpen) => !isOpen)}
            >
              {menuOpen ? <X size={18} /> : <Menu size={18} />}
            </button>
            <nav id="main-navigation" className={menuOpen ? "site-menu menu-open" : "site-menu"} aria-label="Main navigation">
              <NavLink to="/" end onClick={() => setMenuOpen(false)}>Home</NavLink>
              <NavLink to="/safari-tours" onClick={() => setMenuOpen(false)}>Safari tours</NavLink>
              <NavLink to="/safari-cars" onClick={() => setMenuOpen(false)}>Safari cars</NavLink>
              <a href="/#promise" onClick={() => setMenuOpen(false)}>Our promise</a>
              <NavLink to="/account" onClick={() => setMenuOpen(false)}>My account</NavLink>
              {user?.role === "ADMIN" && <NavLink to="/admin" onClick={() => setMenuOpen(false)}>Admin</NavLink>}
            </nav>
            <div className="header-actions">
              {user ? <><span className="signed-in">{user.fullName.split(" ")[0]}</span><button className="outline-btn" onClick={() => void signOut()}><LogOut size={15} /> Sign out</button></> :
                <button className="outline-btn" onClick={openSignIn}><LogIn size={15} /> Sign in</button>}
            </div>
          </div>
        </header>
        {notice && <div className="site-notice" role="status"><span>{notice}</span><button onClick={() => setNotice("")} aria-label="Dismiss notification"><X size={16} /></button></div>}
        <Routes>
          <Route path="/" element={<HomePage tours={tours} loading={loading} />} />
          <Route path="/safari-tours" element={<SafariToursPage tours={tours} loading={loading} error={error} notice="" dismissNotice={() => setNotice("")} />} />
          <Route path="/safari-tours/:tourId" element={<TourDetailsPage tours={tours} loading={loading} onBook={submitBooking} />} />
          <Route path="/safari-cars" element={<SafariCarsPage cars={cars} loading={loading} />} />
          <Route path="/safari-cars/:carId" element={<CarDetailsPage cars={cars} loading={loading} onBook={submitCarBooking} />} />
          <Route path="/payment/callback" element={<main className="detail-page payment-callback">
            <span className="eyebrow dark">PAYMENT RETURN</span>
            <h1>Payment update</h1>
            <p role="status">{notice || (user ? "Checking your payment status…" : "Sign in to verify and view your payment.")}</p>
            <Link className="text-link" to="/account">Go to my account</Link>
          </main>} />
          <Route path="/account" element={<AccountPage user={user} bookings={myTrips} carBookings={myCarTrips} notice="" onSignIn={openSignIn} onCancelBooking={(id) => void cancelTourBooking(id)} onCancelCarBooking={(id) => void cancelVehicleBooking(id)} />} />
          <Route path="/admin" element={<AdminPage
            user={user}
            tours={adminTourRows}
            addons={adminAddOnRows}
            cars={adminFleetRows}
            tourBookings={adminRows}
            carBookings={adminCarRows}
            tourForm={tourForm}
            setTourForm={setTourForm}
            departureForm={departureForm}
            setDepartureForm={setDepartureForm}
            carForm={adminCarForm}
            setCarForm={setAdminCarForm}
            onCreateTour={(event) => void submitTour(event)}
            onUpdateTour={(id, active, event) => void updateTour(id, active, event)}
            onCreateDeparture={(event) => void submitDeparture(event)}
            onCreateCar={(event) => void submitCar(event)}
            onUpdateCar={(id, active, event) => void updateCar(id, active, event)}
            onUpdateBookingStatus={(id, status) => void updateTourBooking(id, status)}
            onUpdateCarBookingStatus={(id, status) => void updateVehicleBooking(id, status)}
            addonForm={addonForm}
            setAddOnForm={setAddOnForm}
            editingAddOnId={editingAddOnId}
            setEditingAddOnId={setEditingAddOnId}
            onCreateAddOn={async (input) => {
              if (!user) return;
              try {
                await api.createAddOn(input);
                setNotice("Extra created.");
                await loadAccount(user);
              } catch (reason) {
                setNotice(reason instanceof Error ? reason.message : "Could not create add-on");
              }
            }}
            onUpdateAddOn={async (id, input) => {
              if (!user) return;
              try {
                await api.updateAddOn(id, input);
                setNotice("Extra updated.");
                await loadAccount(user);
              } catch (reason) {
                setNotice(reason instanceof Error ? reason.message : "Could not update add-on");
              }
            }}
            notice=""
          />} />
          <Route path="/operator/booking-action" element={<Operator />} />
          <Route path="*" element={<main className="detail-page"><h1>Page not found</h1><Link to="/">Return home</Link></main>} />
        </Routes>
        {authMode && <div className="backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setAuthMode(false); }}>
          <section className="modal" role="dialog" aria-modal="true" aria-labelledby="signin-title">
            <button className="close" onClick={() => setAuthMode(false)} aria-label="Close sign in"><X size={20} /></button>
            <span className="eyebrow dark">WELCOME TO WILDPATH</span>
            <h2 id="signin-title">Your next adventure.</h2>
            <p>Sign in with a one-time email code to access your bookings and admin tools.</p>
            <form className="stack-form" onSubmit={(event) => void submitAuth(event)}>
              <label>Email address<input required type="email" value={authForm.email} onChange={(event) => setAuthForm({ ...authForm, email: event.target.value })} /></label>
              {authForm.step === "code" && <label>Verification code<input required inputMode="numeric" value={authForm.code} onChange={(event) => setAuthForm({ ...authForm, code: event.target.value })} /></label>}
              <button className="primary-btn" type="submit" disabled={authBusy}>{authBusy ? "Please wait…" : authForm.step === "code" ? "Verify and sign in" : "Send verification code"}</button>
            </form>
            {authForm.step === "code" && <button className="switch-auth" onClick={() => setAuthForm({ ...authForm, step: "email", code: "" })}>Use a different email</button>}
          </section>
        </div>}
      </>}
      {!operatorAction && (
        <a
          className="whatsapp-button"
          href={WHATSAPP_URL}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Chat with Wildpath Safaris on WhatsApp"
        >
          <MessageCircle size={20} aria-hidden="true" />
          <span>WhatsApp</span>
        </a>
      )}
      <ScenicFooter totalHits={totalHits} hitCountUnavailable={hitCountUnavailable} />
    </div>
  );
}
