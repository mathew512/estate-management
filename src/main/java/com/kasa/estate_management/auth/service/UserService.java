package com.kasa.estate_management.auth.service;

import com.kasa.estate_management.audit.model.AuditAction;
import com.kasa.estate_management.audit.service.AuditService;
import com.kasa.estate_management.auth.model.Role;
import com.kasa.estate_management.auth.model.User;
import com.kasa.estate_management.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public User registerUser(String fullName, String email, String phoneNumber, String rawPassword, Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        User user = User.builder()
            .fullName(fullName)
            .email(email)
            .phoneNumber(phoneNumber)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .active(true)
            .build();

        User saved = userRepository.save(user);
        auditService.record(AuditAction.USER_CREATED, "USER", saved.getId(),
            "Created " + role + " account for " + email);
        return saved;
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    public User authenticate(String email, String rawPassword) {
        Optional<User> found = userRepository.findByEmail(email);

        if (found.isEmpty()) {
            auditService.recordBestEffortAs(email, null, null, AuditAction.LOGIN_FAILED,
                "USER", null, "Login failed: unknown email");
            throw new IllegalArgumentException("Invalid email or password");
        }

        User user = found.get();

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            auditService.recordBestEffortAs(user.getEmail(), user.getId(), user.getRole().name(),
                AuditAction.LOGIN_FAILED, "USER", user.getId(), "Login failed: wrong password");
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.isActive()) {
            auditService.recordBestEffortAs(user.getEmail(), user.getId(), user.getRole().name(),
                AuditAction.LOGIN_FAILED, "USER", user.getId(), "Login refused: account disabled");
            throw new IllegalArgumentException("Account is disabled");
        }

        auditService.recordBestEffortAs(user.getEmail(), user.getId(), user.getRole().name(),
            AuditAction.LOGIN_SUCCESS, "USER", user.getId(), "Logged in");
        return user;
    }
}