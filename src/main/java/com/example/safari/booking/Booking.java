package com.example.safari.booking;

import com.example.safari.payment.PaymentStatus;
import com.example.safari.security.AppUser;
import com.example.safari.tour.Departure;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings", indexes = @Index(
        name = "idx_bookings_status_created_at",
        columnList = "status,created_at"
))
@Data
@Getter
@Setter
@ToString(exclude = {"customer", "departure"})
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(
            name = "customer_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bookings_customer_id")
    )
    private AppUser customer;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(
            name = "departure_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bookings_departure_id")
    )
    private Departure departure;

    @Column(nullable = false)
    private int guests;

    // Original advertised booking price in USD.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    // Locked amount payable through Paystack in KES.
    @Column(precision = 12, scale = 2)
    private BigDecimal paymentAmountKes;

    // Exchange rate used for the KES quote.
    @Column(precision = 10, scale = 6)
    private BigDecimal exchangeRate;

    // Source of the exchange rate (e.g., "pricing-conversion-service").
    @Column(length = 64)
    private String exchangeRateSource;

    // When the KES payment quote expires.
    @Column
    private Instant paymentQuoteExpiresAt;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(length = 64, unique = true)
    private String actionToken;

    @Column
    private Instant actionTokenExpiresAt;

    @Column(length = 128)
    private String paymentReference;

    @Column(length = 255)
    private String paymentCheckoutUrl;

    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "booking_addons",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "addon_id")
    )
    @OrderColumn(name = "selection_order")
    private List<AddOn> addons = new ArrayList<>();

    @Builder.Default
    @Column(length = 3)
    private String paymentCurrency = "KES";

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = BookingStatus.PENDING;
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }
        if (currency == null || currency.isBlank()) {
            currency = "USD";
        }
        if (paymentCurrency == null || paymentCurrency.isBlank()) {
            paymentCurrency = "KES";
        }
    }

    public Booking(AppUser customer, Departure departure, int guests, BigDecimal totalAmount) {
        this.customer = customer;
        this.departure = departure;
        this.guests = guests;
        this.addons = new ArrayList<>();
        this.totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        this.currency = "USD";
        this.status = BookingStatus.PENDING;
        this.paymentStatus = PaymentStatus.UNPAID;
        this.createdAt = Instant.now();
        departure.reserve(guests);
    }

    public void setGuests(int guests) {
        if (guests < 1) throw new IllegalArgumentException("Guests must be at least 1");
        this.guests = guests;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Total amount must be positive");
        this.totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public void setCurrency(String currency) {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("Currency is required");
        this.currency = currency;
    }

    public void setPaymentAmountKes(BigDecimal paymentAmountKes) {
        this.paymentAmountKes = paymentAmountKes;
    }

    public void setStatus(BookingStatus status) {
        if (status == null) throw new IllegalArgumentException("Booking status is required");
        this.status = status;
    }

    public void setPaymentStatus(PaymentStatus status) {
        if (status == null) throw new IllegalArgumentException("Payment status is required");
        if (paymentStatus == PaymentStatus.PAID && status != PaymentStatus.PAID && status != PaymentStatus.REFUNDED) {
            throw new IllegalStateException("A paid booking cannot be moved back to an unpaid state.");
        }
        this.paymentStatus = status;
    }

    public void setActionToken(String actionToken) { this.actionToken = actionToken; }
    public void setActionTokenExpiresAt(Instant actionTokenExpiresAt) { this.actionTokenExpiresAt = actionTokenExpiresAt; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public void setPaymentCheckoutUrl(String paymentCheckoutUrl) { this.paymentCheckoutUrl = paymentCheckoutUrl; }
    public void setPaymentCurrency(String paymentCurrency) { this.paymentCurrency = paymentCurrency; }

    public void addAddOn(AddOn addon) {
        addons.add(addon);
    }

    public void setPaymentReferenceSafe(String ref) {
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("Payment reference is required.");
        if (paymentReference != null && !paymentReference.equals(ref) && paymentStatus != PaymentStatus.FAILED) {
            throw new IllegalStateException("An active payment reference already exists.");
        }
        this.paymentReference = ref;
    }

    @Transient
    public BigDecimal getKesPaymentQuote() { return paymentAmountKes; }

    public void setKesPaymentQuote(BigDecimal exchangeRate, String source, Instant expiresAt) {
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("USD to KES exchange rate must be positive.");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Booking must have a valid USD amount.");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Exchange rate source is required.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Payment quote expiry is required.");
        }
        this.exchangeRate = exchangeRate.setScale(6, RoundingMode.HALF_UP);
        this.paymentAmountKes = totalAmount.multiply(this.exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);
        this.paymentCurrency = "KES";
        this.exchangeRateSource = source;
        this.paymentQuoteExpiresAt = expiresAt;
    }

}
