package com.example.safari.payment;

import com.example.safari.car.CarBooking;
import com.example.safari.car.CarBookingRepository;
import com.example.safari.car.CarBookingStatus;
import com.example.safari.config.PricingConversionService;
import com.example.safari.security.AppUser;
import com.example.safari.security.PasswordlessAuthService;
import com.example.safari.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarPaymentControllerTest {

    @Mock
    private CarBookingRepository bookings;
    @Mock
    private PaymentProvider paymentProvider;
    @Mock
    private PaymentCompletionService completionService;
    @Mock
    private PasswordlessAuthService authService;
    @Mock
    private PricingConversionService conversionService;
    @Mock
    private HttpServletRequest request;

    private CarPaymentController controller;
    private CarBooking booking;

    @BeforeEach
    void setUp() {
        controller = new CarPaymentController(
                bookings, paymentProvider, completionService, authService, conversionService);
        AppUser customer = AppUser.builder()
                .email("guest@example.com")
                .passwordHash("hash")
                .fullName("Guest")
                .role(Role.CUSTOMER)
                .build();
        booking = CarBooking.builder()
                .id(22L)
                .customer(customer)
                .currency("USD")
                .totalAmount(new BigDecimal("150.00"))
                .status(CarBookingStatus.CONFIRMED.name())
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentCurrency("KES")
                .build();
        when(authService.customerEmailFromRequest(request)).thenReturn("guest@example.com");
        when(bookings.findByIdForUpdate(22L)).thenReturn(Optional.of(booking));
    }

    @Test
    void createsKesCheckoutForConfirmedVehicleBooking() {
        when(conversionService.getUsdToKesRate()).thenReturn(new BigDecimal("130.50"));
        when(paymentProvider.createCarCheckout(
                22L, new BigDecimal("19575.00"), "KES", "guest@example.com"))
                .thenReturn(new PaymentProvider.CheckoutSession(
                        "car-ref-1", "https://checkout.paystack.com/car-ref-1"));

        ResponseEntity<PaymentController.CheckoutResponse> result = controller.checkout(22L, request);

        PaymentController.CheckoutResponse response = Objects.requireNonNull(result.getBody());
        assertEquals(200, result.getStatusCode().value());
        assertEquals("car-ref-1", response.reference());
        assertEquals(new BigDecimal("19575.00"), response.paymentAmount());
        assertEquals("KES", response.paymentCurrency());
        assertEquals(PaymentStatus.INITIATED.name(), response.status());
        assertNotNull(booking.getPaymentQuoteExpiresAt());
        verify(bookings).save(booking);
    }
}
