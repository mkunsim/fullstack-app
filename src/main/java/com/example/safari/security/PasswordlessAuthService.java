package com.example.safari.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

@Service
public class PasswordlessAuthService {

    public static final String SESSION_COOKIE = "wildpath_session";
    private static final long OTP_EXPIRY_SECONDS = 600;
    private static final long RESEND_WAIT_SECONDS = 60;
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final AppUserRepository users;
    private final AuthSessionRepository authSessions;
    private final LoginOtpRepository loginOtps;
    private final JavaMailSender mail;
    private final SecureRandom random = new SecureRandom();
    private final String mailUsername;
    private final long sessionExpirySeconds;

    public PasswordlessAuthService(
            AppUserRepository users,
            AuthSessionRepository authSessions,
            LoginOtpRepository loginOtps,
            JavaMailSender mail,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${app.auth.session-expiry-days:30}") long sessionExpiryDays) {
        this.users = users;
        this.authSessions = authSessions;
        this.loginOtps = loginOtps;
        this.mail = mail;
        this.mailUsername = mailUsername;
        if (sessionExpiryDays < 1) {
            throw new IllegalArgumentException("Session expiry must be at least one day.");
        }
        this.sessionExpirySeconds = Duration.ofDays(sessionExpiryDays).toSeconds();
    }

    @Transactional
    public void sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (email.isBlank() || email.length() > 254
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        }
        if (mailUsername == null || mailUsername.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery is not configured."
            );
        }

        Instant now = Instant.now();
        LoginOtp otp = loginOtps.findByEmailForUpdate(email).orElseGet(() -> new LoginOtp(email));
        if (otp.getLastSentAt() != null
                && otp.getLastSentAt().plusSeconds(RESEND_WAIT_SECONDS).isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait one minute before requesting another code."
            );
        }

        String code = Integer.toString(100000 + random.nextInt(900000));
        otp.issue(hash(code), now.plusSeconds(OTP_EXPIRY_SECONDS), now);
        loginOtps.saveAndFlush(otp);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject("Your Wildpath Safaris sign-in code");
        message.setText("Your Wildpath Safaris verification code is " + code
                + ". It expires in 10 minutes. If you did not request it, you can ignore this email.");
        try {
            mail.send(message);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the verification email. Check the email service configuration."
            );
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse verifyCode(String rawEmail, String rawCode) {
        String email = normalize(rawEmail);
        LoginOtp otp = loginOtps.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Code expired. Request a new code."));

        if (otp.getExpiresAt().isBefore(Instant.now()) || otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            loginOtps.delete(otp);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Code expired. Request a new code.");
        }

        String suppliedCode = rawCode == null ? "" : rawCode.trim();
        if (!MessageDigest.isEqual(
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(suppliedCode).getBytes(StandardCharsets.UTF_8))) {
            otp.recordFailedAttempt();
            loginOtps.save(otp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid verification code");
        }
        loginOtps.delete(otp);

        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(Objects.requireNonNull(AppUser.builder()
                        .email(email)
                        .passwordHash("PASSWORDLESS_LOGIN_DISABLED")
                        .fullName(email.substring(0, email.indexOf('@')))
                        .role(Role.CUSTOMER)
                        .build())));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is disabled.");
        }

        String token = generateToken();
        Instant expiresAt = Instant.now().plusSeconds(sessionExpirySeconds);
        authSessions.save(Objects.requireNonNull(AuthSession.builder()
                .tokenHash(hash(token))
                .email(email)
                .expiresAt(expiresAt)
                .build()));
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name(), expiresAt);
    }

    public CurrentUser currentUser(HttpServletRequest request) {
        String email = emailFromRequest(request);
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again."));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is disabled.");
        }
        return new CurrentUser(user.getEmail(), user.getFullName(), user.getRole().name());
    }

    public String customerEmailFromRequest(HttpServletRequest request) {
        CurrentUser user = currentUser(request);
        if (!Role.CUSTOMER.name().equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer access required.");
        }
        return user.email();
    }

    public boolean isAdminSession(HttpServletRequest request) {
        try {
            return Role.ADMIN.name().equals(currentUser(request).role());
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        String token = tokenFromRequest(request);
        if (token.isBlank()) return;
        authSessions.findByTokenHashAndRevokedAtIsNull(hash(token))
                .ifPresent(session -> {
                    session.revoke();
                    authSessions.save(session);
                });
    }

    private String emailFromRequest(HttpServletRequest request) {
        String token = tokenFromRequest(request);
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication session.");
        }
        AuthSession session = authSessions.findByTokenHashAndRevokedAtIsNull(hash(token))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Session expired or invalid. Please sign in again."));
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            session.revoke();
            authSessions.save(session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again.");
        }
        return session.getEmail();
    }

    private String tokenFromRequest(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String bearer = authorization.substring(7).trim();
            if (!bearer.isBlank()) return bearer;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue() == null ? "" : cookie.getValue().trim();
                }
            }
        }
        return "";
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash authentication value", ex);
        }
    }

    public record AuthResponse(String token, String email, String fullName, String role, Instant expiresAt) {}
    public record CurrentUser(String email, String fullName, String role) {}
}
