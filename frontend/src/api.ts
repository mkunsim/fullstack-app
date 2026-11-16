import type { AddOn, AdminBooking, Booking, CarBooking, CarBookingRequest, SafariCar, Tour, User } from "./types";

const API = import.meta.env.VITE_API_URL ?? "/api";

function headers(json = false): HeadersInit {
  return json ? { "Content-Type": "application/json" } : {};
}

function request(url: string, init: RequestInit = {}): Promise<Response> {
  return fetch(url, { credentials: "include", ...init });
}

async function read<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export const getTours = () => request(`${API}/tours`).then((r) => read<Tour[]>(r));
export const recordSiteHit = (pagePath: string) => request(`${API}/hits`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ pagePath }),
}).then((r) => read<{ totalCount: number }>(r));
export const sendCode = (email: string) => request(`${API}/auth/send-code`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ email }),
}).then(async (r) => {
  if (!r.ok) {
    const body = await r.json().catch(() => null);
    throw new Error(body?.message ?? `Request failed (${r.status})`);
  }
});
export const verifyCode = (email: string, code: string) => request(`${API}/auth/verify-code`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ email, code }),
}).then((r) => read<User>(r));
export const currentUser = () => request(`${API}/auth/me`).then((r) => read<User>(r));
export const logout = () => request(`${API}/auth/logout`, { method: "POST" }).then((r) => {
  if (!r.ok) throw new Error(`Sign-out failed (${r.status})`);
});

export const createBooking = (
  departureId: number,
  guests: number,
  customerName: string,
  customerEmail: string,
  addOnIds: number[],
) => request(`${API}/bookings`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ departureId, guests, customerName, customerEmail, addOnIds }),
}).then((r) => read<Booking>(r));

export const myBookings = () => request(`${API}/bookings/mine`).then((r) => read<Booking[]>(r));
export const cancelMyBooking = (id: number) => request(`${API}/bookings/${id}/cancel`, {
  method: "POST",
}).then((r) => read<Booking>(r));

export const adminBookings = () => request(`${API}/admin/bookings`).then((r) => read<AdminBooking[]>(r));
export const adminTours = () => request(`${API}/admin/tours`).then((r) => read<Tour[]>(r));
type TourInput = Omit<Tour, "id" | "departures" | "addOns">;
export const createTour = (input: TourInput) => request(`${API}/admin/tours`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ ...input, active: true }),
}).then((r) => read<Tour>(r));
export const updateTour = (id: number, input: TourInput & { active: boolean }) => request(`${API}/admin/tours/${id}`, {
  method: "PUT",
  headers: headers(true),
  body: JSON.stringify(input),
}).then((r) => read<Tour>(r));
export const updateBookingStatus = (id: number, status: Booking["status"]) => request(`${API}/admin/bookings/${id}/status`, {
  method: "PUT",
  headers: headers(true),
  body: JSON.stringify({ status }),
}).then((r) => read<AdminBooking>(r));
export const addDeparture = (tourId: number, startDate: string, capacity: number) => request(`${API}/admin/tours/${tourId}/departures`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ startDate, capacity }),
}).then((r) => read<unknown>(r));

export const adminAddOns = () => request(`${API}/admin/add-ons`).then((r) => read<AddOn[]>(r));
export type AddOnInput = Omit<AddOn, "id">;
export const createAddOn = (input: AddOnInput) => request(`${API}/admin/add-ons`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify(input),
}).then((r) => read<AddOn>(r));
export const updateAddOn = (id: number, input: AddOnInput) => request(`${API}/admin/add-ons/${id}`, {
  method: "PUT",
  headers: headers(true),
  body: JSON.stringify(input),
}).then((r) => read<AddOn>(r));

export const getCars = () => request(`${API}/cars`).then((r) => read<SafariCar[]>(r));
export const createCarBooking = (input: CarBookingRequest) => request(`${API}/car-bookings`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify(input),
}).then((r) => read<CarBooking>(r));
export const myCarBookings = () => request(`${API}/car-bookings/mine`).then((r) => read<CarBooking[]>(r));
export const cancelMyCarBooking = (id: number) => request(`${API}/car-bookings/${id}/cancel`, {
  method: "POST",
}).then((r) => read<CarBooking>(r));
export const adminCars = () => request(`${API}/admin/cars`).then((r) => read<SafariCar[]>(r));
export const createCar = (input: Omit<SafariCar, "id">) => request(`${API}/admin/cars`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ ...input, active: true }),
}).then((r) => read<SafariCar>(r));
export const updateCar = (id: number, input: Omit<SafariCar, "id"> & { active: boolean }) => request(`${API}/admin/cars/${id}`, {
  method: "PUT",
  headers: headers(true),
  body: JSON.stringify(input),
}).then((r) => read<SafariCar>(r));
export const adminCarBookings = () => request(`${API}/admin/car-bookings`).then((r) => read<CarBooking[]>(r));
export const updateCarBookingStatus = (id: number, status: CarBooking["status"]) => request(`${API}/admin/car-bookings/${id}/status`, {
  method: "PUT",
  headers: headers(true),
  body: JSON.stringify({ status }),
}).then((r) => read<CarBooking>(r));

export interface CheckoutResponse {
  checkoutUrl: string | null;
  reference: string | null;
  provider: string;
  status: string;
  paymentAmount: number | null;
  paymentCurrency: string | null;
  exchangeRate: number | null;
  exchangeRateSource: string | null;
  quoteExpiresAt: string | null;
}

export const createCheckout = (bookingId: number) => request(
  `${API}/payments/bookings/${bookingId}/checkout`,
  { method: "POST" },
).then((r) => read<CheckoutResponse>(r));
export const createCarCheckout = (bookingId: number) => request(
  `${API}/payments/car-bookings/${bookingId}/checkout`,
  { method: "POST" },
).then((r) => read<CheckoutResponse>(r));
export const verifyPayment = (reference: string) => request(`${API}/payments/verify`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify({ reference }),
}).then((r) => read<{ message: string; bookingId?: number; carBookingId?: number; paymentStatus: string }>(r));

export interface EnquiryRequest {
  name: string;
  email: string;
  itemType: "TOUR" | "VEHICLE";
  itemName: string;
  message: string;
}

export const submitEnquiry = (input: EnquiryRequest) => request(`${API}/enquiries`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify(input),
}).then((r) => read<{ message: string }>(r));

export interface ReviewRequest {
  rating: number;
  text: string;
}

export const submitReview = (input: ReviewRequest) => request(`${API}/reviews`, {
  method: "POST",
  headers: headers(true),
  body: JSON.stringify(input),
}).then((r) => read<{ message: string }>(r));
