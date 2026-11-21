package com.example.safari.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PasswordlessAuthService auth;
    private final boolean secureCookie;

    public AuthController(
            PasswordlessAuthService auth,
            @Value("${app.auth.cookie-secure:true}") boolean secureCookie) {
        this.auth = auth;
        this.secureCookie = secureCookie;
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record VerifyRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 6, max = 6) String code
    ) {}
    public record UserProfile(String email, String fullName, String role) {}

    @PostMapping("/send-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendCode(@Valid @RequestBody EmailRequest request) {
        auth.sendCode(request.email());
    }

    @PostMapping("/verify-code")
    public ResponseEntity<UserProfile> verify(@Valid @RequestBody VerifyRequest request) {
        PasswordlessAuthService.AuthResponse result =
                Objects.requireNonNull(auth.verifyCode(request.email(), request.code()));
        ResponseCookie cookie = sessionCookie(
                Objects.requireNonNull(result.token()),
                Duration.between(Instant.now(), result.expiresAt()).getSeconds()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new UserProfile(result.email(), result.fullName(), result.role()));
    }

    @GetMapping("/me")
    public PasswordlessAuthService.CurrentUser currentUser(HttpServletRequest request) {
        return auth.currentUser(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout(request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString())
                .build();
    }

    private ResponseCookie sessionCookie(@NonNull String value, long maxAgeSeconds) {
        return ResponseCookie.from(PasswordlessAuthService.SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api")
                .maxAge(Math.max(0, maxAgeSeconds))
                .build();
    }
}
