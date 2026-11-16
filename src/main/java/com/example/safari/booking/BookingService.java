package com.example.safari.booking;

import com.example.safari.tour.Departure;
import com.example.safari.tour.DepartureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEmailService emailService;
    private final DepartureRepository departureRepository;

    public BookingService(
            BookingRepository bookingRepository,
            BookingEmailService emailService,
            DepartureRepository departureRepository) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "bookingRepository is required");
        this.emailService = Objects.requireNonNull(emailService, "emailService is required");
        this.departureRepository = Objects.requireNonNull(departureRepository, "departureRepository is required");
    }

    @Transactional
    public Booking confirmBooking(Long bookingId) {
        return updatePendingStatus(bookingId, BookingStatus.CONFIRMED);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        return updatePendingStatus(bookingId, BookingStatus.CANCELLED);
    }

    @Transactional
    public Booking updatePendingStatus(Long bookingId, BookingStatus status) {
        validateFinalStatus(bookingId, status);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        return applyPendingStatus(booking, status, false);
    }

    @Transactional
    public boolean expirePendingBooking(Long bookingId, Instant expiresBefore) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING
                || booking.getCreatedAt().isAfter(expiresBefore)) {
            return false;
        }
        applyPendingStatus(booking, BookingStatus.CANCELLED, true);
        return true;
    }

    private Booking applyPendingStatus(Booking booking, BookingStatus status, boolean expired) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can be updated");
        }
        if (status == BookingStatus.CANCELLED || status == BookingStatus.DECLINED) {
            Departure departure = departureRepository.findByIdForUpdate(booking.getDeparture().getId())
                    .orElseThrow(() -> new IllegalStateException("Booking departure was not found"));
            departure.release(booking.getGuests());
        }
        booking.setStatus(status);
        booking.setActionToken(null);
        booking.setActionTokenExpiresAt(null);
        Booking updated = bookingRepository.save(booking);
        if (expired) {
            emailService.sendExpiredNotice(updated);
        } else {
            emailService.sendStatusUpdate(updated);
        }
        return updated;
    }

    private static void validateFinalStatus(Long bookingId, BookingStatus status) {
        if (bookingId == null || bookingId <= 0) {
            throw new IllegalArgumentException("A valid booking ID is required.");
        }
        if (status == null || status == BookingStatus.PENDING) {
            throw new IllegalArgumentException("A final booking status is required.");
        }
    }

    public int getAvailableSeats(Departure departure) {
        int confirmedGuests = 0;
        int pendingGuests = 0;
        List<Booking> bookings = bookingRepository.findByDepartureId(departure.getId());
        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.CONFIRMED) {
                confirmedGuests += booking.getGuests();
            } else if (booking.getStatus() == BookingStatus.PENDING) {
                pendingGuests += booking.getGuests();
            }
        }
        return departure.getCapacity() - confirmedGuests - pendingGuests;
    }
}
