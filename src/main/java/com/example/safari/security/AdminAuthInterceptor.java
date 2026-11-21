package com.example.safari.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Objects;

import java.io.IOException;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final PasswordlessAuthService authService;

    public AdminAuthInterceptor(PasswordlessAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) throws IOException {
        request = Objects.requireNonNull(request);
        response = Objects.requireNonNull(response);
        handler = Objects.requireNonNull(handler);
        String path = request.getRequestURI();
        if (!isProtectedPath(path)) {
            return true;
        }

        if (authService.isAdminSession(request)) {
            return true;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Administrator access required.\"}");
        return false;
    }

    private boolean isProtectedPath(String path) {
        return path.equals("/api/admin")
                || path.startsWith("/api/admin/")
                || path.equals("/api/operator/bookings")
                || path.startsWith("/api/operator/bookings/");
    }
}
