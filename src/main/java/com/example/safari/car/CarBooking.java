package com.example.safari.car;

import com.example.safari.security.AppUser;
import com.example.safari.payment.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;

@Entity
@Table(name = "car_bookings", indexes = @Index(
        name = "idx_car_bookings_status_created_at",
        columnList = "status,created_at"
))
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CarBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "car_id",
            foreignKey = @ForeignKey(name = "fk_car_bookings_car_id")
    )
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "customer_id",
            foreignKey = @ForeignKey(name = "fk_car_bookings_customer_id")
    )
    private AppUser customer;

    @Column(nullable = false)
    private LocalDate pickupDate;

    @Column(nullable = false)
    private LocalDate dropoffDate;

    @Builder.Default
    @Column(nullable = false)
    private LocalDate bookingDate = LocalDate.now();

    @Column(nullable = false)
    private int passengerCount;

    @Column(nullable = false)
    private int days;

    @Column(nullable = false)
    private String pickupLocation;

    @Column(nullable = false)
    private String dropoffLocation;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean guideIncluded;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column
    private BigDecimal paymentAmountKes;

    @Column(precision = 10, scale = 6)
    private BigDecimal exchangeRate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Builder.Default
    @Column(name = "payment_currency", nullable = false, length = 3)
    private String paymentCurrency = "KES";

    @Column(name = "exchange_rate_source", length = 64)
    private String exchangeRateSource;

    @Column(name = "payment_quote_expires_at")
    private Instant paymentQuoteExpiresAt;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "payment_checkout_url", length = 255)
    private String paymentCheckoutUrl;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructor used by controller for new bookings - without exchangeRate
    public CarBooking(Car car, AppUser customer, LocalDate pickupDate, LocalDate dropoffDate,
                      int passengerCount, String pickupLocation, String dropoffLocation,
                      String currency, String status, boolean guideIncluded,
                      BigDecimal totalAmount) {
        this.car = car;
        this.customer = customer;
        this.pickupDate = pickupDate;
        this.dropoffDate = dropoffDate;
        this.bookingDate = LocalDate.now();
        this.passengerCount = passengerCount;
        this.days = (int) java.time.temporal.ChronoUnit.DAYS.between(pickupDate, dropoffDate);
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        this.currency = currency;
        this.status = status;
        this.guideIncluded = guideIncluded;
        this.totalAmount = totalAmount;
        this.createdAt = LocalDateTime.now();
    }

    // Full constructor for direct instantiation
    public CarBooking(Car car, AppUser customer, LocalDate pickupDate, LocalDate dropoffDate,
                      int passengerCount, String pickupLocation, String dropoffLocation,
                      String currency, String status, boolean guideIncluded,
                      BigDecimal totalAmount, BigDecimal paymentAmountKes, BigDecimal exchangeRate) {
        this(car, customer, pickupDate, dropoffDate, passengerCount, pickupLocation, dropoffLocation,
            currency, status, guideIncluded, totalAmount);
        this.paymentAmountKes = paymentAmountKes;
        this.exchangeRate = exchangeRate;
    }

    public void setKesPaymentQuote(BigDecimal exchangeRate, String source, Instant expiresAt) {
        if (exchangeRate == null || exchangeRate.signum() <= 0) {
            throw new IllegalArgumentException("USD to KES exchange rate must be positive.");
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalStateException("Vehicle booking must have a valid USD amount.");
        }
        if (source == null || source.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("Exchange rate source and quote expiry are required.");
        }
        this.exchangeRate = exchangeRate.setScale(6, RoundingMode.HALF_UP);
        this.paymentAmountKes = totalAmount.multiply(this.exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);
        this.paymentCurrency = "KES";
        this.exchangeRateSource = source;
        this.paymentQuoteExpiresAt = expiresAt;
    }

    public void setPaymentReferenceSafe(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required.");
        }
        if (paymentReference != null && !paymentReference.equals(reference)
                && paymentStatus != PaymentStatus.FAILED) {
            throw new IllegalStateException("An active payment reference already exists.");
        }
        this.paymentReference = reference;
    }
}