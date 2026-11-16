package com.example.safari.car;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CarBookingLifecycleService {

    private final CarBookingRepository bookings;
    private final CarBookingEmailService emailService;

    public CarBookingLifecycleService(CarBookingRepository bookings, CarBookingEmailService emailService) {
        this.bookings = bookings;
        this.emailService = emailService;
    }

    @Transactional
    public boolean expirePendingBooking(long bookingId, LocalDateTime expiresBefore) {
        CarBooking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || !CarBookingStatus.PENDING.name().equals(booking.getStatus())
                || booking.getCreatedAt().isAfter(expiresBefore)) {
            return false;
        }
        booking.setStatus(CarBookingStatus.CANCELLED.name());
        CarBooking saved = bookings.save(booking);
        emailService.sendExpiredNotice(saved);
        return true;
    }

    public void remindPendingBooking(CarBooking booking) {
        emailService.sendExpiryReminder(booking);
    }
}
