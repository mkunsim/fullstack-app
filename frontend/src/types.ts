/*export type Tour = {
  id: number
  name: string
  description: string
  destination: string
  durationDays: number
  pricePerPerson: number
  imageUrl: string
}*/
export type BookingResponse = {
  id: number
  status: string
  customerName: string
  customerEmail: string
  startDate: string
  guests: number
}

export type Departure = {id:number;startDate:string;capacity:number;availableGuests:number}
export type OptionalExperience = {
  id: number;
  name: string;
  description: string;
  preference: string;
  price: number;
  available: boolean;
}
export type SelectedOptionalExperience = {
  optionalExperienceId: number;
  name: string;
  description: string;
  preference: string;
  price: number;
}
export type Tour = {id:number;name:string;description:string;destination:string;durationDays:number;pricePerPerson:number;imageUrl:string;departures:Departure[];optionalExperiences:OptionalExperience[];active?:boolean}
export type User = {email:string;fullName:string;role:'CUSTOMER'|'ADMIN'}
export type BookingStatus = "PENDING" | "CONFIRMED" | "DECLINED" | "CANCELLED"
export type Booking = {id:number;tourName:string;destination:string;startDate:string;guests:number;totalAmount:number;currency:string;status:BookingStatus;paymentStatus:string;optionalExperiences:SelectedOptionalExperience[]}
export type AdminBooking = Booking & {customerName:string;customerEmail:string}

export type SafariCar = {
  id:number; name:string; description:string; destination:string; passengerCapacity:number; active?:boolean;
  dailyRateUsd:number; guideDailyRateUsd:number; imageUrl:string
}
export type CarBooking = {
  id:number; carName:string; destination:string; startDate:string; endDate:string;
  pickupLocation:string; dropoffLocation:string; passengers:number; driverIncluded:boolean;
  guideIncluded:boolean; totalAmount:number; currency:string; status:BookingStatus; paymentStatus:string;
  customerName:string; customerEmail:string
}
export type CarBookingRequest = {
  carId:number; startDate:string; endDate:string; pickupLocation:string; dropoffLocation:string;
  passengers:number; guideIncluded:boolean; customerName:string; customerEmail:string
}
