package com.kasa.estate_management.auth.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
import com.kasa.estate_management.auth.model.PasswordResetCode;
import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.auth.repository.PasswordResetCodeRepository;
import com.kasa.estate_management.auth.repository.UserRepository;
import com.kasa.estate_management.common.PhoneNumbers;
import com.kasa.estate_management.notification.model.NotificationType;
import com.kasa.estate_management.notification.service.NotificationService;
import com.kasa.estate_management.tenant.model.Tenant;
import com.kasa.estate_management.tenant.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetCodeRepository codeRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final String company;

    public PasswordResetService(PasswordResetCodeRepository codeRepository,
                                TenantRepository tenantRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                NotificationService notificationService,
                                AuditService auditService,
                                @Value("${app.company-name:Kasa Estate}") String company) {
        this.codeRepository = codeRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.company = company;
    }

    /**
     * Sends a reset code if the number belongs to an active tenant login. Does nothing otherwise,
     * and the caller cannot tell which case happened.
     */
    @Transactional
    public void requestReset(String phone) {
        String msisdn = PhoneNumbers.normalizeKenyan(phone);
        if (msisdn == null) {
            return;
        }

        Optional<User> found = findTenantUser(msisdn);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();

        Instant now = Instant.now();
        long recent = codeRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)));
        if (recent >= MAX_REQUESTS_PER_HOUR) {
            return;
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        codeRepository.save(PasswordResetCode.builder()
            .user(user)
            .codeHash(passwordEncoder.encode(code))
            .expiresAt(now.plus(CODE_TTL))
            .build());

        String message = "Your " + company + " password reset code is " + code
            + ". It expires in 10 minutes. Never share this code. - " + company;
        notificationService.queueSms("+" + msisdn, message, NotificationType.PASSWORD_RESET, null);

        // Attributed to the account owner; neither the phone number nor the code is recorded.
        auditService.recordAs(user.getEmail(), user.getId(), user.getRole().name(),
            AuditAction.PASSWORD_RESET_REQUESTED, "USER", user.getId(), "Password reset code requested by SMS");
    }

    /**
     * Returns true if the password was changed. Returns false, rather than throwing, for every
     * failure: a thrown exception would roll back the attempt counter this method just increased.
     */
    @Transactional
    public boolean confirmReset(String phone, String code, String newPassword) {
        String msisdn = PhoneNumbers.normalizeKenyan(phone);
        if (msisdn == null) {
            return false;
        }

        Optional<User> found = findTenantUser(msisdn);
        if (found.isEmpty()) {
            return false;
        }
        User user = found.get();

        Optional<PasswordResetCode> latest =
            codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());
        if (latest.isEmpty()) {
            return false;
        }

        PasswordResetCode reset = latest.get();
        Instant now = Instant.now();
        if (reset.getExpiresAt().isBefore(now) || reset.getAttempts() >= MAX_ATTEMPTS) {
            return false;
        }

        reset.setAttempts(reset.getAttempts() + 1);
        if (!passwordEncoder.matches(code, reset.getCodeHash())) {
            codeRepository.save(reset);

            // Only counted guesses are logged (at most five per code), so repeated attempts
            // against a locked code cannot flood the audit trail.
            int used = reset.getAttempts();
            auditService.recordAs(user.getEmail(), user.getId(), user.getRole().name(),
                AuditAction.PASSWORD_RESET_FAILED, "USER", user.getId(),
                "Wrong password reset code (attempt " + used + " of " + MAX_ATTEMPTS + ")"
                    + (used >= MAX_ATTEMPTS ? "; code now locked" : ""));
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        List<PasswordResetCode> open = codeRepository.findByUserIdAndUsedAtIsNull(user.getId());
        open.forEach(c -> c.setUsedAt(now));
        codeRepository.saveAll(open);

        auditService.recordAs(user.getEmail(), user.getId(), user.getRole().name(),
            AuditAction.PASSWORD_RESET_COMPLETED, "USER", user.getId(), "Password changed using an SMS code");
        return true;
    }

    private Optional<User> findTenantUser(String msisdn) {
        String national = msisdn.substring(3);
        List<String> variants = List.of(msisdn, "+" + msisdn, "0" + national, national);

        return tenantRepository.findByPhoneNumberIn(variants).stream()
            .map(Tenant::getUser)
            .filter(user -> user != null && user.isActive() && user.getRole() == Role.TENANT)
            .findFirst();
    }
}