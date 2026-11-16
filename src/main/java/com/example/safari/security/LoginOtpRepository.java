package com.example.safari.security;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoginOtpRepository extends JpaRepository<LoginOtp, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select otp from LoginOtp otp where otp.email = :email")
    Optional<LoginOtp> findByEmailForUpdate(@Param("email") String email);
}
