package com.example.safari.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthInterceptorTest {
    private final PasswordlessAuthService authService = mock(PasswordlessAuthService.class);
    private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor(authService);

    @Test
    void blocksUnauthenticatedAdminEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.isAdminSession(request)).thenReturn(false);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }

    @Test
    void permitsAuthorizedAdminEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/bookings");
        request.addHeader("Authorization", "Bearer admin-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.isAdminSession(request)).thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void leavesPublicAndTokenizedOperatorActionsAvailable() throws Exception {
        MockHttpServletRequest publicRequest = new MockHttpServletRequest("GET", "/api/tours");
        MockHttpServletRequest tokenizedAction = new MockHttpServletRequest("POST", "/api/operator/booking-action");

        assertTrue(interceptor.preHandle(publicRequest, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(tokenizedAction, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void blocksDirectOperatorBookingManagementWithoutAdminKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/operator/bookings/12/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.isAdminSession(request)).thenReturn(false);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }
}
