package com.example.safari.booking;

import com.example.safari.car.CarBooking;
import com.example.safari.car.CarBookingLifecycleService;
import com.example.safari.car.CarBookingRepository;
import com.example.safari.car.CarBookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class PendingBookingLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingBookingLifecycleScheduler.class);

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final BookingEmailService bookingEmailService;
    private final CarBookingRepository carBookings;
    private final CarBookingLifecycleService carBookingService;
    private final long holdHours;
    private final long reminderHours;

    public PendingBookingLifecycleScheduler(
            BookingRepository bookings,
            BookingService bookingService,
            BookingEmailService bookingEmailService,
            CarBookingRepository carBookings,
            CarBookingLifecycleService carBookingService,
            @Value("${app.bookings.hold-hours:72}") long holdHours,
            @Value("${app.bookings.reminder-hours:48}") long reminderHours) {
        if (holdHours < 2 || reminderHours < 1 || reminderHours >= holdHours) {
            throw new IllegalArgumentException("Booking reminder must precede a hold expiry of at least two hours.");
        }
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.bookingEmailService = bookingEmailService;
        this.carBookings = carBookings;
        this.carBookingService = carBookingService;
        this.holdHours = holdHours;
        this.reminderHours = reminderHours;
    }

    @Scheduled(fixedDelayString = "${app.bookings.poll-ms:60000}")
    public void processPendingBookings() {
        Instant now = Instant.now();
        Instant expiresBefore = now.minusSeconds(holdHours * 3600);
        Instant reminderBefore = now.minusSeconds(reminderHours * 3600);
        processTourBookings(expiresBefore, reminderBefore);
        LocalDateTime localNow = LocalDateTime.now();
        processCarBookings(localNow.minusHours(holdHours), localNow.minusHours(reminderHours));
    }

    private void processTourBookings(Instant expiresBefore, Instant reminderBefore) {
        List<Booking> expired = bookings.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                BookingStatus.PENDING, expiresBefore);
        for (Booking booking : expired) {
            try {
                bookingService.expirePendingBooking(booking.getId(), expiresBefore);
            } catch (RuntimeException ex) {
                log.error("Could not expire pending tour booking {}", booking.getId(), ex);
            }
        }
        List<Booking> reminders = bookings.findTop100ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                BookingStatus.PENDING, expiresBefore, reminderBefore);
        reminders.forEach(bookingEmailService::sendExpiryReminder);
    }

    private void processCarBookings(LocalDateTime expiresBefore, LocalDateTime reminderBefore) {
        List<CarBooking> expired = carBookings.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                CarBookingStatus.PENDING.name(), expiresBefore);
        for (CarBooking booking : expired) {
            try {
                carBookingService.expirePendingBooking(booking.getId(), expiresBefore);
            } catch (RuntimeException ex) {
                log.error("Could not expire pending vehicle booking {}", booking.getId(), ex);
            }
        }
        List<CarBooking> reminders = carBookings.findTop100ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                CarBookingStatus.PENDING.name(), expiresBefore, reminderBefore);
        reminders.forEach(carBookingService::remindPendingBooking);
    }
}
