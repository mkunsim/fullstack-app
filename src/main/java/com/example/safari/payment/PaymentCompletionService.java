package com.example.safari.payment;

import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.booking.BookingStatus;
import com.example.safari.car.CarBooking;
import com.example.safari.car.CarBookingRepository;
import com.example.safari.car.CarBookingStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class PaymentCompletionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletionService.class);

    private final BookingRepository bookingRepository;
    private final CarBookingRepository carBookingRepository;
    private final PaymentProvider paymentProvider;

    public PaymentCompletionService(
            BookingRepository bookingRepository,
            CarBookingRepository carBookingRepository,
            PaymentProvider paymentProvider
    ) {
        this.bookingRepository = bookingRepository;
        this.carBookingRepository = carBookingRepository;
        this.paymentProvider = paymentProvider;
    }

    /**
     * Verify a Paystack transaction and mark its booking as PAID.
     * This method is also used by the Paystack webhook.
     */
    @Transactional
    public Booking verifyAndComplete(String reference) {

        if (reference == null || reference.isBlank()) {

            throw new IllegalArgumentException(
                    "Payment reference is required."
            );
        }

        PaymentProvider.PaymentVerification verification;

        try {
            verification = paymentProvider.verifyTransaction(reference);
        } catch (RuntimeException ex) {
            throw ex;
        }

        return completeVerified(verification, reference);
    }

    @Transactional
    public void verifyAndCompleteAny(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required.");
        }
        PaymentProvider.PaymentVerification verification =
                paymentProvider.verifyTransaction(reference);
        if (verification != null && verification.isCarBooking()) {
            completeCarVerified(verification, reference);
        } else {
            completeVerified(verification, reference);
        }
    }

    /**
     * Complete a payment using verification data already retrieved
     * from the payment provider.
     */
    @Transactional
    public Booking completeVerified(
            PaymentProvider.PaymentVerification verification,
            String expectedReference
    ) {

        if (expectedReference == null || expectedReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Expected payment reference is required."
            );
        }

        if (verification == null) {

            throw new IllegalStateException(
                    "Payment provider returned no verification data."
            );
        }
        if (verification.isCarBooking()) {
            throw new IllegalStateException("Payment verification refers to a vehicle booking.");
        }

        // Verify transaction reference.
        if (verification.reference() == null
                || !expectedReference.equals(verification.reference())) {

            throw new IllegalStateException(
                    "Payment reference does not match."
            );
        }

        // Only successful transactions can complete a booking.
        if (!"success".equalsIgnoreCase(verification.status())) {

            throw new IllegalStateException(
                    "Payment was not successful."
            );
        }

        // Verify booking ID.
        Long bookingId = verification.bookingId();
        if (bookingId == null || bookingId <= 0) {

            throw new IllegalStateException(
                    "Payment verification contains no valid booking ID."
            );
        }

        long validBookingId = bookingId;

        Booking booking = bookingRepository.findById(
                validBookingId
        ).orElseThrow(() -> {

            return new IllegalStateException(
                    "Booking was not found."
            );
        });

        // Verify that this reference belongs to this booking.
        if (booking.getPaymentReference() == null
                || !booking.getPaymentReference().equals(expectedReference)) {

            throw new IllegalStateException(
                    "Booking payment reference does not match."
            );
        }

        // Booking must be confirmed.
        if (booking.getStatus() != BookingStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Booking is not confirmed."
            );
        }

        // Verify Paystack currency.
        String verifiedCurrency = verification.currency();

        if (verifiedCurrency == null
                || !"KES".equals(
                        verifiedCurrency.trim().toUpperCase(Locale.ROOT)
                )) {

            throw new IllegalStateException(
                    "Paystack transaction currency must be KES."
            );
        }

        // Verify booking currency.
        if (booking.getPaymentCurrency() == null
                || !"KES".equalsIgnoreCase(
                        booking.getPaymentCurrency()
                )) {

            throw new IllegalStateException(
                    "Booking payment currency is not KES."
            );
        }

        // Verify the expected KES amount.
        BigDecimal expectedAmount = booking.getPaymentAmountKes();

        if (expectedAmount == null
                || expectedAmount.signum() <= 0) {

            throw new IllegalStateException(
                    "Booking has no valid KES payment quote."
            );
        }

        final long expectedAmountMinor;

        try {
            expectedAmountMinor = expectedAmount
                    .setScale(2)
                    .movePointRight(2)
                    .longValueExact();

        } catch (ArithmeticException ex) {

            throw new IllegalStateException(
                    "Booking KES payment quote is invalid.",
                    ex
            );
        }

        if (verification.amountMinor() != expectedAmountMinor) {

            throw new IllegalStateException(
                    "Payment amount does not match the booking KES quote."
            );
        }

        // Idempotency: do not process a successful payment twice.
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {

            return booking;
        }

        if (booking.getPaymentStatus() != PaymentStatus.INITIATED) {

            throw new IllegalStateException(
                    "Payment was not initiated for this booking."
            );
        }

        booking.setPaymentStatus(PaymentStatus.PAID);

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Payment completed for booking {}", savedBooking.getId());

        return savedBooking;
    }

    @Transactional
    public CarBooking completeCarVerified(
            PaymentProvider.PaymentVerification verification,
            String expectedReference
    ) {
        validateReferenceAndSuccess(verification, expectedReference);
        if (!verification.isCarBooking()) {
            throw new IllegalStateException("Payment verification does not refer to a vehicle booking.");
        }

        Long bookingId = verification.bookingId();
        if (bookingId == null || bookingId <= 0) {
            throw new IllegalStateException("Payment verification contains no valid vehicle booking ID.");
        }

        CarBooking booking = carBookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException("Vehicle booking was not found."));
        if (booking.getPaymentReference() == null
                || !booking.getPaymentReference().equals(expectedReference)) {
            throw new IllegalStateException("Vehicle booking payment reference does not match.");
        }
        if (!CarBookingStatus.CONFIRMED.name().equals(booking.getStatus())) {
            throw new IllegalStateException("Vehicle booking is not confirmed.");
        }
        if (verification.currency() == null
                || !"KES".equals(verification.currency().trim().toUpperCase(Locale.ROOT))
                || !"KES".equalsIgnoreCase(booking.getPaymentCurrency())) {
            throw new IllegalStateException("Paystack transaction currency must be KES.");
        }

        BigDecimal expectedAmount = booking.getPaymentAmountKes();
        if (expectedAmount == null || expectedAmount.signum() <= 0) {
            throw new IllegalStateException("Vehicle booking has no valid KES payment quote.");
        }
        final long expectedAmountMinor;
        try {
            expectedAmountMinor = expectedAmount.setScale(2).movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Vehicle booking KES payment quote is invalid.", ex);
        }
        if (verification.amountMinor() != expectedAmountMinor) {
            throw new IllegalStateException("Payment amount does not match the vehicle booking KES quote.");
        }
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            return booking;
        }
        if (booking.getPaymentStatus() != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Payment was not initiated for this vehicle booking.");
        }

        booking.setPaymentStatus(PaymentStatus.PAID);
        CarBooking saved = carBookingRepository.save(booking);
        log.info("Payment completed for vehicle booking {}", saved.getId());
        return saved;
    }

    private static void validateReferenceAndSuccess(
            PaymentProvider.PaymentVerification verification,
            String expectedReference
    ) {
        if (expectedReference == null || expectedReference.isBlank()) {
            throw new IllegalArgumentException("Expected payment reference is required.");
        }
        if (verification == null) {
            throw new IllegalStateException("Payment provider returned no verification data.");
        }
        if (verification.reference() == null || !expectedReference.equals(verification.reference())) {
            throw new IllegalStateException("Payment reference does not match.");
        }
        if (!verification.isSuccessful()) {
            throw new IllegalStateException("Payment was not successful.");
        }
    }
}