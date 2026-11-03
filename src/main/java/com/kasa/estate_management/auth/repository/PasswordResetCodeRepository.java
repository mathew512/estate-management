package com.kasa.estate_management.auth.repository;

import com.kasa.estate_management.auth.model.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    Optional<PasswordResetCode> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<PasswordResetCode> findByUserIdAndUsedAtIsNull(UUID userId);
}